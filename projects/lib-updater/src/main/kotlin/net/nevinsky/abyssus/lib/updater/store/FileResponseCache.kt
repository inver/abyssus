/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.store

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import net.nevinsky.abyssus.lib.updater.release.CachedResponse
import net.nevinsky.abyssus.lib.updater.release.ResponseCache

/** Keeps the last release list and its ETag in [folder] (`releases.etag`, `releases.json`), so a start can send `If-None-Match`. */
class FileResponseCache(private val folder: Path) : ResponseCache {
    private val etagFile = folder.resolve("releases.etag")
    private val bodyFile = folder.resolve("releases.json")

    override fun load(): CachedResponse? = try {
        if (Files.isRegularFile(etagFile) && Files.isRegularFile(bodyFile)) {
            CachedResponse(Files.readString(etagFile).trim(), Files.readAllBytes(bodyFile))
        } else null
    } catch (e: IOException) {
        null
    }

    override fun store(response: CachedResponse) {
        writeAtomically(bodyFile, response.body)
        writeAtomically(etagFile, response.etag.toByteArray())
    }
}
