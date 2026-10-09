/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import java.util.zip.ZipFile

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.grammarkit")
    id("org.jetbrains.changelog")
    id("org.jetbrains.kotlinx.kover")
    id("com.gradleup.shadow")
    // LWJGL, libGDX and JUnit versions from the catalog (build-logic)
    id("abyssus.dependency-platforms")
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

repositories {
    intellijPlatform {
        defaultRepositories()
    }
}

val playHostLibs by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    intellijPlatform {
        create(properties("platformType"), properties("platformVersion"))
        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
        plugins(properties("platformPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) })
        // JSON language support: .scene files are JSON
        bundledPlugin("com.intellij.modules.json")
        pluginVerifier()
        zipSigner()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    implementation(project(":lib-physics")) { isTransitive = false }
    playHostLibs(project(":lib-physics"))
    playHostLibs(project(path = ":lib-physics", configuration = "playHost"))
    testImplementation(libs.junit4)
    // HdrFixtures: Radiance test images from a pixel function
    testImplementation(testFixtures(project(":lib-core")))
    // parseScene, testProject and the other scene helpers shared with editor-core's tests
    testImplementation(testFixtures(project(":lib-core-editor")))

    // JSON reading/writing for asset files; the platform does not ship jackson-databind, so it is bundled
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)

    // LWJGL 3 + AWT bridge for the OpenGL scene panel
    val lwjglNatives = listOf("natives-macos-arm64", "natives-macos", "natives-windows", "natives-linux")
    implementation(libs.lwjgl.awt)
    implementation(libs.lwjgl)
    implementation(libs.lwjgl.opengl)
    lwjglNatives.forEach {
        runtimeOnly(variantOf(libs.lwjgl) { classifier(it) })
        runtimeOnly(variantOf(libs.lwjgl.opengl) { classifier(it) })
        // HDR preview regressions decode EXR files without altering the plugin's packaged runtime.
        testRuntimeOnly(variantOf(libs.lwjgl.tinyexr) { classifier(it) })
    }

    // libGDX core (g3d, math) hosted on the AWT GL canvas; only the backend's GL wrapper classes are used
    implementation(libs.gdx)
    implementation(libs.gdx.backend.lwjgl3) { isTransitive = false }
    runtimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })

    // Asset reading and loading (plain JVM, see core/README.md); the IDE provides Kotlin and SLF4J here too
    implementation(project(":lib-core")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
    implementation(project(":lib-raytracing")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
    // The editing engine without the IDE (see editor-core/README.md); the plugin keeps the IDE glue
    implementation(project(":lib-core-editor")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
}

// Set the JVM language level used to build the project. IntelliJ 2025.2+ requires Java 21.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginVerification {
        ides { recommended() }
        // Internal usages are checked against exact descriptions by checkPluginInternalApis below.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES
        )
    }
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")
        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = properties("pluginUntilBuild")
        }
    }
    signing {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = environment("PUBLISH_TOKEN")
        channels =
            properties("pluginVersion").map { listOf(it.split('-').getOrElse(1) { "default" }.split('.').first()) }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    // CHANGELOG.md stays at the repository root
    path.set(rootProject.file("CHANGELOG.md").absolutePath)
    groups.empty()
    repositoryUrl.set(properties("pluginRepositoryUrl"))
}

tasks {
    test {
        // GL render tests open a real window: opt in with -Dabyssus.glTests=true
        System.getProperty("abyssus.glTests")?.let { systemProperty("abyssus.glTests", it) }
        System.getProperty("abyssus.rayTimingTests")?.let { systemProperty("abyssus.rayTimingTests", it) }
        // device tests that render through the real Metal backend: opt in with -Dabyssus.metalTests=true
        System.getProperty("abyssus.metalTests")?.let { systemProperty("abyssus.metalTests", it) }
        // and through the real Vulkan backend: -Dabyssus.vulkanTests=true (add -Dabyssus.raytracing.validation=true for the validation layer)
        System.getProperty("abyssus.vulkanTests")?.let { systemProperty("abyssus.vulkanTests", it) }
        System.getProperty("abyssus.raytracing.validation")?.let { systemProperty("abyssus.raytracing.validation", it) }
    }
    runIde {
        // Open a project on startup: -PideProject=/path/to/project
        providers.gradleProperty("ideProject").orNull?.let { args(it) }
        providers.gradleProperty("rayExperiment").orNull?.let { jvmArgs("-Dabyssus.raytracing.experiment=$it") }
        // The sandbox IDE trusts every project (no "Trust project?" dialog) and opens the Abyssus view.
        jvmArgs("-Didea.trust.all.projects=true", "-Dabyssus.openView=true")
    }

    patchPluginXml {

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(providers.fileContents(rootProject.layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        })

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes.set(properties("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        })
    }

}

// Sandbox IDE with the robot-server plugin for UI tests (run configuration "Run IDE for UI Tests", run-ui-tests.yml)
val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
    task {
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-Drobot-server.port=8082",
                "-Dide.mac.message.dialogs.as.sheets=false",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Djb.consents.confirmation.enabled=false",
                "-Didea.trust.all.projects=true",
            )
        }
    }
    plugins {
        robotServerPlugin()
    }
}

