/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.buildlogic.packaging

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/** Packs [appDir] into an AppImage with `appimagetool`, on the pinned runtime file. */
abstract class BuildAppImage @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tool: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeFile: RegularFileProperty

    /** `x86_64` or `aarch64`: the architecture of the AppImage and of the tool. */
    @get:Input
    abstract val arch: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun pack() {
        val out = output.get().asFile
        out.parentFile.mkdirs()
        out.delete()
        exec.exec {
            // the tool is an AppImage itself; hosted runners and containers have no FUSE, so it unpacks itself and runs from there
            environment("APPIMAGE_EXTRACT_AND_RUN", "1")
            environment("ARCH", arch.get())
            commandLine(tool.get().asFile.path, "--runtime-file", runtimeFile.get().asFile.path, appDir.get().asFile.path, out.path)
        }
    }
}
