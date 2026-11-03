/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.io

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
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

    /**
     * Reads [file] as a [type] with [json] (a null for a primitive is an error; [lenient] ignores unknown properties) and
     * runs [validate] on it; a file that fails either way is set aside like any unreadable one.
     */
    fun <T : Any> readJson(
        file: Path,
        json: ObjectMapper,
        type: Class<T>,
        lenient: Boolean = false,
        validate: (T) -> Unit = {},
        damaged: () -> Unit = {},
    ): T? = read(file, {
        val read = json.copy().configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, !lenient)
            .readValue(it.toFile(), type)
        validate(read)
        read
    }, damaged)

    /** Writes [value] to [file] as indented JSON with `\n` line ends; see [save]. */
    fun saveJson(file: Path, prefix: String, json: ObjectMapper, value: Any) {
        save(file, prefix) { temp ->
            Files.writeString(
                temp,
                json.copy().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(value).replace("\r\n", "\n") + "\n",
            )
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
