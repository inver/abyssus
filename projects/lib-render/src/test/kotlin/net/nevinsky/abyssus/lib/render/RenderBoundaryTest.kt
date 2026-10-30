/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** The rendering library is plain JVM and knows no host: no IDE, Swing/AWT, editor, physics or ray-backend type. */
class RenderBoundaryTest {
    @Test fun theTestClasspathHoldsNoIdeEditorPhysicsOrRayModule() {
        val entries = System.getProperty("java.class.path").split(File.pathSeparator).map(::File).filter { it.exists() }
        assertTrue(entries.isNotEmpty())
        val forbidden = listOf("com/intellij/", "net/nevinsky/abyssus/lib/core/editor/", "net/nevinsky/abyssus/lib/physics/",
            "net/nevinsky/abyssus/lib/raytracing/", "net/nevinsky/abyssus/lib/gamelink/", "net/nevinsky/abyssus/plugin/", "com/github/stephengold/")
        val offenders = entries.filter { entry ->
            fun has(name: String) = forbidden.any { name.startsWith(it) }
            if (entry.isDirectory) forbidden.any { File(entry, it).exists() }
            else ZipFile(entry).use { zip -> zip.entries().asSequence().any { has(it.name) } }
        }
        assertEquals(emptyList<File>(), offenders)
    }

    @Test fun noMainSourceImportsAHostTypeSwingOrAwt() {
        val root = File(System.getProperty("abyssus.renderSources"))
        assertTrue(root.isDirectory)
        val forbidden = Regex(
            """^import\s+(com\.intellij|javax\.swing|java\.awt|org\.jetbrains\.annotations|com\.github\.stephengold|""" +
                """net\.nevinsky\.abyssus\.(lib\.core\.editor|lib\.physics|lib\.raytracing|lib\.gamelink|plugin|app))\b""",
            RegexOption.MULTILINE,
        )
        val offenders = root.walk().filter { it.extension == "kt" }.filter { forbidden.containsMatchIn(it.readText()) }.toList()
        assertEquals(emptyList<File>(), offenders)
    }
}
