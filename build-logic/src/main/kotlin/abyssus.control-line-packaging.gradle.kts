/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

import net.nevinsky.abyssus.buildlogic.packaging.BuildAppImage
import net.nevinsky.abyssus.buildlogic.packaging.BuildUpdateParts
import net.nevinsky.abyssus.buildlogic.packaging.CheckPackagePayload
import net.nevinsky.abyssus.buildlogic.packaging.DownloadVerified
import net.nevinsky.abyssus.buildlogic.packaging.JPackage
import net.nevinsky.abyssus.buildlogic.packaging.JlinkRuntime
import net.nevinsky.abyssus.buildlogic.packaging.NotarizeDmg
import net.nevinsky.abyssus.buildlogic.packaging.SignWindowsFiles
import net.nevinsky.abyssus.buildlogic.packaging.WriteChecksums
import net.nevinsky.abyssus.buildlogic.packaging.WriteInstallerDigest
import net.nevinsky.abyssus.buildlogic.packaging.windowsSignableFiles
import java.util.Properties

// Native packages of Control Line (deb, rpm, AppImage, msi, dmg): a convention plugin the game's build applies after
// `application`. Its data (modules.txt, icons, AppImage templates) is in the game's packaging/ folder.
// One payload per OS: the distribution's jars and the bundled project are staged, jpackage turns them into an app-image
// on a trimmed jlink runtime, and every installer is built from that app-image, so one check covers all of them.
// jpackage cannot cross-build: run a package task on the OS it targets (see the game's README, "Packages").

/**
 * A release build (`-PreleaseBuild=true`, set only by the release workflow in the protected `release` environment) signs what it
 * packages and fails when a signing input is missing. Without it nothing is signed and no credential is read.
 */
val releaseBuild: Boolean = providers.gradleProperty("releaseBuild").orNull == "true"

/** A required signing input from the environment; a release build that lacks one fails naming it. */
fun requiredEnv(name: String): Provider<String> =
    providers.environmentVariable(name).orElse(providers.provider { throw GradleException("The release build cannot sign: $name is not set") })

/** The package version: `.msi` needs numeric x.y.z and `.dmg` a major version above 0. */
val controlLineVersion: Provider<String> = providers.gradleProperty("controlLineVersion").orElse("1.0.0")

val packagingBuild: Provider<Directory> = layout.buildDirectory.dir("packaging")
val installDist = tasks.named<Sync>("installDist")

/** The launcher and its runtime classpath (the game's build adds `project(":app-game-control-line-launcher")`). */
val launcherPackage: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME)) }
}

/** `packaging/updater.properties`: which jars only an installer can replace, and the launcher protocol number. */
val updaterProps: Provider<Properties> =
    providers.fileContents(layout.projectDirectory.file("packaging/updater.properties")).asText
        .map { text -> Properties().apply { load(text.reader()) } }
val installerOnlyJarPatterns: Provider<List<String>> = updaterProps.map { it.getProperty("installerOnly").split(',').map(String::trim).filter(String::isNotEmpty) }

/** What an update cannot change, as one hash (design decision 2); the installed copy is compared with the manifest's. */
val writeInstallerDigest by tasks.registering(WriteInstallerDigest::class) {
    group = "packaging"
    description = "Writes installer.digest: the hash of the installer-only jars, the module list, the JDK version and the launcher protocol."
    gameLib.set(installDist.map { it.destinationDir }.let { dir -> layout.dir(dir.map { File(it, "lib") }) })
    modules.set(layout.projectDirectory.file("packaging/modules.txt"))
    installerOnlyPatterns.set(installerOnlyJarPatterns)
    jdkVersion.set(providers.systemProperty("java.version"))
    protocol.set(updaterProps.map { it.getProperty("launcherProtocol").toInt() })
    output.set(packagingBuild.map { it.file("installer.digest") })
}

// The payload (jpackage's --input), laid out the same on every OS under the application folder (`$APPDIR`):
//   launcher/            the launcher, lib-updater and their libraries (the jpackage main jar's folder; installer-owned)
//   game/                the seed game: the distribution's jars and game/project/ControlLine (what an update replaces)
//   installer.digest     see writeInstallerDigest
val stagePackageInput by tasks.registering(Sync::class) {
    group = "packaging"
    description = "Stages the launcher, the distribution's jars with the bundled project, and installer.digest as the input of the native packages."
    dependsOn(installDist, writeInstallerDigest)
    val installed = installDist.map { it.destinationDir }
    from(launcherPackage) { into("launcher") }
    from(installed.map { File(it, "lib") }) { into("game") }
    from(installed.map { File(it, "project") }) { into("game/project") }
    from(writeInstallerDigest.flatMap { it.output })
    into(packagingBuild.map { it.dir("input") })
}

