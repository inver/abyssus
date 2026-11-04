/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.release

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GitHubReleaseSourceTest {
    private lateinit var server: HttpServer
    private val requests = AtomicInteger()
    private val seenIfNoneMatch = mutableListOf<String?>()
    private var body = ""
    private var delayMillis = 0L
    private var redirectTo: String? = null

    private val base get() = URI.create("http://127.0.0.1:${server.address.port}")

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            if (delayMillis > 0) Thread.sleep(delayMillis)
            val etag = exchange.requestHeaders.getFirst("If-None-Match")
            seenIfNoneMatch += etag
            val target = redirectTo
            when {
                target != null -> {
                    exchange.responseHeaders.add("Location", target)
                    exchange.sendResponseHeaders(302, -1)
                }
                etag == "\"v1\"" -> exchange.sendResponseHeaders(304, -1)
                else -> {
                    val bytes = body.toByteArray()
                    exchange.responseHeaders.add("ETag", "\"v1\"")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
            }
            exchange.close()
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun release(tag: String, prerelease: Boolean = false, draft: Boolean = false) =
        """{"tag_name":"$tag","prerelease":$prerelease,"draft":$draft,"html_url":"https://github.com/x/y/releases/tag/$tag",
            "assets":[{"name":"manifest.json","browser_download_url":"https://github.com/x/y/releases/download/$tag/manifest.json"}]}"""

    private fun source(timeout: Duration = Duration.ofSeconds(5), cache: ResponseCache = MemoryResponseCache()) =
        GitHubReleaseSource(
            HttpFetcher({ it.host == "127.0.0.1" }, timeout, "control-line/test"), "x/y", "control-line-v", cache, base
        )

    @Test
    fun `canary newer than stable is ignored`() {
        body = "[" + listOf(
            release("canary", prerelease = true), release("control-line-v1.9.0"), release("control-line-v1.10.0"),
            release("control-line-v2.0.0", prerelease = true), release("control-line-v3.0.0", draft = true),
        ).joinToString(",") + "]"
        val found = source().latestStable()
        assertEquals("control-line-v1.10.0", found?.tag)
        assertEquals("manifest.json", found?.asset("manifest.json")?.name)
        assertNull(found?.asset("missing"))
    }

    @Test
    fun `plugin release is ignored`() {
        body = "[" + listOf(release("v0.9.4"), release("control-line-v1.0.1"), release("control-line-vNEXT")).joinToString(",") + "]"
        assertEquals("control-line-v1.0.1", source().latestStable()?.tag)
        body = "[" + release("v0.9.4") + "]"
        assertNull(source().latestStable())
    }

    @Test
    fun `not modified reuses the cached answer`() {
        body = "[" + release("control-line-v1.0.1") + "]"
        val cache = MemoryResponseCache()
        assertEquals("control-line-v1.0.1", source(cache = cache).latestStable()?.tag)
        body = "[]" // would be a different answer if it were read again
        assertEquals("control-line-v1.0.1", source(cache = cache).latestStable()?.tag)
        assertEquals(listOf(null, "\"v1\""), seenIfNoneMatch)
    }

    @Test
    fun `slow server times out`() {
        body = "[]"
        delayMillis = 1500
        try {
            source(timeout = Duration.ofMillis(200)).latestStable()
            fail("expected a timeout")
        } catch (e: ReleaseSourceException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `error status is a failure`() {
        server.removeContext("/")
        server.createContext("/") { it.sendResponseHeaders(500, -1); it.close() }
        try {
            source().latestStable()
            fail("expected a failure")
        } catch (e: ReleaseSourceException) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun `redirect to a disallowed host is refused`() {
        redirectTo = "http://localhost:${server.address.port}/elsewhere"
        try {
            source().latestStable()
            fail("expected a refusal")
        } catch (e: ReleaseSourceException) {
            assertTrue(e.message!!, e.message!!.contains("Refusing"))
        }
        assertEquals(1, requests.get())
    }

    @Test
    fun `redirect to an allowed host is followed`() {
        body = "[" + release("control-line-v1.0.1") + "]"
        server.createContext("/moved") { exchange ->
            requests.incrementAndGet()
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("ETag", "\"v2\"")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        redirectTo = "http://127.0.0.1:${server.address.port}/moved"
        assertEquals("control-line-v1.0.1", source().latestStable()?.tag)
    }

    @Test
    fun `github policy allows only https to github hosts`() {
        val policy = githubHostPolicy()
        assertTrue(policy.allows(URI.create("https://api.github.com/repos/x/y/releases")))
        assertTrue(policy.allows(URI.create("https://github.com/x/y/releases/download/t/a.zip")))
        assertTrue(policy.allows(URI.create("https://objects.githubusercontent.com/abc")))
        assertTrue(!policy.allows(URI.create("http://github.com/x")))
        assertTrue(!policy.allows(URI.create("https://github.com.evil.example/x")))
        assertTrue(!policy.allows(URI.create("https://evilgithubusercontent.com/x")))
    }
}
