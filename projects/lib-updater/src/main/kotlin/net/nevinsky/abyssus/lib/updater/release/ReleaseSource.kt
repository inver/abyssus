/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.release

import java.net.URI
import net.nevinsky.abyssus.lib.updater.manifest.VersionOrder

/** One file attached to a release; only its [name] and [url] are taken from the release list. */
data class ReleaseAsset(val name: String, val url: URI)

/** A published, stable release: its [tag], numeric [version], page address and assets. */
data class Release(val tag: String, val version: VersionOrder, val pageUrl: String, val assets: List<ReleaseAsset>) {
    fun asset(name: String): ReleaseAsset? = assets.firstOrNull { it.name == name }
}

/** The release list could not be read. */
class ReleaseSourceException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Where the newest stable release comes from. */
interface ReleaseSource {
    /** The newest stable release, `null` when there is none. Throws [ReleaseSourceException] on a failure. */
    fun latestStable(): Release?
}

/** A remembered answer, kept so a `304 Not Modified` can reuse it. */
data class CachedResponse(val etag: String, val body: ByteArray)

/** Where [GitHubReleaseSource] keeps the last answer and its ETag. */
interface ResponseCache {
    fun load(): CachedResponse?

    fun store(response: CachedResponse)
}

/** A cache that lives as long as the object does. */
class MemoryResponseCache : ResponseCache {
    private var last: CachedResponse? = null

    override fun load() = last

    override fun store(response: CachedResponse) {
        last = response
    }
}
