/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.io

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** JSON codecs and validation belong to callers, including gzip recordings; replacement and recovery stay shared. */
class AtomicJsonStore {
    fun <T : Any> read(file: Path, decode: (Path) -> T, damaged: () -> Unit = {}): T? {
        if (!Files.exists(file)) {
            return null
        }
        return try {
            decode(file)
        } catch (_: Exception) {
            Files.move(
                file,
                file.resolveSibling(file.fileName.toString() + ".bad"),
                StandardCopyOption.REPLACE_EXISTING
            )
            damaged()
            null
        }
    }

    /** A failed encoding never replaces the previous main file. Always removes the temporary file. */
    fun save(file: Path, prefix: String, encode: (Path) -> Unit) {
        Files.createDirectories(file.parent)
        val temp = Files.createTempFile(file.parent, prefix, ".tmp")
        try {
            encode(temp)
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
