/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.buildlogic.packaging

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ManifestWriterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val patterns = listOf("*natives*", "jolt-jni-*")

    private fun lib(game: String = "game", natives: String = "n"): File = tmp.newFolder().also {
        File(it, "app-game-control-line.jar").writeText(game)
        File(it, "gdx-1.jar").writeText("gdx")
        File(it, "gdx-platform-natives-desktop.jar").writeText(natives)
        File(it, "jolt-jni-Linux64-ReleaseSp.jar").writeText("jolt")
    }

    private fun project(): File = tmp.newFolder().also {
        File(it, "a.scene").writeText("scene")
        File(it, "assets").mkdirs()
        File(it, "assets/meta.json").writeText("{}")
    }

    @Test
    fun `files are listed with the right hashes and installer-only jars are excluded from the code part`() {
        val out = tmp.newFolder()
        val manifest = buildUpdateParts(lib(), project(), patterns, "1.2.0", "Main", "digest", out)
        val text = manifest.readText()
        assertTrue(text.contains("\"version\": \"1.2.0\""))
        assertTrue(text.contains("\"sha256\": \"${sha256("game".toByteArray())}\""))
        assertTrue(text.contains("\"path\": \"project/ControlLine/assets/meta.json\""))
        ZipFile(File(out, "game-code-1.2.0.zip")).use { zip ->
            assertEquals(listOf("app-game-control-line.jar", "gdx-1.jar"), zip.entries().asSequence().map { it.name }.toList())
        }
        ZipFile(File(out, "game-project-1.2.0.zip")).use { zip ->
            assertEquals(listOf("project/ControlLine/a.scene", "project/ControlLine/assets/meta.json"), zip.entries().asSequence().map { it.name }.toList())
        }
        // installer-only jars appear in the manifest, owned by the installer
        assertTrue(text.contains("\"path\": \"jolt-jni-Linux64-ReleaseSp.jar\"") && text.contains("\"part\": \"$INSTALLER_PART\""))
        assertEquals(listOf("gdx-platform-natives-desktop.jar", "jolt-jni-Linux64-ReleaseSp.jar"), installerOnlyJars(lib(), patterns).map { it.name })
        val partLine = text.lines().first { it.contains("\"name\": \"game-code-1.2.0.zip\"") }
        assertTrue(partLine.contains(sha256(File(out, "game-code-1.2.0.zip"))))
    }

    @Test
    fun `digest ignores game code and comments but follows natives and the module list`() {
        val modules = "# comment\njava.base\njava.sql\n"
        val base = installerDigest(installerOnlyJars(lib(), patterns), modules, "21.0.9", 1)
        assertEquals(base, installerDigest(installerOnlyJars(lib(game = "changed game code"), patterns), modules, "21.0.9", 1))
        assertEquals(base, installerDigest(installerOnlyJars(lib(), patterns), "java.sql\n# other comment\njava.base\n", "21.0.9", 1))
        assertNotEquals(base, installerDigest(installerOnlyJars(lib(), patterns), modules + "jdk.unsupported\n", "21.0.9", 1))
        assertNotEquals(base, installerDigest(installerOnlyJars(lib(natives = "other"), patterns), modules, "21.0.9", 1))
        assertNotEquals(base, installerDigest(installerOnlyJars(lib(), patterns), modules, "21.0.10", 1))
        assertNotEquals(base, installerDigest(installerOnlyJars(lib(), patterns), modules, "21.0.9", 2))
    }

    @Test
    fun `json strings are escaped`() {
        val text = renderManifest("p", "1", "Main", "d", emptyList(), listOf(ManifestEntry("a\"b\\c", 1, "h", "p")))
        assertTrue(text, text.contains("a\\\"b\\\\c"))
        assertFalse(text.contains("a\"b"))
    }
}

class ChecksumTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `lines name every installer and nothing else`() {
        val dir = tmp.newFolder()
        File(dir, "control-line-1-linux-x64.AppImage").writeText("a")
        File(dir, "control-line-1-linux-x64.deb").writeText("b")
        File(dir, "manifest.json").writeText("m")
        File(dir, "game-code-1.zip").writeText("z")
        val lines = checksumLines(dir)
        assertEquals(listOf("control-line-1-linux-x64.AppImage", "control-line-1-linux-x64.deb"), lines.map { it.substringAfter("  ") })
        assertEquals("${sha256("a".toByteArray())}  control-line-1-linux-x64.AppImage", lines[0])
    }
}