// The update parts and the unsigned manifest of a release (`buildUpdateParts`); GitHub signs the published release
val buildUpdateParts by tasks.registering(BuildUpdateParts::class) {
    group = "packaging"
    description = "Writes game-code-<version>.zip, game-project-<version>.zip and the unsigned manifest.json into build/packaging/update."
    gameLib.set(installDist.map { it.destinationDir }.let { dir -> layout.dir(dir.map { File(it, "lib") }) })
    bundledProject.set(installDist.map { it.destinationDir }.let { dir -> layout.dir(dir.map { File(it, "project/ControlLine") }) })
    digestFile.set(writeInstallerDigest.flatMap { it.output })
    installerOnlyPatterns.set(installerOnlyJarPatterns)
    version.set(controlLineVersion)
    mainClass.set("net.nevinsky.abyssus.app.game.controlline.MainKt")
    output.set(packagingBuild.map { it.dir("update") })
}

// Release steps, run on a folder that holds the manifest, the parts and the installers: -PreleaseDir=<folder>.
// writeChecksums -> :lib-updater:verifyReleaseManifest, then the release is published as an immutable release: GitHub signs it
// (a release attestation listing every asset), checked after publishing by :lib-updater:verifyPublishedRelease
val releaseDirectory: Provider<Directory> = layout.dir(providers.gradleProperty("releaseDir").map { File(it) })

val writeChecksums by tasks.registering(WriteChecksums::class) {
    group = "release"
    description = "Writes SHA256SUMS for the installers in the release folder given as -PreleaseDir=<folder>."
    releaseDir.set(releaseDirectory)
}

/** The JDK that runs Gradle supplies `jdeps`, `jlink` and `jpackage`; it is the runtime the packages bundle. */
val buildJdkHome: Provider<String> = providers.systemProperty("java.home")

val osName: String = System.getProperty("os.name").lowercase()
val isMac: Boolean = osName.contains("mac")
val isWindows: Boolean = osName.contains("win")
/** The OS and architecture the package is built on, as they appear in a package's file name. */
val platformName: String = if (isMac) "macos" else if (isWindows) "windows" else "linux"
val archName: String = System.getProperty("os.arch").let { if (it == "aarch64" || it == "arm64") "arm64" else "x64" }

val jlinkRuntime by tasks.registering(JlinkRuntime::class) {
    group = "packaging"
    description = "Builds the bundled Java runtime from packaging/modules.txt."
    dependsOn(stagePackageInput)
    payload.set(packagingBuild.map { it.dir("input") })
    modules.set(layout.projectDirectory.file("packaging/modules.txt"))
    jdkHome.set(buildJdkHome)
    runtime.set(packagingBuild.map { it.dir("runtime") })
}

/** The application's display name: the launcher, the `.app` folder and the menu entry. */
val applicationName = "Control Line"
val iconFile: RegularFile = layout.projectDirectory.file(
    "packaging/icons/control-line." + if (isMac) "icns" else if (isWindows) "ico" else "png"
)

// The launcher's options. `$APPDIR` is expanded by the native launcher to the application folder on every OS; the launcher
// finds game/ and installer.digest under it and starts the game with controlLine.project set. macOS needs the window on the
// process's first thread (the launcher runs the game on it); --enable-native-access silences JDK 25's restricted-method warnings.
val launcherOptions: List<String> = buildList {
    add("-DcontrolLine.installDir=\$APPDIR")
    add("--enable-native-access=ALL-UNNAMED")
    if (isMac) add("-XstartOnFirstThread")
}

/** Defaults shared by every jpackage run of the game. */
fun JPackage.controlLine(packageType: String) {
    group = "packaging"
    jdkHome.set(buildJdkHome)
    type.set(packageType)
    appName.set(applicationName)
    appVersion.set(controlLineVersion)
    vendor.set("Alexey Nevinsky")
    appDescription.set("Control Line, a model aircraft flying game")
    destination.set(packagingBuild.map { it.dir(packageType) })
}

/** jpackage's signing options (names checked against JDK 21 and 25, design.md decision 8); the identity and keychain are the job's. */
fun macSigningArgs(): Provider<List<String>> = requiredEnv("MACOS_SIGNING_IDENTITY").zip(requiredEnv("MACOS_KEYCHAIN")) { identity, keychain ->
    listOf(
        "--mac-sign", "--mac-signing-keychain", keychain, "--mac-signing-key-user-name", identity,
        "--mac-entitlements", layout.projectDirectory.file("packaging/macos/entitlements.plist").asFile.path,
    )
}

