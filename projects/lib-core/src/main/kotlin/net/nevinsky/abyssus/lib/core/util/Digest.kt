/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.util

import java.io.InputStream
import java.security.MessageDigest

fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

/** Reads to EOF with bounded memory. The caller retains ownership of [input]. */
fun sha256(input: InputStream): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(1 shl 16)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) {
            break
        }
        digest.update(buffer, 0, count)
    }
    return digest.digest()
}

/** Lowercase, unsigned hex with two characters per byte, including leading zeros. */
fun hexLower(bytes: ByteArray): String = buildString(bytes.size * 2) {
    val digits = "0123456789abcdef"
    for (byte in bytes) {
        val value = byte.toInt() and 0xff
        append(digits[value ushr 4])
        append(digits[value and 0xf])
    }
}

fun sha256Hex(bytes: ByteArray): String = hexLower(sha256(bytes))

fun sha256Hex(input: InputStream): String = hexLower(sha256(input))
