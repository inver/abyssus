/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import net.nevinsky.abyssus.lib.core.io.FileLoader
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FoliageMaskFileTest {
    private fun project(block: (File) -> Unit): File = Files.createTempDirectory("foliage-mask").toFile().also(block)

    private fun File.mask(name: String, bytes: ByteArray) {
        File(this, "assets/$name").mkdirs()
        File(this, "assets/$name/${foliageMaskFileName(1)}").writeBytes(bytes)
    }

    @Test
    fun aMaskRoundTripsByteForByte() {
        val dir = project { it.mask("foliage", ByteArray(4 * 4) { (it * 7 % 256).toByte() }) }
        val masks = FoliageMaskFile(FileLoader(dir))
        val bytes = masks.read("foliage", 1, 4)
        assertEquals(16, bytes.size)
        assertEquals(0, bytes[0].toInt())
        assertEquals(7, bytes[1].toInt())
        val out = File(dir, "out.mask")
        masks.write(out, bytes, 4)
        assertArrayEquals(bytes, out.readBytes())
        dir.deleteRecursively()
    }

    @Test
    fun aMissingMaskIsFullDensity() {
        val dir = project { }
        val bytes = FoliageMaskFile(FileLoader(dir)).read("nowhere", 1, 8)
        assertEquals(64, bytes.size)
        for (b in bytes) assertEquals(FOLIAGE_MASK_MAX, b.toInt() and 0xFF)
        dir.deleteRecursively()
    }

    @Test
    fun aTruncatedMaskNamesTheFile() {
        val dir = project { it.mask("foliage", ByteArray(15)) }
        val error = assertThrows(FoliageMaskException::class.java) {
            FoliageMaskFile(FileLoader(dir)).read("foliage", 1, 4)
        }
        assertTrue(error.message!!, error.message!!.contains(foliageMaskFileName(1)))
        assertTrue(error.message!!, error.message!!.contains("16"))
        dir.deleteRecursively()
    }

    @Test
    fun writingTheWrongLengthIsRefused() {
        val dir = project { }
        assertThrows(FoliageMaskException::class.java) {
            FoliageMaskFile(FileLoader(dir)).write(File(dir, "out.mask"), ByteArray(15), 4)
        }
        dir.deleteRecursively()
    }
}
