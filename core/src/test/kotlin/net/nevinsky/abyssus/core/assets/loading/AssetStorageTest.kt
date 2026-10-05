/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.testing.RecordingLogger
import org.slf4j.Logger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class AssetStorageTest {
    private class Res(val name: String) : Disposable {
        var disposed = false
        override fun dispose() {
            disposed = true
        }
    }

    private val queue = ArrayDeque<Runnable>()
    private val executor = Executor { queue += it }
    private val recorder = RecordingLogger()
    private val log: Logger = recorder
    private val logged: List<String> get() = recorder.warnings
    private val prepares = mutableListOf<String>()
    private val builds = mutableListOf<String>()

    /** Prepared data is the asset name itself, so `build` / `upload` / `discard` receive the name. */
    private inner class FakeLoader(
        val prepare: (String) -> String? = { it },
        val build: (String) -> Res = { Res(it) },
        val upload: (String) -> Boolean = { true },
        val discarded: MutableList<String> = mutableListOf(),
    ) : AssetLoader<String, Res> {
        override fun loadPrepared(meta: AssetMeta<Any>): String? = error("the storage prepares by name")
        override fun prepare(name: String): String? {
            prepares += name
            return prepare.invoke(name)
        }
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
        loader: AssetLoader<String, Res> = FakeLoader(prepare),
    ) = AssetStorage(executor, loader, log)

    private fun runBackground() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }

    @Test
    fun progressIsLoggedAtDebugLevelAndNeverAsAWarning() {
        val c = cache()
        c.request("a")
        runBackground()
        c.pump()
        assertEquals(emptyList<String>(), logged)
        val debug = recorder.messages(org.slf4j.event.Level.DEBUG)
        assertTrue(debug.toString(), "Loading asset 'a'" in debug && "Asset 'a' is ready" in debug)
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
        lateinit var c: AssetStorage<String, Res>
        c = AssetStorage(executor, FakeLoader(prepare = { c.retain(emptySet()); it }, discarded = discarded), log)
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
        lateinit var c: AssetStorage<String, Res>
        c = AssetStorage(executor, FakeLoader(prepare = { c.dispose(); it }, discarded = discarded), log)
        c.request("a")
        runBackground()
        assertEquals(listOf("a"), discarded)
    }

    @Test
    fun aResultArrivingAfterAbandonIsReleasedAndTheNextRequestLoadsAgain() {
        val loader = FakeLoader()
        val discarded = loader.discarded
        var abandonNext = true
        lateinit var c: AssetStorage<String, Res>
        c = AssetStorage(executor, FakeLoader(prepare = {
            if (abandonNext) c.abandon()
            abandonNext = false
            it
        }, discarded = discarded), log)
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
        val c = AssetStorage(
            executor,
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
        val c = AssetStorage(executor, loader, log)
        c.request("x")
        runBackground()
        c.pump()
        assertEquals(listOf("x"), discarded)
        assertFalse(c.isLoading())
        assertNull(c.get("x"))
    }

    // --- revisions: invalidate, replace, retry ---

    /** A cache whose prepared data carries a revision number, so a test can tell which revision became the asset. */
    private fun revisions(discarded: MutableList<String>, fail: Set<Int> = emptySet(), slices: Int = 1): Triple<AssetStorage<String, Res>, () -> Unit, MutableMap<String, Int>> {
        var revision = 0
        val current = mutableMapOf<String, Int>()
        val progress = mutableMapOf<String, Int>()
        val c = AssetStorage<String, Res>(
            executor,
            loader = FakeLoader(
                prepare = { n -> val r = ++revision; current[n] = r; if (r in fail) null else "$n#$r" },
                upload = { d -> val k = (progress[d] ?: 0) + 1; progress[d] = k; k >= slices },
                discarded = discarded,
            ),
            log = log,
        )
        return Triple(c, { runBackground() }, progress)
    }

    @Test
    fun aChangedAssetStaysInUseUntilItsReplacementIsBuilt() {
        val discarded = mutableListOf<String>()
        val (c, run) = revisions(discarded)
        c.request("a")
        run()
        c.pump()
        val first = c.get("a")!!
        assertEquals("a#1", first.name)
        assertEquals(1, c.version("a"))

        c.invalidate(setOf("a"))
        assertSame("the old asset keeps rendering while the new one loads", first, c.get("a"))
        assertTrue(c.isLoading())
        c.request("a")
        run()
        assertSame(first, c.get("a"))
        assertFalse(first.disposed)
        c.pump()
        val second = c.get("a")!!
        assertEquals("a#2", second.name)
        assertEquals(2, c.version("a"))
        assertTrue(first.disposed)
        assertFalse(second.disposed)
        assertFalse(c.isLoading())
        c.request("a")
        run()
        assertEquals("nothing reloads once current", 2, builds.size)
    }

    @Test
    fun invalidatingOneNameLeavesTheOthersAlone() {
        val c = cache()
        c.request("a")
        c.request("b")
        runBackground()
        c.pump(2)
        val a = c.get("a")!!
        val b = c.get("b")!!
        c.invalidate(setOf("a", "unknown"))
        c.request("a")
        c.request("b")
        runBackground()
        c.pump(2)
        assertTrue(a.disposed)
        assertSame(b, c.get("b"))
        assertFalse(b.disposed)
        assertEquals(1, c.version("b"))
        assertEquals(0, c.version("unknown"))
    }

    @Test
    fun anInvalidatedLoadingRequestIsDroppedAndNeverBuilt() {
        val discarded = mutableListOf<String>()
        val (c, run) = revisions(discarded)
        c.request("a")
        run() // revision 1 prepared, waiting for the GL thread
        c.invalidate(setOf("a"))
        c.request("a")
        run()
        c.pump(2)
        assertEquals(listOf("a#1"), discarded)
        assertEquals(listOf("a#2"), builds)
        assertEquals("a#2", c.get("a")!!.name)
    }

    @Test
    fun anInvalidatedRequestThatHasNotStartedIsNeverPrepared() {
        val c = cache()
        c.request("a")
        c.invalidate(setOf("a"))
        runBackground()
        assertTrue("only the dropped request ran", prepares.isEmpty())
        c.request("a")
        runBackground()
        c.pump()
        assertEquals(listOf("a"), prepares)
        assertEquals("a", c.get("a")!!.name)
    }

    @Test
    fun aReplacementPreparedWhileInvalidatedAgainIsDiscardedOnce() {
        val discarded = mutableListOf<String>()
        val (c, run) = revisions(discarded)
        c.request("a")
        run()
        c.pump()
        val first = c.get("a")!!
        repeat(3) {
            c.invalidate(setOf("a"))
            c.request("a")
            run()
        }
        c.pump(10)
        assertEquals("only the latest revision is built", listOf("a#1", "a#4"), builds)
        assertEquals(listOf("a#2", "a#3"), discarded)
        assertTrue(first.disposed)
        assertEquals("a#4", c.get("a")!!.name)
        assertFalse(c.get("a")!!.disposed)
        assertEquals(2, c.version("a"))
    }

    @Test
    fun anUploadInProgressForASupersededRevisionIsDiscardedOnceAndNeverBuilt() {
        val discarded = mutableListOf<String>()
        val (c, run, progress) = revisions(discarded, slices = 3)
        c.request("a")
        run()
        c.pump(1) // slice 1 of a#1
        assertEquals(mapOf("a#1" to 1), progress)
        c.invalidate(setOf("a"))
        c.request("a")
        run()
        c.pump(1) // a#1 is superseded: discarded without another slice; a#2 gets this step's slice
        assertEquals(listOf("a#1"), discarded)
        assertEquals(mapOf("a#1" to 1, "a#2" to 1), progress)
        assertTrue(builds.isEmpty())
        c.pump(1)
        c.pump(1)
        assertEquals(listOf("a#2"), builds)
        assertEquals(listOf("a#1"), discarded)
        assertEquals("a#2", c.get("a")!!.name)
    }

    @Test
    fun aFailedAssetIsRetriedAfterItChanges() {
        val discarded = mutableListOf<String>()
        val (c, run) = revisions(discarded, fail = setOf(1))
        c.request("a")
        run()
        c.pump()
        assertNull(c.get("a"))
        assertEquals(1, logged.size)
        c.request("a")
        run()
        assertEquals("not retried without a change", 1, logged.size)
        assertTrue(builds.isEmpty())

        c.invalidate(setOf("a"))
        c.request("a")
        run()
        c.pump()
        assertEquals("a#2", c.get("a")!!.name)
        assertEquals(1, logged.size)
    }

    @Test
    fun aBrokenRevisionRemovesTheOldAssetAndIsReportedOnce() {
        val discarded = mutableListOf<String>()
        val (c, run) = revisions(discarded, fail = setOf(2, 3))
        c.request("a")
        run()
        c.pump()
        val first = c.get("a")!!
        c.invalidate(setOf("a"))
        c.request("a")
        run()
        c.pump()
        assertNull(c.get("a"))
        assertTrue(first.disposed)
        assertEquals(1, logged.size)
        c.request("a")
        run()
        c.pump()
        assertEquals("one report per revision", 1, logged.size)
        c.invalidate(setOf("a"))
        c.request("a")
        run()
        c.pump()
        assertEquals(2, logged.size)
        assertNull(c.get("a"))
    }

    @Test
    fun retainingWithoutAReplacementPendingDisposesTheOldAndTheReplacementIsDropped() {
        val discarded = mutableListOf<String>()
        val (c, run) = revisions(discarded)
        c.request("a")
        run()
        c.pump()
        val first = c.get("a")!!
        c.invalidate(setOf("a"))
        c.request("a")
        run()
        c.retain(emptySet())
        assertTrue(first.disposed)
        c.pump()
        assertEquals(listOf("a#2"), discarded)
        assertEquals(listOf("a#1"), builds)
        assertNull(c.get("a"))
    }

    @Test
    fun disposeReleasesAPendingReplacementToo() {
        val discarded = mutableListOf<String>()
        val (c, run) = revisions(discarded)
        c.request("a")
        run()
        c.pump()
        val first = c.get("a")!!
        c.invalidate(setOf("a"))
        c.request("a")
        run()
        c.dispose()
        assertTrue(first.disposed)
        assertEquals(listOf("a#2"), discarded)
    }

    @Test
    fun storagesOfDifferentProjectsAreIsolated() {
        fun project(prefix: String) = AssetStorage(executor, FakeLoader(prepare = { "$prefix/$it" }), log)
        val a = project("one")
        val b = project("two")
        a.request("x")
        b.request("x")
        runBackground()
        a.pump()
        b.pump()
        val ax = a.get("x")!!
        val bx = b.get("x")!!
        assertEquals("one/x", ax.name)
        assertEquals("two/x", bx.name)

        a.invalidate(setOf("x"))
        a.request("x")
        runBackground()
        a.pump()
        assertTrue(ax.disposed)
        assertFalse(bx.disposed)
        assertSame(bx, b.get("x"))
        assertEquals(2, a.version("x"))
        assertEquals(1, b.version("x"))
    }
}