val packageApplication by tasks.registering(JPackage::class) {
    description = "Builds the application folder (the payload of every package of this OS) with jpackage."
    controlLine("app-image")
    dependsOn(stagePackageInput, jlinkRuntime)
    payload.set(packagingBuild.map { it.dir("input") })
    runtime.set(packagingBuild.map { it.dir("runtime") })
    mainJar.set("launcher/app-game-control-line-launcher.jar")
    mainClass.set("net.nevinsky.abyssus.app.game.controlline.launcher.LauncherMainKt")
    javaOptions.set(launcherOptions)
    icon.set(iconFile)
    if (isMac) extraArgs.addAll("--mac-package-identifier", "net.nevinsky.abyssus.controlline", "--mac-package-name", "Control Line")
    if (isMac && releaseBuild) extraArgs.addAll(macSigningArgs())
}

// A shipped game carries no editor code: the payload every installer is built from is checked after it is made, and an
// installer task depends on the check, so a package is never built from a payload that failed it.
val checkPackagePayload by tasks.registering(CheckPackagePayload::class) {
    group = "verification"
    description = "Fails when the packaged application holds editor code or differs from the Gradle distribution's lib/."
    appImage.set(packagingBuild.map { it.dir("app-image") })
    distributionLib.set(installDist.map { it.destinationDir }.let { dir -> layout.dir(dir.map { File(it, "lib") }) })
    launcherJars.from(launcherPackage)
    mustRunAfter(packageApplication)
}
packageApplication.configure { finalizedBy(checkPackagePayload) }

// The same check on a package unpacked elsewhere (`scripts/smoke-control-line-linux.sh` unpacks the .deb): -PpackageDir=<folder>
tasks.register<CheckPackagePayload>("checkExtractedPackage") {
    group = "verification"
    description = "Runs the payload check on an unpacked package given as -PpackageDir=<folder>."
    appImage.set(layout.dir(providers.gradleProperty("packageDir").map { File(it) }))
    distributionLib.set(installDist.map { it.destinationDir }.let { dir -> layout.dir(dir.map { File(it, "lib") }) })
    launcherJars.from(launcherPackage)
}

/** An installer of [packageType], built from the checked app-image and named `control-line-<version>-<os>-<arch>.<extension>`. */
fun JPackage.installer(packageType: String, extension: String) {
    controlLine(packageType)
    dependsOn(packageApplication, checkPackagePayload)
    // plain values, so the lambdas below capture no script object (the configuration cache cannot store one)
    val imageFolder = applicationName + if (isMac) ".app" else ""
    val os = platformName
    val arch = archName
    // jpackage wants the application folder itself: `Control Line.app` on macOS, `Control Line` elsewhere
    appImage.set(packagingBuild.map { it.dir("app-image/$imageFolder") })
    licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE"))
    outputName.set(controlLineVersion.map { "control-line-$it-$os-$arch.$extension" })
}

if (isMac) {
    tasks.register<JPackage>("packageDmg") {
        description = "Builds the macOS disk image for this Mac's architecture."
        installer("dmg", "dmg")
        extraArgs.addAll("--mac-package-identifier", "net.nevinsky.abyssus.controlline", "--mac-package-name", "Control Line")
        if (releaseBuild) extraArgs.addAll(macSigningArgs())
    }
    if (releaseBuild) {
        // the signed .dmg goes to Apple, then the ticket is stapled; a rejection fails the release
        val packageDmg = tasks.named("packageDmg")
        val notarizeDmg = tasks.register<NotarizeDmg>("notarizeDmg") {
            group = "release"
            description = "Notarizes the signed .dmg with notarytool and staples the ticket (APPLE_ID, APPLE_TEAM_ID, APPLE_APP_PASSWORD)."
            dependsOn(packageDmg)
            dmg.set(packagingBuild.zip(controlLineVersion) { dir, v -> dir.file("dmg/control-line-$v-$platformName-$archName.dmg") })
            retries.set(1)
        }
        packageDmg.configure { finalizedBy(notarizeDmg) }
    }
}

