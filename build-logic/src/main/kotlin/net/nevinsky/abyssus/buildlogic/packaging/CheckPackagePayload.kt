/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.buildlogic.packaging

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipFile

/**
 * Fails when the application folder of an `app-image` holds editor code, or when its jars differ from the Gradle
 * distribution's `lib/`. Every installer is built from that folder, so a clean folder means clean packages.
 */
abstract class CheckPackagePayload : DefaultTask() {
    /** The folder `jpackage --type app-image` wrote. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appImage: DirectoryProperty

    /** The distribution's `lib/` folder, which the payload must match. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val distributionLib: DirectoryProperty

    @TaskAction
    fun check() {
        val image = appImage.get().asFile
        val gameJar = image.walkTopDown().firstOrNull { it.isFile && it.name == GAME_JAR }
            ?: throw GradleException("No $GAME_JAR found in $image: the app-image holds no game")
        val jars = gameJar.parentFile.listFiles { f -> f.extension == "jar" }!!.sortedBy { it.name }
        val problems = sortedSetOf<String>()

        jars.filter { jar -> EDITOR_ONLY_JARS.any { jar.name.startsWith(it) } }
            .forEach { problems += "${it.name}: an editor-only library is in the package" }
        for (jar in jars) {
            ZipFile(jar).use { zip ->
                val hit = zip.entries().asSequence().map { it.name }.firstOrNull { name -> EDITOR_ONLY_PACKAGES.any { name.startsWith(it) } }
                if (hit != null) problems += "${jar.name}: holds editor-only code ($hit)"
            }
        }

        val expected = distributionLib.get().asFile.listFiles { f -> f.extension == "jar" }!!.map { it.name }.toSortedSet()
        val actual = jars.map { it.name }.toSortedSet()
        (actual - expected).forEach { problems += "$it: in the package but not in the Gradle distribution's lib/" }
        (expected - actual).forEach { problems += "$it: in the Gradle distribution's lib/ but not in the package" }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "The package payload in ${gameJar.parentFile} is not the shipped game:\n" + problems.joinToString("\n") { "  - $it" }
            )
        }
    }

    private companion object {
        const val GAME_JAR = "app-game-control-line.jar"

        /** Libraries that only the editor links (`checkShippedClasspath` guards the same set on the Gradle classpath). */
        val EDITOR_ONLY_JARS = listOf("lib-core-editor", "lib-game-link", "lib-raytracing", "plugin-")

        /** Entry prefixes of editor-only code: the editing library, the Play bridge, the ray tracing modules, the ray view. */
        val EDITOR_ONLY_PACKAGES = listOf(
            "net/nevinsky/abyssus/lib/core/editor/",
            "net/nevinsky/abyssus/lib/gamelink/",
            "net/nevinsky/abyssus/lib/raytracing/",
            "net/nevinsky/abyssus/app/game/controlline/ray/",
            "net/nevinsky/abyssus/app/game/controlline/play/",
        )
    }
}
