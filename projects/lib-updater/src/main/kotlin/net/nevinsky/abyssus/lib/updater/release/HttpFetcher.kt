/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.release

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Which addresses the updater may talk to; every request and every redirect target is checked against it. */
fun interface HostPolicy {
    fun allows(uri: URI): Boolean
}

/**
 * HTTPS to GitHub's API, its release pages and the hosts that serve release assets and stored attestation bundles
 * (`*.blob.core.windows.net`; what comes from there is checked cryptographically, so the host adds no trust).
 */
fun githubHostPolicy() = HostPolicy { uri ->
    val host = uri.host?.lowercase() ?: return@HostPolicy false
    uri.scheme.equals("https", ignoreCase = true) &&
        (host == "github.com" || host == "api.github.com" || host.endsWith(".githubusercontent.com") ||
            host.endsWith(".blob.core.windows.net"))
}

/** A failed or refused request. */
class FetchException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** One answer: the HTTP [status], its `ETag` and the [body] to read and close. */
class Fetched(val status: Int, val etag: String?, val body: InputStream) : AutoCloseable {
    override fun close() = body.close()
}

/**
 * GET over `java.net.http` with a [timeout] for connecting and for the answer, no automatic redirects (each hop is
 * checked against [policy], at most [maxRedirects] of them) and a fixed User-Agent.
 */
class HttpFetcher(
    private val policy: HostPolicy,
    private val timeout: Duration,
    private val userAgent: String,
    private val maxRedirects: Int = 5,
) {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(timeout).build()

    fun get(uri: URI, headers: Map<String, String> = emptyMap()): Fetched {
        var target = uri
        repeat(maxRedirects + 1) {
            if (!policy.allows(target)) throw FetchException("Refusing to contact $target")
            val request = HttpRequest.newBuilder(target).timeout(timeout).header("User-Agent", userAgent).apply {
                headers.forEach { (name, value) -> header(name, value) }
            }.GET().build()
            val response = try {
                client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            } catch (e: IOException) {
                throw FetchException("Request to $target failed: ${e.message ?: e.javaClass.simpleName}", e)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw FetchException("Request to $target was interrupted", e)
            }
            if (response.statusCode() in REDIRECTS) {
                response.body().close()
                val location = response.headers().firstValue("Location").orElse(null)
                    ?: throw FetchException("Redirect from $target without a Location")
                target = target.resolve(location)
            } else {
                return Fetched(response.statusCode(), response.headers().firstValue("ETag").orElse(null), response.body())
            }
        }
        throw FetchException("Too many redirects from $uri")
    }

    /** The whole body of a 200 answer, at most [maxBytes]; other statuses throw. */
    fun getBytes(uri: URI, maxBytes: Int, headers: Map<String, String> = emptyMap()): ByteArray =
        get(uri, headers).use { fetched ->
            if (fetched.status != 200) throw FetchException("$uri answered HTTP ${fetched.status}")
            fetched.body.readNBytes(maxBytes + 1).also {
                if (it.size > maxBytes) throw FetchException("$uri is larger than $maxBytes bytes")
            }
        }
}

private val REDIRECTS = setOf(301, 302, 303, 307, 308)