sourceSets["main"].java {
    srcDirs("src/main/gen")
}

val gltfGrammarDir = "src/main/java/net/nevinsky/abyssus/language/psi"

val generateGltfParser by tasks.registering(org.jetbrains.grammarkit.tasks.GenerateParserTask::class) {
    sourceFile.set(file("$gltfGrammarDir/Gltf.bnf"))
    targetRootOutputDir.set(file("src/main/gen"))
    pathToParser.set("/net/nevinsky/abyssus/plugin/language/parser/GltfParser.java")
    pathToPsiRoot.set("/net/nevinsky/abyssus/plugin/language/psi")
    purgeOldFiles.set(true)
}

val generateGltfLexer by tasks.registering(org.jetbrains.grammarkit.tasks.GenerateLexerTask::class) {
    sourceFile.set(file("$gltfGrammarDir/Gltf.flex"))
    targetOutputDir.set(file("src/main/gen/net/nevinsky/abyssus/plugin/language/lexer"))
    purgeOldFiles.set(true)
}

tasks.named("compileKotlin") { dependsOn(generateGltfParser, generateGltfLexer) }
tasks.named("compileJava") { dependsOn(generateGltfParser, generateGltfLexer) }

apply(from = "${project.rootDir}/gradle/plugin-verification.gradle.kts")

// Jolt's natives must never load in the IDE: the plugin's own code may not name jolt-jni or physics' Jolt package.
val checkNoJolt by tasks.registering {
    dependsOn(generateGltfParser, generateGltfLexer)
    val sources = fileTree("src/main") { include("**/*.kt", "**/*.java", "**/*.xml") }
    val root = layout.projectDirectory.asFile
    inputs.files(sources)
    doLast {
        val forbidden = Regex("""com\.github\.stephengold|net\.nevinsky\.abyssus\.lib\.physics\.jolt""")
        val found = sources.files.sorted().flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                if (forbidden.containsMatchIn(line)) "${file.relativeTo(root)}:${i + 1}: ${line.trim()}" else null
            }
        }
        if (found.isNotEmpty()) {
            throw GradleException(
                "Abyssus must not load Jolt in the IDE; remove these references:\n" + found.joinToString(
                    "\n"
                )
            )
        }
    }
}

tasks.named("check") { dependsOn(checkNoJolt) }


tasks.test {
    systemProperty("abyssus.testData", rootProject.file("projects/plugin-abyssus/src/test/testData").absolutePath)
    val pluginDirectory = tasks.prepareSandbox.flatMap { it.pluginDirectory }
    dependsOn(tasks.prepareSandbox)
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Dabyssus.playHost=${pluginDirectory.get().asFile.resolve("play-host")}")
    }
}

tasks.prepareSandbox {
    from(playHostLibs) { into(pluginName.map { "$it/play-host" }) }
}

val checkNoJoltInZip by tasks.registering {
    val archive = tasks.buildPlugin.flatMap { it.archiveFile }
    dependsOn(tasks.buildPlugin)
    inputs.file(archive)
    doLast {
        ZipFile(archive.get().asFile).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            val forbidden = names.filter { it.contains("/lib/") &&
                (it.contains("jolt", ignoreCase = true) || it.contains("stephengold", ignoreCase = true)) }
            check(forbidden.isEmpty()) { "Jolt must stay outside IDE-loaded lib/: $forbidden" }
            check(names.any { it.contains("/lib/lib-physics") }) { "Missing physics library in lib/" }
            check(names.any { it.contains("/play-host/") && it.contains("jolt", ignoreCase = true) }) {
                "Missing native physics dependencies in play-host/"
            }
        }
    }
}
tasks.named("check") { dependsOn(checkNoJoltInZip) }
