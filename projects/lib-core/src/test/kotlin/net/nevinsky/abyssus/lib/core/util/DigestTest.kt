/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.util

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DigestTest {
    private val abc = "abc".toByteArray()
    private val abcHex = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun knownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(byteArrayOf()))
        assertEquals(abcHex, sha256Hex(abc))
        assertArrayEquals(byteArrayOf(0xba.toByte(), 0x78, 0x16, 0xbf.toByte()), sha256(abc).copyOf(4))
    }

    @Test
    fun hexKeepsLeadingZerosAndUnsignedBytes() {
        assertEquals("00017f80ff", hexLower(byteArrayOf(0, 1, 127, -128, -1)))
    }

    @Test
    fun streamingHandlesShortReadsAndLeavesOwnershipWithCaller() {
        val input = object : ByteArrayInputStream(abc) {
            var closed = false
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 1))
            override fun close() { closed = true }
        }
        assertEquals(abcHex, sha256Hex(input))
        assertFalse(input.closed)
    }
}
