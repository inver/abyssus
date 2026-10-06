/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** The editing library builds and runs with no IntelliJ Platform, Swing-hosting IDE class on its classpath. */
class NoPlatformClasspathTest {
    @Test fun theTestClasspathHoldsNoIntellijClass() {
        val entries = System.getProperty("java.class.path").split(File.pathSeparator).map(::File).filter { it.exists() }
        assertTrue(entries.isNotEmpty())
        val platform = entries.filter { entry ->
            if (entry.isDirectory) File(entry, "com/intellij").exists()
            else ZipFile(entry).use { zip -> zip.entries().asSequence().any { it.name.startsWith("com/intellij/") } }
        }
        assertEquals(emptyList<File>(), platform)
    }

    @Test fun noMainSourceImportsTheIdeSwingOrAwt() {
        val root = File(System.getProperty("abyssus.editorCoreSources"))
        assertTrue(root.isDirectory)
        val forbidden = Regex("""^import\s+(com\.intellij|javax\.swing|java\.awt|org\.jetbrains\.annotations)\b""", RegexOption.MULTILINE)
        val offenders = root.walk().filter { it.extension == "kt" }.filter { forbidden.containsMatchIn(it.readText()) }.toList()
        assertEquals(emptyList<File>(), offenders)
    }
}
