/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.store

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Writes [bytes] to [target] through a temporary file in the same folder and an atomic move. */
fun writeAtomically(target: Path, bytes: ByteArray) {
    Files.createDirectories(target.parent)
    val temp = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
    try {
        Files.write(temp, bytes)
        moveAtomically(temp, target)
    } finally {
        Files.deleteIfExists(temp)
    }
}

/** Renames [from] to [to] atomically, replacing a file at [to]. */
fun moveAtomically(from: Path, to: Path) {
    try {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: AtomicMoveNotSupportedException) {
        throw IOException("The file system cannot rename $from atomically", e)
    }
}

/** Deletes a file or a folder tree; a missing path is fine. */
fun deleteTree(path: Path) {
    if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
    Files.walk(path).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
}