if (!isMac && !isWindows) {
    // the package maintainer's address (jpackage writes it after the vendor's name): a placeholder, since the field is public in the .deb
    val maintainer = "noreply@invalid.example"
    val linuxCommon = listOf("--linux-package-name", "control-line", "--linux-menu-group", "Game", "--linux-app-category", "games", "--linux-shortcut")

    tasks.register<JPackage>("packageDeb") {
        description = "Builds the Debian package (needs dpkg-deb and fakeroot)."
        installer("deb", "deb")
        extraArgs.addAll(linuxCommon)
        extraArgs.addAll("--linux-deb-maintainer", maintainer)
    }
    tasks.register<JPackage>("packageRpm") {
        description = "Builds the RPM package (needs rpmbuild)."
        installer("rpm", "rpm")
        extraArgs.addAll(linuxCommon)
        extraArgs.addAll("--linux-rpm-license-type", "Apache-2.0")
    }

    // The AppImage: jpackage cannot make one, so the app-image folder is wrapped by the pinned appimagetool.
    val arch = if (archName == "arm64") "aarch64" else "x86_64"
    val os = platformName
    val shortArch = archName
    val imageFolder = applicationName
    val toolProps: Provider<Properties> =
        providers.fileContents(layout.projectDirectory.file("packaging/appimage/tools.properties")).asText
            .map { text -> Properties().apply { load(text.reader()) } }
    val toolsDir = packagingBuild.map { it.dir("tools") }

    val downloadAppImageTool by tasks.registering(DownloadVerified::class) {
        group = "packaging"
        description = "Downloads appimagetool (pinned version, checksum verified)."
        url.set(toolProps.map { "https://github.com/AppImage/appimagetool/releases/download/${it.getProperty("appimagetool.version")}/appimagetool-$arch.AppImage" })
        sha256.set(toolProps.map { it.getProperty("$arch.appimagetool.sha256") })
        output.set(toolsDir.map { it.file("appimagetool-$arch.AppImage") })
    }
    val downloadAppImageRuntime by tasks.registering(DownloadVerified::class) {
        group = "packaging"
        description = "Downloads the AppImage runtime file appimagetool embeds (pinned version, checksum verified)."
        url.set(toolProps.map { "https://github.com/AppImage/type2-runtime/releases/download/${it.getProperty("runtime.version")}/runtime-$arch" })
        sha256.set(toolProps.map { it.getProperty("$arch.runtime.sha256") })
        output.set(toolsDir.map { it.file("runtime-$arch") })
    }

    // AppDir: the application folder under app/, AppRun, the desktop entry and the icon
    val assembleAppDir by tasks.registering(Sync::class) {
        group = "packaging"
        description = "Assembles the AppImage's AppDir from the checked application folder."
        dependsOn(packageApplication, checkPackagePayload)
        from(packagingBuild.map { it.dir("app-image/$imageFolder") }) { into("app") }
        from(layout.projectDirectory.dir("packaging/appimage")) {
            include("AppRun", "control-line.desktop")
            filesMatching("AppRun") { permissions { unix("rwxr-xr-x") } }
        }
        from(iconFile) { rename { "control-line.png" } }
        from(iconFile) { rename { ".DirIcon" } }
        into(packagingBuild.map { it.dir("appdir") })
    }

    tasks.register<BuildAppImage>("packageAppImage") {
        group = "packaging"
        description = "Builds the AppImage (the bundled runtime included) with appimagetool."
        dependsOn(assembleAppDir)
        appDir.set(packagingBuild.map { it.dir("appdir") })
        tool.set(downloadAppImageTool.flatMap { it.output })
        runtimeFile.set(downloadAppImageRuntime.flatMap { it.output })
        this.arch.set(arch)
        output.set(packagingBuild.zip(controlLineVersion) { dir, version -> dir.file("appimage/control-line-$version-$os-$shortArch.AppImage") })
    }
}

if (isWindows) {
    // Fixed for the life of the product: Windows Installer uses it to find an installed Control Line and upgrade it in place
    val upgradeUuid = "6c09579a-d9e1-4213-81ff-056f92b90a42"
    val packageMsi = tasks.register<JPackage>("packageMsi") {
        description = "Builds the Windows installer (needs WiX 3.x on the PATH)."
        installer("msi", "msi")
        extraArgs.addAll(
            "--win-menu", "--win-menu-group", "Control Line", "--win-shortcut", "--win-dir-chooser", "--win-upgrade-uuid", upgradeUuid
        )
    }
    if (releaseBuild) {
        // jpackage has no signing option: the launcher and DLLs are signed before the .msi is built from them, then the .msi
        // itself, each through the command template of WINDOWS_SIGN_COMMAND (a route chosen by CI, see design.md)
        val signAppImage = tasks.register<SignWindowsFiles>("signWindowsAppImage") {
            group = "release"
            description = "Signs the app-image's launcher and DLLs with WINDOWS_SIGN_COMMAND."
            dependsOn(packageApplication, checkPackagePayload)
            files.from(packagingBuild.map { windowsSignableFiles(it.dir("app-image/$applicationName").asFile) })
        }
        packageMsi.configure { dependsOn(signAppImage) }
        val signMsi = tasks.register<SignWindowsFiles>("signWindowsMsi") {
            group = "release"
            description = "Signs the .msi with WINDOWS_SIGN_COMMAND."
            dependsOn(packageMsi)
            files.from(packagingBuild.zip(controlLineVersion) { dir, v -> dir.file("msi/control-line-$v-$platformName-$archName.msi") })
        }
        packageMsi.configure { finalizedBy(signMsi) }
    }
}
