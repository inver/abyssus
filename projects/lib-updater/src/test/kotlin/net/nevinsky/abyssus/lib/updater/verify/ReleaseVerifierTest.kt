/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.verify

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import net.nevinsky.abyssus.lib.updater.manifest.Manifest
import net.nevinsky.abyssus.lib.updater.manifest.ManifestPart
import net.nevinsky.abyssus.lib.updater.manifest.writeManifest
import net.nevinsky.abyssus.lib.updater.hashing.sha256Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReleaseVerifierTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val verifier = ReleaseVerifier(requireChecksums = true)

    private fun release(): Path {
        val dir = tmp.newFolder().toPath()
        val zip = ByteArrayOutputStream().also { o -> ZipOutputStream(o).use { it.putNextEntry(ZipEntry("a")); it.write(1); it.closeEntry() } }.toByteArray()
        Files.write(dir.resolve("game-code-1.0.1.zip"), zip)
        Files.write(dir.resolve("control-line-1.0.1-linux-x64.AppImage"), "image".toByteArray())
        val manifest = writeManifest(
            Manifest(1, "control-line", "1.0.1", "Main", "d", listOf(ManifestPart("game-code-1.0.1.zip", zip.size.toLong(), sha256Hex(zip))))
        )
        Files.write(dir.resolve("manifest.json"), manifest)
        val sums = "${sha256Hex("image".toByteArray())}  control-line-1.0.1-linux-x64.AppImage\n".toByteArray()
        Files.write(dir.resolve("SHA256SUMS"), sums)
        return dir
    }

    private fun problems(dir: Path): List<String> = try {
        verifier.verify(dir)
        emptyList()
    } catch (e: ReleaseVerificationException) {
        e.problems
    }

    @Test
    fun `a good folder passes`() = assertEquals(emptyList<String>(), problems(release()))

    @Test
    fun `a changed part fails`() {
        val dir = release()
        val part = dir.resolve("game-code-1.0.1.zip")
        Files.write(part, Files.readAllBytes(part).also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() })
        assertTrue(problems(dir).any { it.startsWith("game-code-1.0.1.zip") })
    }

    @Test
    fun `a manifest that does not bind fails`() {
        val dir = release()
        Files.writeString(dir.resolve("manifest.json"), "{ not json")
        assertTrue(problems(dir).any { it.startsWith("manifest.json") })
    }

    @Test
    fun `a changed package or checksum list fails`() {
        val dir = release()
        Files.writeString(dir.resolve("control-line-1.0.1-linux-x64.AppImage"), "imagf")
        assertTrue(problems(dir).any { it.contains("differs from SHA256SUMS") })
        val other = release()
        Files.writeString(other.resolve("SHA256SUMS"), "0  x\n")
        assertTrue(problems(other).any { it.startsWith("x: listed in SHA256SUMS") })
    }

    @Test
    fun `missing checksums fail when required`() {
        val dir = release()
        Files.delete(dir.resolve("SHA256SUMS"))
        assertEquals(listOf("SHA256SUMS: missing"), problems(dir))
        try {
            ReleaseVerifier().verify(tmp.newFolder().toPath())
            fail("expected a failure")
        } catch (e: ReleaseVerificationException) {
            assertEquals(listOf("manifest.json is missing"), e.problems)
        }
    }
}
