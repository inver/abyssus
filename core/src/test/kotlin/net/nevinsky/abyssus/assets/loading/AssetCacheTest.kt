/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.assets.AssetLog
import net.nevinsky.abyssus.assets.files.AssetFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class AssetCacheTest {
    private class Res(val name: String) : Disposable {
        var disposed = false
        override fun dispose() {
            disposed = true
        }
    }

    private val queue = ArrayDeque<Runnable>()
    private val executor = Executor { queue += it }
    private val logged = mutableListOf<String>()
    private val log = AssetLog { message, _ -> logged += message }
    private val prepares = mutableListOf<String>()
    private val builds = mutableListOf<String>()

    /** Prepared data is the asset name itself, so `build` / `upload` / `discard` receive the name. */
    private inner class FakeLoader(
        val build: (String) -> Res = { Res(it) },
        val upload: (String) -> Boolean = { true },
        val discarded: MutableList<String> = mutableListOf(),
    ) : AssetLoader<String, Res> {
        override fun prepare(files: AssetFiles, name: String): String? = error("the cache prepares through its own lambda")
        override fun upload(prepared: String): Boolean = upload.invoke(prepared)
        override fun build(prepared: String): Res {
            builds += prepared
            return build.invoke(prepared)
        }

        override fun discard(prepared: String) {
            discarded += prepared
        }
    }

    private fun cache(
        prepare: (String) -> String? = { it },
        loader: AssetLoader<String, Res> = FakeLoader(),
    ) = AssetCache(executor, { prepares += it; prepare(it) }, loader, log)

    private fun runBackground() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }

    @Test
    fun loadsOnceAndSharesTheResult() {
        val c = cache()
        c.request("a")
        c.request("a")
        runBackground()
        assertEquals(listOf("a"), prepares)
        assertNull(c.get("a"))
        assertTrue(c.pump())
        val first = c.get("a")!!
        c.request("a")
        runBackground()
        assertSame(first, c.get("a"))
        assertEquals(listOf("a"), builds)
    }

    @Test
    fun buildsOnePerPump() {
        val c = cache()
        c.request("a")
        c.request("b")
        runBackground()
        c.pump()
        assertEquals(1, builds.size)
        c.pump()
        assertEquals(2, builds.size)
    }

    @Test
    fun failureIsRememberedAndNotRetried() {
        val c = cache(prepare = { null })
        c.request("bad")
        runBackground()
        c.pump()
        c.request("bad")
        runBackground()
        assertEquals(1, prepares.size)
        assertNull(c.get("bad"))
        assertTrue(builds.isEmpty())
    }

    @Test
    fun aFailedAssetIsLoggedOnce() {
        val c = cache(prepare = { if (it == "bad") error("broken") else it })
        repeat(3) {
            c.request("bad")
            c.request("good")
            runBackground()
            c.pump(maxSteps = 4)
        }
        assertEquals(listOf("Failed to load asset 'bad'"), logged)
        assertNull(c.get("bad"))
        assertEquals("good", c.get("good")!!.name)
    }

    @Test
    fun buildFailureDoesNotStopOthers() {
        val c = cache(loader = FakeLoader(build = { n -> if (n == "x") error("boom") else Res(n) }))
        c.request("x")
        c.request("y")
        runBackground()
        c.pump(2)
        assertNull(c.get("x"))
        assertEquals("y", c.get("y")!!.name)
    }

    @Test
    fun retainDisposesUnusedAndAllowsReload() {
        val c = cache()
        c.request("a")
        c.request("b")
        runBackground()
        c.pump(2)
        val a = c.get("a")!!
        val b = c.get("b")!!
        c.retain(setOf("b"))
        assertTrue(a.disposed)
        assertFalse(b.disposed)
        assertNull(c.get("a"))
        c.request("a")
        runBackground()
        c.pump()
        assertEquals(3, prepares.size)
        assertFalse(c.get("a")!!.disposed)
    }

    @Test
    fun forgottenBeforeItStartsIsNeverPrepared() {
        val c = cache()
        c.request("a")
        c.retain(emptySet())
        runBackground()
        assertFalse(c.pump())
        assertTrue(prepares.isEmpty())
        assertNull(c.get("a"))
    }

    @Test
    fun forgottenWhilePreparingIsDiscardedWithoutReachingTheGlThread() {
        val loader = FakeLoader()
        val discarded = loader.discarded
        lateinit var c: AssetCache<String, Res>
        c = AssetCache(executor, { c.retain(emptySet()); it }, loader, log)
        c.request("a")
        runBackground()
        assertEquals(listOf("a"), discarded)
        assertFalse(c.pump())
        assertTrue(builds.isEmpty())
    }

    @Test
    fun aResultArrivingAfterDisposeIsReleased() {
        val loader = FakeLoader()
        val discarded = loader.discarded
        lateinit var c: AssetCache<String, Res>
        c = AssetCache(executor, { c.dispose(); it }, loader, log)
        c.request("a")
        runBackground()
        assertEquals(listOf("a"), discarded)
    }

    @Test
    fun aResultArrivingAfterAbandonIsReleasedAndTheNextRequestLoadsAgain() {
        val loader = FakeLoader()
        val discarded = loader.discarded
        var abandonNext = true
        lateinit var c: AssetCache<String, Res>
        c = AssetCache(executor, {
            if (abandonNext) c.abandon()
            abandonNext = false
            it
        }, loader, log)
        c.request("a")
        runBackground()
        assertEquals(listOf("a"), discarded)
        c.request("a")
        runBackground()
        c.pump()
        assertEquals("a", c.get("a")!!.name)
    }

    @Test
    fun disposeReleasesEverything() {
        val c = cache()
        c.request("a")
        runBackground()
        c.pump()
        val a = c.get("a")!!
        c.dispose()
        assertTrue(a.disposed)
    }

    @Test
    fun abandonForgetsWithoutDisposingAndReloads() {
        val c = cache()
        c.request("a")
        runBackground()
        c.pump()
        val old = c.get("a")!!
        c.abandon()
        assertFalse("objects of a lost GL context must not be touched", old.disposed)
        assertNull(c.get("a"))
        c.request("a")
        runBackground()
        c.pump()
        assertEquals(2, builds.size)
        assertTrue(c.get("a") !== old)
    }

    @Test
    fun isLoadingUntilEveryRequestIsBuiltOrFailed() {
        val c = cache(prepare = { if (it == "bad") null else it })
        assertFalse(c.isLoading())
        c.request("a")
        c.request("bad")
        assertTrue(c.isLoading())
        runBackground()
        assertTrue("prepared but not built yet", c.isLoading())
        c.pump(1)
        assertTrue(c.isLoading() || builds.size == 1)
        c.pump(5)
        assertFalse(c.isLoading())
    }

    @Test
    fun slowGpuWorkIsSpreadOverSeveralSteps() {
        val slices = mutableMapOf<String, Int>()
        val c = AssetCache(
            executor, { it },
            FakeLoader(upload = { d -> val n = (slices[d] ?: 0) + 1; slices[d] = n; n >= 3 }), log,
        )
        c.request("big")
        c.request("small")
        runBackground()
        c.pump(1) // big: slice 1
        c.pump(1) // small: slice 1 (the other asset gets its turn)
        assertEquals(mapOf("big" to 1, "small" to 1), slices)
        assertTrue(c.isLoading())
        assertTrue(builds.isEmpty())
        c.pump(1); c.pump(1) // slice 2 each
        assertTrue(builds.isEmpty())
        c.pump(1) // big done
        assertEquals(listOf("big"), builds)
        assertNull(c.get("small"))
        c.pump(1)
        assertEquals(listOf("big", "small"), builds)
        assertFalse(c.isLoading())
    }

    @Test
    fun anAssetThatFailsInTheMiddleIsDiscardedAndRemembered() {
        val loader = FakeLoader(upload = { error("upload failed") })
        val discarded = loader.discarded
        val c = AssetCache(executor, { it }, loader, log)
        c.request("x")
        runBackground()
        c.pump()
        assertEquals(listOf("x"), discarded)
        assertFalse(c.isLoading())
        assertNull(c.get("x"))
    }
}
