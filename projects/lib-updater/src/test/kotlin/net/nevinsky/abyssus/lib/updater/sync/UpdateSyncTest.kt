/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.sync

import net.nevinsky.abyssus.lib.updater.hashing.sha256Hex
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import net.nevinsky.abyssus.lib.updater.manifest.Manifest
import net.nevinsky.abyssus.lib.updater.manifest.ManifestFile
import net.nevinsky.abyssus.lib.updater.manifest.ManifestPart
import net.nevinsky.abyssus.lib.updater.manifest.VerificationException
import net.nevinsky.abyssus.lib.updater.manifest.bindManifest
import net.nevinsky.abyssus.lib.updater.attest.ManifestAuthenticator
import net.nevinsky.abyssus.lib.updater.manifest.writeManifest
import net.nevinsky.abyssus.lib.updater.release.GitHubReleaseSource
import net.nevinsky.abyssus.lib.updater.release.HttpFetcher
import net.nevinsky.abyssus.lib.updater.release.MemoryResponseCache
import net.nevinsky.abyssus.lib.updater.store.UpdateSettings
import net.nevinsky.abyssus.lib.updater.store.UpdateState
import net.nevinsky.abyssus.lib.updater.store.UpdateStatus
import net.nevinsky.abyssus.lib.updater.store.VersionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateSyncTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: HttpServer
    private val assets = ConcurrentHashMap<String, ByteArray>()
    private val requests = AtomicInteger()
    private var releaseList = "[]"
    private val cutOff = ConcurrentHashMap.newKeySet<String>()

    /** The crypto of the attestation is tested on a real GitHub bundle (ReleaseAttestationTest); here only what the sync does with the answer. */
    private var attested = true
    private val authenticator = ManifestAuthenticator { release, bytes ->
        if (!attested) throw VerificationException("GitHub has no release attestation for ${release.tag}")
        bindManifest(bytes)
    }

    private lateinit var updates: Path
    private lateinit var seed: Path
    private lateinit var home: Path

    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun start() {
        updates = tmp.newFolder("updates").toPath()
        seed = tmp.newFolder("seed").toPath()
        home = tmp.newFolder("home").toPath()
        Files.writeString(seed.resolve("game.jar"), "seed jar")
        Files.createDirectories(seed.resolve("project"))
        Files.writeString(seed.resolve("project/level.scene"), "seed scene")
        Files.writeString(home.resolve("scores.json"), "[1,2,3]")
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            val path = exchange.requestURI.path
            val bytes = if (path == "/repos/x/y/releases") releaseList.toByteArray() else assets[path]
            if (bytes == null) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                exchange.responseHeaders.add("ETag", "\"e\"")
                if (path in cutOff) {
                    // the network drops halfway: the promised length is never delivered
                    exchange.sendResponseHeaders(200, bytes.size.toLong() + 100)
                    exchange.responseBody.write(bytes, 0, bytes.size / 2)
                    exchange.responseBody.flush()
                    exchange.close()
                    return@createContext
                }
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun zip(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(data.toByteArray())
                z.closeEntry()
            }
        }
    }.toByteArray()

    /** Publishes version [version]: a code part with game.jar and a project part with the scene. */
    private fun publish(
        version: String, jar: String = "jar $version", scene: String = "seed scene", digest: String = "digest",
        corruptCode: Boolean = false,
    ) {
        val tag = "control-line-v$version"
        val codeZip = zip("game.jar" to jar)
        val projectZip = zip("project/level.scene" to scene)
        val code = ManifestPart("code-$version.zip", codeZip.size.toLong(), sha256Hex(codeZip))
        val project = ManifestPart("project-$version.zip", projectZip.size.toLong(), sha256Hex(projectZip))
        val manifest = writeManifest(
            Manifest(
                1, "control-line", version, "Main", digest, listOf(code, project),
                listOf(
                    ManifestFile("game.jar", jar.length.toLong(), sha256Hex(jar.toByteArray()), code.name),
                    ManifestFile("project/level.scene", scene.length.toLong(), sha256Hex(scene.toByteArray()), project.name),
                )
            )
        )
        val served = if (corruptCode) codeZip.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() } else codeZip
        val prefix = "/dl/$tag"
        assets["$prefix/manifest.json"] = manifest
        assets["$prefix/${code.name}"] = served
        assets["$prefix/${project.name}"] = projectZip
        val names = listOf("manifest.json", code.name, project.name)
        val entry = """{"tag_name":"$tag","prerelease":false,"draft":false,"html_url":"$base/page/$tag","assets":[""" +
            names.joinToString(",") { """{"name":"$it","browser_download_url":"$base$prefix/$it"}""" } + "]}"
        releaseList = "[" + (listOf(entry) + releaseList.removePrefix("[").removeSuffix("]").takeIf { it.isNotBlank() }.orEmpty().let { if (it.isEmpty()) emptyList() else listOf(it) }).joinToString(",") + "]"
    }

    private fun sync(
        running: String = "1.0.0", enabled: Boolean = true, installedDigest: String = "digest",
    ): UpdateSync {
        val fetcher = HttpFetcher({ it.host == "127.0.0.1" }, Duration.ofSeconds(5), "control-line/test")
        val settings = UpdateSettings(updates.resolve("settings.properties"), if (enabled) emptyMap() else mapOf("CONTROL_LINE_UPDATES" to "off"), { null })
        return UpdateSync(
            GitHubReleaseSource(fetcher, "x/y", "control-line-v", MemoryResponseCache(), URI.create(base)),
            fetcher, authenticator, VersionStore(updates, seed), settings,
            UpdateState(updates.resolve("state.json")), UpdateStatus(updates.resolve("status.json")),
            "control-line", running, installedDigest,
        )
    }

    private fun status() = UpdateStatus(updates.resolve("status.json")).read()!!

    private fun resolved() = VersionStore(updates, seed).resolve(emptySet())

    @Test
    fun `update is applied at the next start only`() {
        publish("1.0.1", scene = "new scene")
        assertEquals(null, resolved().version)
        val outcome = sync().run()
        assertEquals(SyncOutcome.Applied("1.0.1"), outcome)
        val next = resolved()
        assertEquals("1.0.1", next.version)
        assertEquals("jar 1.0.1", Files.readString(next.dir.resolve("game.jar")))
        assertEquals("new scene", Files.readString(next.dir.resolve("project/level.scene")))
        val s = status()
        assertEquals("1.0.0", s.runningVersion)
        assertEquals("1.0.1", s.waitingVersion)
        assertTrue(s.appliesAtNextStart)
    }

    @Test
    fun `project only change downloads only the project part`() {
        // the seed's game.jar equals the new release's, so the code part is not needed
        Files.writeString(seed.resolve("game.jar"), "same jar")
        publish("1.0.1", jar = "same jar", scene = "new scene")
        val before = requests.get()
        assertEquals(SyncOutcome.Applied("1.0.1"), sync().run())
        assertFalse(assets.keys.isEmpty())
        assertEquals("same jar", Files.readString(resolved().dir.resolve("game.jar")))
        // list, manifest and the project part: three requests, no code part
        assertEquals(before + 3, requests.get())
    }

    @Test
    fun `corrupted part is discarded and the file is named`() {
        publish("1.0.1", corruptCode = true)
        val outcome = sync().run() as SyncOutcome.Rejected
        assertEquals("code-1.0.1.zip", outcome.file)
        assertEquals(null, resolved().version)
        assertFalse(Files.exists(updates.resolve("staging/1.0.1")))
        val s = status()
        assertEquals("1.0.1", s.failedVersion)
        assertEquals("code-1.0.1.zip", s.failedFile)
        assertNotNull(s.reason)
    }

    @Test
    fun `network cut mid download leaves the current version running and nothing staged`() {
        publish("1.0.1")
        cutOff += "/dl/control-line-v1.0.1/code-1.0.1.zip"
        val outcome = sync().run()
        assertTrue(outcome.toString(), outcome is SyncOutcome.Rejected)
        assertEquals(null, resolved().version)
        assertFalse(Files.exists(updates.resolve("staging/1.0.1")))
        // the next check, with the network back, restarts the download and applies the update
        cutOff.clear()
        assertEquals(SyncOutcome.Applied("1.0.1"), sync().run())
        assertEquals("1.0.1", resolved().version)
    }

    @Test
    fun `manifest without a GitHub attestation is ignored and nothing is downloaded`() {
        publish("1.0.1")
        attested = false
        val before = requests.get()
        val outcome = sync().run() as SyncOutcome.Rejected
        assertEquals("manifest.json", outcome.file)
        assertEquals(before + 2, requests.get()) // list and manifest: no part downloaded
        assertEquals(null, resolved().version)
        assertTrue(status().reason!!.contains("attestation"))
        // once GitHub has published the attestation the next check applies the update
        attested = true
        assertEquals(SyncOutcome.Applied("1.0.1"), sync().run())
    }

    @Test
    fun `lower version is ignored`() {
        publish("1.0.4")
        val outcome = sync(running = "1.0.9").run()
        assertEquals(SyncOutcome.UpToDate, outcome)
        assertEquals(null, resolved().version)
    }

    @Test
    fun `equal version downloads nothing`() {
        publish("1.0.1")
        val before = requests.get()
        assertEquals(SyncOutcome.UpToDate, sync(running = "1.0.1").run())
        assertEquals(before + 1, requests.get())
    }

    @Test
    fun `changed installer content needs a new installer`() {
        publish("1.0.1", digest = "other digest")
        val outcome = sync().run() as SyncOutcome.NewInstallerNeeded
        assertEquals("1.0.1", outcome.version)
        assertEquals(null, resolved().version)
        val s = status()
        assertTrue(s.newInstallerNeeded)
        assertTrue(s.releaseUrl!!.endsWith("control-line-v1.0.1"))
    }

    @Test
    fun `updates off makes no request`() {
        publish("1.0.1")
        val before = requests.get()
        assertEquals(SyncOutcome.Off, sync(enabled = false).run())
        assertEquals(before, requests.get())
        assertEquals(null, resolved().version)
    }

    @Test
    fun `release server down is a rejection not a crash`() {
        server.stop(0)
        assertTrue(sync().run() is SyncOutcome.Rejected)
        assertEquals(null, resolved().version)
    }

    @Test
    fun `scores outside the update folder are untouched`() {
        publish("1.0.1")
        sync().run()
        assertEquals("[1,2,3]", Files.readString(home.resolve("scores.json")))
        assertEquals(listOf("scores.json"), Files.list(home).use { it.map { p -> p.fileName.toString() }.toList() })
    }
}
