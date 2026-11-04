/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.buildlogic.packaging

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations

/**
 * One `jpackage` run. With [type] `app-image` it builds the application from [payload] on [runtime]; any other type
 * builds an installer from the `app-image` folder in [appImage], so every package of an OS holds the same payload.
 */
abstract class JPackage @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    /** The JDK whose `jpackage` runs. */
    @get:Input abstract val jdkHome: Property<String>

    /** `app-image`, `deb`, `rpm`, `msi` or `dmg`. */
    @get:Input abstract val type: Property<String>

    @get:Input abstract val appName: Property<String>

    @get:Input abstract val appVersion: Property<String>

    @get:Input abstract val vendor: Property<String>

    @get:Input abstract val appDescription: Property<String>

    /** The jars and the bundled project (an `app-image` build only). */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val payload: DirectoryProperty

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtime: DirectoryProperty

    @get:Input @get:Optional abstract val mainJar: Property<String>

    @get:Input @get:Optional abstract val mainClass: Property<String>

    /** Launcher JVM options (an `app-image` build only). */
    @get:Input abstract val javaOptions: ListProperty<String>

    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.NONE) abstract val icon: RegularFileProperty

    /** The `app-image` folder an installer is built from (not an `app-image` build). */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appImage: DirectoryProperty

    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.NONE) abstract val licenseFile: RegularFileProperty

    /** Options only one OS or package type takes (`--linux-*`, `--win-*`, `--mac-*`). */
    @get:Input abstract val extraArgs: ListProperty<String>

    /**
     * A "Developer ID Application" identity (name or SHA-1 hash): the macOS app and the `.dmg` are signed with it for
     * notarization. Unset, the app is only signed ad hoc, which Apple silicon requires.
     */
    @get:Input @get:Optional abstract val macSigningIdentity: Property<String>

    /** The hardened-runtime entitlements of the signed app (needs [macSigningIdentity]). */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val macEntitlements: RegularFileProperty

    /** `scripts/sign-macos-app.sh`, which signs the app, its native libraries and the jars' native libraries. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val macSignScript: RegularFileProperty

    /** The installer's file name when it should not be jpackage's own (`Name-1.0.0.dmg`). */
    @get:Input @get:Optional abstract val outputName: Property<String>

    @get:OutputDirectory abstract val destination: DirectoryProperty

    @TaskAction
    fun build() {
        val dest = destination.get().asFile
        dest.deleteRecursively()
        val isImage = type.get() == "app-image"
        val cmd =
            mutableListOf(
                jdkTool(jdkHome.get(), "jpackage"),
                "--type",
                type.get(),
                "--name",
                appName.get(),
                "--app-version",
                appVersion.get(),
                "--vendor",
                vendor.get(),
                "--description",
                appDescription.get(),
                "--dest",
                dest.path,
            )
        if (isImage) {
            check(payload.isPresent && runtime.isPresent && mainJar.isPresent && mainClass.isPresent) {
                "an app-image build needs payload, runtime, mainJar and mainClass"
            }
            cmd +=
                listOf(
                    "--input",
                    payload.get().asFile.path,
                    "--runtime-image",
                    runtime.get().asFile.path,
                    "--main-jar",
                    mainJar.get(),
                    "--main-class",
                    mainClass.get(),
                )
            javaOptions.get().forEach { cmd += listOf("--java-options", it) }
            if (icon.isPresent) {
                cmd += listOf("--icon", icon.get().asFile.path)
            }
        } else {
            check(appImage.isPresent) { "an installer build needs appImage" }
            cmd += listOf("--app-image", appImage.get().asFile.path)
            if (licenseFile.isPresent) {
                cmd += listOf("--license-file", licenseFile.get().asFile.path)
            }
        }
        cmd += extraArgs.get()
        exec.exec { commandLine(cmd) }
        if (isImage && System.getProperty("os.name").lowercase().contains("mac")) {
            dropMicrophonePrompt(dest)
        }
        if (!isImage && outputName.isPresent) {
            val made =
                dest.listFiles()?.singleOrNull { it.isFile }
                    ?: throw GradleException("jpackage should have written exactly one file into $dest")
            val named = java.io.File(dest, outputName.get())
            made.renameTo(named)
            if (type.get() == "dmg" && macSigningIdentity.isPresent) {
                resignAppInDmg(named)
                signDmg(named)
            }
        }
    }

    /**
     * jpackage writes `NSMicrophoneUsageDescription` into every macOS app, which makes macOS ask the player for the
     * microphone at first start. The game never records, so the key is removed and the app signed again, with
     * [macSigningIdentity] or else ad hoc (editing `Info.plist` invalidates jpackage's own ad-hoc signature, which
     * Apple silicon requires).
     */
    private fun dropMicrophonePrompt(dest: java.io.File) {
        dest
            .listFiles { f -> f.name.endsWith(".app") }
            ?.forEach { app ->
                val plist = java.io.File(app, "Contents/Info.plist")
                // plutil fails when the key is absent, which is fine: nothing to remove
                ProcessBuilder("plutil", "-remove", "NSMicrophoneUsageDescription", plist.path)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
                if (macSigningIdentity.isPresent) {
                    check(macEntitlements.isPresent && macSignScript.isPresent) {
                        "macSigningIdentity needs macEntitlements and macSignScript"
                    }
                    exec.exec {
                        commandLine(
                            macSignScript.get().asFile.path,
                            app.path,
                            macSigningIdentity.get(),
                            macEntitlements.get().asFile.path,
                        )
                    }
                } else {
                    exec.exec { commandLine("codesign", "--force", "--deep", "--sign", "-", app.path) }
                }
            }
    }

    /**
     * jpackage signs the app inside a `.dmg` again, ad hoc and without a secure timestamp, which replaces the Developer
     * ID signatures made on the app image and which notarization rejects. So the image is opened for writing, the app
     * in it is signed again with [macSignScript] and the image is compressed back.
     */
    private fun resignAppInDmg(dmg: java.io.File) {
        check(macEntitlements.isPresent && macSignScript.isPresent) {
            "macSigningIdentity needs macEntitlements and macSignScript"
        }
        val work = java.nio.file.Files.createTempDirectory("abyssus-dmg").toFile()
        val writable = java.io.File(work, "writable.dmg")
        val mount = java.io.File(work, "mount").also { it.mkdirs() }
        val repacked = java.io.File(work, "repacked.dmg")
        try {
            exec.exec { commandLine("hdiutil", "convert", dmg.path, "-format", "UDRW", "-o", writable.path) }
            // the signatures carry timestamps and are larger than the ad hoc ones: leave room
            val limits =
                ProcessBuilder("hdiutil", "resize", "-limits", writable.path).redirectErrorStream(true).start().let { p
                    ->
                    p.inputStream.bufferedReader().readText().also { p.waitFor() }
                }
            // "<minimum> <current> <maximum>" in 512-byte sectors
            val sectors = limits.trim().lines().last().trim().split(Regex("\\s+"))[1].toLong() + 64L * 2048
            exec.exec { commandLine("hdiutil", "resize", "-sectors", sectors.toString(), writable.path) }
            exec.exec {
                commandLine(
                    "hdiutil",
                    "attach",
                    writable.path,
                    "-mountpoint",
                    mount.path,
                    "-nobrowse",
                    "-noverify",
                    "-noautoopen",
                )
            }
            try {
                val app =
                    mount.listFiles { f -> f.name.endsWith(".app") }?.singleOrNull()
                        ?: throw GradleException("the disk image should hold exactly one .app")
                exec.exec {
                    commandLine(
                        macSignScript.get().asFile.path,
                        app.path,
                        macSigningIdentity.get(),
                        macEntitlements.get().asFile.path,
                    )
                }
            } finally {
                exec.exec {
                    commandLine("hdiutil", "detach", mount.path)
                    isIgnoreExitValue = true
                }
            }
            exec.exec { commandLine("hdiutil", "convert", writable.path, "-format", "UDZO", "-o", repacked.path) }
            java.nio.file.Files.move(repacked.toPath(), dmg.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            work.deleteRecursively()
        }
    }

    /** The disk image carries its own signature (the notarization ticket is stapled to it later). */
    private fun signDmg(dmg: java.io.File) {
        val cmd = mutableListOf("codesign", "--force", "--timestamp", "--sign", macSigningIdentity.get())
        System.getenv("MACOS_KEYCHAIN")?.takeIf { it.isNotEmpty() }?.let { cmd += listOf("--keychain", it) }
        cmd += dmg.path
        exec.exec { commandLine(cmd) }
    }
}
