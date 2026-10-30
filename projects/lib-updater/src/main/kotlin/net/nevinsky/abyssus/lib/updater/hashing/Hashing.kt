/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.hashing

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Lower-case hex of [bytes]. */
fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

/** SHA-256 of a file, streamed. */
fun sha256Hex(file: Path): String = Files.newInputStream(file).use { sha256Hex(it) }

fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
    }
    return hex(digest.digest())
}
