/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.release

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.nevinsky.abyssus.lib.updater.manifest.parseVersionOrNull
import java.net.URI

/**
 * Reads `GET <apiBase>/repos/<repository>/releases` anonymously and keeps the stable releases whose tag starts with
 * [tagPrefix] followed by a numeric dotted version. Drafts and pre-releases (the canary) never count. The answer is
 * requested with the cached ETag; a 304 reuses the cached body, so it costs no rate limit.
 */
class GitHubReleaseSource(
    private val fetcher: HttpFetcher,
    private val repository: String,
    private val tagPrefix: String,
    private val cache: ResponseCache,
    private val apiBase: URI = URI.create("https://api.github.com"),
    private val maxBytes: Int = 8 * 1024 * 1024,
) : ReleaseSource {
    private val json = ObjectMapper()

    override fun latestStable(): Release? {
        val cached = cache.load()
        val headers = buildMap {
            put("Accept", "application/vnd.github+json")
            cached?.let { put("If-None-Match", it.etag) }
        }
        val body = try {
            fetcher.get(URI.create("$apiBase/repos/$repository/releases?per_page=100"), headers).use { fetched ->
                when {
                    fetched.status == 304 && cached != null -> cached.body
                    fetched.status == 200 -> fetched.body.readNBytes(maxBytes + 1).also {
                        if (it.size > maxBytes) throw ReleaseSourceException("The release list is larger than $maxBytes bytes")
                        fetched.etag?.let { etag -> cache.store(CachedResponse(etag, it)) }
                    }

                    else -> throw ReleaseSourceException("The release list answered HTTP ${fetched.status}")
                }
            }
        } catch (e: FetchException) {
            throw ReleaseSourceException(e.message ?: "The release list could not be fetched", e)
        }
        return parse(body)
    }

    private fun parse(body: ByteArray): Release? {
        val root = try {
            json.readTree(body)
        } catch (e: Exception) {
            throw ReleaseSourceException("The release list is not valid JSON", e)
        }
        if (!root.isArray) throw ReleaseSourceException("The release list is not a JSON array")
        return root.mapNotNull { stable(it) }.maxByOrNull { it.version }
    }

    private fun stable(node: JsonNode): Release? {
        if (node.path("draft").asBoolean(false) || node.path("prerelease").asBoolean(false)) return null
        val tag = node.path("tag_name").asText("")
        if (!tag.startsWith(tagPrefix)) return null
        val version = parseVersionOrNull(tag.removePrefix(tagPrefix)) ?: return null
        val assets = node.path("assets").mapNotNull { asset ->
            val name = asset.path("name").asText("")
            val url = asset.path("browser_download_url").asText("")
            if (name.isEmpty() || url.isEmpty()) null else ReleaseAsset(name, URI.create(url))
        }
        return Release(tag, version, node.path("html_url").asText(""), assets)
    }
}
