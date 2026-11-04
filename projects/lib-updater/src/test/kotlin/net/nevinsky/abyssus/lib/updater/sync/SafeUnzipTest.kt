/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.sync

import net.nevinsky.abyssus.lib.updater.hashing.sha256Hex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import net.nevinsky.abyssus.lib.updater.manifest.ManifestFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SafeUnzipTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
    }.toByteArray()

    private fun listed(path: String, data: ByteArray) = ManifestFile(path, data.size.toLong(), sha256Hex(data), "p.zip")

    private fun refused(zip: ByteArray, expected: List<ManifestFile>, fragment: String, file: String?, target: Path = tmp.newFolder().toPath()) {
        try {
            SafeUnzip().extract(ByteArrayInputStream(zip), expected, target)
            fail("expected a refusal")
        } catch (e: UnzipException) {
            assertTrue(e.message, e.message!!.contains(fragment))
            assertEquals(file, e.file)
        }
    }

    private val data = "hello".toByteArray()

    @Test
    fun `listed files are extracted`() {
        val target = tmp.newFolder().toPath()
        SafeUnzip().extract(ByteArrayInputStream(zip("a/b.txt" to data)), listOf(listed("a/b.txt", data)), target)
        assertEquals("hello", Files.readString(target.resolve("a/b.txt")))
    }

    @Test
    fun `parent traversal is refused`() {
        refused(zip("../evil.txt" to data), listOf(listed("../evil.txt", data)), "Unsafe", "../evil.txt")
        refused(zip("a/../../evil.txt" to data), listOf(listed("a/../../evil.txt", data)), "Unsafe", "a/../../evil.txt")
    }

    @Test
    fun `absolute and drive paths are refused`() {
        refused(zip("/etc/evil" to data), listOf(listed("/etc/evil", data)), "Unsafe", "/etc/evil")
        refused(zip("C:/evil" to data), listOf(listed("C:/evil", data)), "Unsafe", "C:/evil")
        refused(zip("a\\evil" to data), listOf(listed("a\\evil", data)), "Unsafe", "a\\evil")
    }

    @Test
    fun `entry not in the manifest is refused`() {
        refused(zip("a.txt" to data, "b.txt" to data), listOf(listed("a.txt", data)), "not listed", "b.txt")
    }

    @Test
    fun `size mismatch is refused`() {
        val large = "hello world".toByteArray()
        refused(zip("a.txt" to large), listOf(ManifestFile("a.txt", 5, sha256Hex(large), "p.zip")), "larger", "a.txt")
        refused(zip("a.txt" to data), listOf(ManifestFile("a.txt", 50, sha256Hex(data), "p.zip")), "smaller", "a.txt")
    }

    @Test
    fun `hash mismatch is refused`() {
        val other = "hellO".toByteArray()
        refused(zip("a.txt" to other), listOf(listed("a.txt", data)), "checksum", "a.txt")
    }

    @Test
    fun `missing listed file is refused`() {
        refused(zip("a.txt" to data), listOf(listed("a.txt", data), listed("b.txt", data)), "missing", "b.txt")
    }

    @Test
    fun `duplicate entry is refused`() {
        // ZipOutputStream refuses duplicates: write "b.txt", then rename it to "a.txt" in the raw bytes (same length)
        val raw = String(zip("a.txt" to data, "b.txt" to data), Charsets.ISO_8859_1).replace("b.txt", "a.txt")
        refused(raw.toByteArray(Charsets.ISO_8859_1), listOf(listed("a.txt", data)), "twice", "a.txt")
    }
}
