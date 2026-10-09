/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.lib.core.assets.loading.AssetState
import net.nevinsky.abyssus.lib.core.assets.loading.AssetStorage
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.Prepared
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.slf4j.Logger
import net.nevinsky.abyssus.lib.core.assets.loading.exception.DependencyFailedException
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.loading.exception.AssetAbsentException
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
        val needs: (String) -> Set<String> = { emptySet() },
        val onBuild: (String, BuiltAssets) -> Unit = { _, _ -> },
    ) : AssetLoader<Unit, String, Res> {
        /** Data a build took over: the storage frees staged data after building, which is not a discard. */
        private val builtData = mutableListOf<String>()

        override fun loadPrepared(meta: AssetMeta<Any>): Prepared<Unit, String>? = error("the storage prepares by name")
        override fun prepare(name: String): Prepared<Unit, String>? {
            prepares += name
            return prepare.invoke(name)?.let { Prepared(it) }
        }
        override fun upload(staged: String): Boolean = upload.invoke(staged)
        override fun dependencies(staged: String): Set<String> = needs.invoke(staged)

        override fun build(staged: String, assets: BuiltAssets): Res {
            builds += staged
            onBuild(staged, assets)
            return build.invoke(staged).also { builtData += staged }
        }

        override fun discard(model: Unit) = Unit

        override fun discardStaged(staged: String) {
            if (!builtData.remove(staged)) discarded += staged
        }
    }

    private fun cache(
        prepare: (String) -> String? = { it },
        loader: AssetLoader<Unit, String, Res> = FakeLoader(prepare),
    ) = AssetStorage(executor, loader, log)

    private fun runBackground() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }

    @Test
    fun progressIsLoggedAtDebugLevelAndNeverAsAWarning() {
        val c = cache()
        c.request("a")
        runBackground()
        c.update()
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
        assertNull(c.getAs<Res>("a"))
        assertTrue(c.update())
        val first = c.getAs<Res>("a")!!
        c.request("a")
        runBackground()
        assertSame(first, c.getAs<Res>("a"))
        assertEquals(listOf("a"), builds)
    }

    @Test
    fun buildsOnePerPump() {
        val c = cache()
        c.request("a")
        c.request("b")
        runBackground()
        c.update()
        assertEquals(1, builds.size)
        c.update()
        assertEquals(2, builds.size)
    }

    @Test
    fun failureIsRememberedAndNotRetried() {
        val c = cache(prepare = { null })
        c.request("bad")
        runBackground()
        c.update()
        c.request("bad")
        runBackground()
        assertEquals(1, prepares.size)
        assertNull(c.getAs<Res>("bad"))
        assertTrue(builds.isEmpty())
    }

    @Test
    fun aFailedAssetIsLoggedOnce() {
        val c = cache(prepare = { if (it == "bad") error("broken") else it })
        repeat(3) {
            c.request("bad")
            c.request("good")
            runBackground()
            c.update(maxSteps = 4)
        }
        assertEquals(listOf("Failed to load asset 'bad'"), logged)
        assertNull(c.getAs<Res>("bad"))
        assertEquals("good", c.getAs<Res>("good")!!.name)
    }

    @Test
    fun buildFailureDoesNotStopOthers() {
        val c = cache(loader = FakeLoader(build = { n -> if (n == "x") error("boom") else Res(n) }))
        c.request("x")
        c.request("y")
        runBackground()
        c.update(2)
        assertNull(c.getAs<Res>("x"))
        assertEquals("y", c.getAs<Res>("y")!!.name)
    }

    @Test
    fun retainDisposesUnusedAndAllowsReload() {
        val c = cache()
        c.request("a")
        c.request("b")
        runBackground()
        c.update(2)
        val a = c.getAs<Res>("a")!!
        val b = c.getAs<Res>("b")!!
        c.retain(setOf("b"))
        assertTrue(a.disposed)
        assertFalse(b.disposed)
        assertNull(c.getAs<Res>("a"))
        c.request("a")
        runBackground()
        c.update()
        assertEquals(3, prepares.size)
        assertFalse(c.getAs<Res>("a")!!.disposed)
    }

    @Test
    fun forgottenBeforeItStartsIsNeverPrepared() {
        val c = cache()
        c.request("a")
        c.retain(emptySet())
        runBackground()
        assertFalse(c.update())
        assertTrue(prepares.isEmpty())
        assertNull(c.getAs<Res>("a"))
    }

    @Test
    fun forgottenWhilePreparingIsDiscardedWithoutReachingTheGlThread() {
        val loader = FakeLoader()
        val discarded = loader.discarded
        lateinit var c: AssetStorage
        c = AssetStorage(executor, FakeLoader(prepare = { c.retain(emptySet()); it }, discarded = discarded), log)
        c.request("a")
        runBackground()
        assertEquals(listOf("a"), discarded)
        assertFalse(c.update())
        assertTrue(builds.isEmpty())
    }

    @Test
    fun aResultArrivingAfterDisposeIsReleased() {
        val loader = FakeLoader()
        val discarded = loader.discarded
        lateinit var c: AssetStorage
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
        lateinit var c: AssetStorage
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
        c.update()
        assertEquals("a", c.getAs<Res>("a")!!.name)
    }

    @Test
    fun disposeReleasesEverything() {
        val c = cache()
        c.request("a")
        runBackground()
        c.update()
        val a = c.getAs<Res>("a")!!
        c.dispose()
        assertTrue(a.disposed)
    }

    @Test
    fun abandonForgetsWithoutDisposingAndReloads() {
        val c = cache()
        c.request("a")
        runBackground()
        c.update()
        val old = c.getAs<Res>("a")!!
        c.abandon()
        assertFalse("objects of a lost GL context must not be touched", old.disposed)
        assertNull(c.getAs<Res>("a"))
        c.request("a")
        runBackground()
        c.update()
        assertEquals(2, builds.size)
        assertTrue(c.getAs<Res>("a") !== old)
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
        c.update(1)
        assertTrue(c.isLoading() || builds.size == 1)
        c.update(5)
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
        c.update(1) // big: slice 1
        c.update(1) // small: slice 1 (the other asset gets its turn)
        assertEquals(mapOf("big" to 1, "small" to 1), slices)
        assertTrue(c.isLoading())
        assertTrue(builds.isEmpty())
        c.update(1); c.update(1) // slice 2 each
        assertTrue(builds.isEmpty())
        c.update(1) // big done
        assertEquals(listOf("big"), builds)
        assertNull(c.getAs<Res>("small"))
        c.update(1)
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
        c.update()
        assertEquals(listOf("x"), discarded)
        assertFalse(c.isLoading())
        assertNull(c.getAs<Res>("x"))
    }

    // --- revisions: invalidate, replace, retry ---

    /** A cache whose prepared data carries a revision number, so a test can tell which revision became the asset. */
    private fun revisions(discarded: MutableList<String>, fail: Set<Int> = emptySet(), slices: Int = 1): Triple<AssetStorage, () -> Unit, MutableMap<String, Int>> {
        var revision = 0
        val current = mutableMapOf<String, Int>()
        val progress = mutableMapOf<String, Int>()
        val c = AssetStorage(
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
        c.update()
        val first = c.getAs<Res>("a")!!
        assertEquals("a#1", first.name)
        assertEquals(1, c.version("a"))

        c.invalidate(setOf("a"))
        assertSame("the old asset keeps rendering while the new one loads", first, c.getAs<Res>("a"))
        assertTrue(c.isLoading())
        c.request("a")
        run()
        assertSame(first, c.getAs<Res>("a"))
        assertFalse(first.disposed)
        c.update()
        val second = c.getAs<Res>("a")!!
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
        c.update(2)
        val a = c.getAs<Res>("a")!!
        val b = c.getAs<Res>("b")!!
        c.invalidate(setOf("a", "unknown"))
        c.request("a")
        c.request("b")
        runBackground()
        c.update(2)
        assertTrue(a.disposed)
        assertSame(b, c.getAs<Res>("b"))
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
        c.update(2)
        assertEquals(listOf("a#1"), discarded)
        assertEquals(listOf("a#2"), builds)
        assertEquals("a#2", c.getAs<Res>("a")!!.name)
    }

    @Test
    fun isLoadingByNameFollowsOneAssetFromRequestToBuiltAndToFailed() {
        val c = cache(prepare = { if (it == "bad") null else it })
        assertFalse("never requested", c.isLoading("a"))
        c.request("a"); c.request("bad")
        assertTrue(c.isLoading("a")); assertTrue(c.isLoading("bad"))
        runBackground()
        assertTrue("prepared, not yet built", c.isLoading("a"))
        c.update(2)
        assertFalse(c.isLoading("a")); assertFalse("failed is settled", c.isLoading("bad"))
        c.invalidate(setOf("a"))
        assertTrue("a loaded asset marked changed is being replaced", c.isLoading("a"))
    }

    // --- dependencies: an asset that needs others ---

    private fun withDependencies(
        needs: Map<String, Set<String>>,
        prepare: (String) -> String? = { it },
        onBuild: (String, BuiltAssets) -> Unit = { _, _ -> },
    ) = AssetStorage(executor, FakeLoader(prepare = prepare, needs = { needs[it].orEmpty() }, onBuild = onBuild), log)

    @Test
    fun anAssetRequestsItsDependenciesAndWaitsUntilTheyAreBuilt() {
        var seen: Res? = null
        val c = withDependencies(mapOf("terr" to setOf("tex"))) { name, assets -> if (name == "terr") seen = assets.get("tex") as Res }
        c.request("terr")
        runBackground()
        c.update()
        assertTrue("terr is prepared, not built", builds.isEmpty())
        assertTrue(c.isLoading("terr"))
        assertTrue("its dependency was requested", c.isLoading("tex"))
        runBackground()
        c.update(1) // terr is blocked and waits; tex gets the step
        assertEquals(listOf("tex"), builds)
        c.update(1)
        assertEquals(listOf("tex", "terr"), builds)
        assertSame("the build reads the built dependency", c.getAs<Res>("tex"), seen)
        assertFalse(c.isLoading())
    }

    @Test
    fun aDependencyThatFailsFailsTheAssetThatNeedsIt() {
        val c = withDependencies(mapOf("terr" to setOf("tex")), prepare = { if (it == "tex") null else it })
        c.request("terr")
        runBackground(); c.update()
        runBackground(); c.update(3)
        assertTrue(builds.isEmpty())
        assertNull(c.getAs<Res>("tex"))
        assertNull(c.getAs<Res>("terr"))
        val reason = (c.state("terr") as AssetState.Failed).reason as DependencyFailedException
        assertEquals("tex", reason.dependency)
        assertFalse(c.isLoading())
    }

    @Test
    fun anAssetWhoseDependencyNeverProgressesDoesNotSpinThePump() {
        val c = withDependencies(mapOf("terr" to setOf("tex")))
        c.request("terr")
        runBackground() // terr is prepared; tex is requested but its preparation never runs
        assertFalse(c.update(5))
        assertFalse(c.update(5))
        assertTrue(builds.isEmpty())
        assertTrue(c.isLoading("terr"))
    }

    /** Pumps until nothing is loading: enough rounds for every request, preparation and build to happen. */
    private fun settle(c: AssetStorage) {
        repeat(10) { runBackground(); c.update(3) }
    }

    @Test
    fun assetsThatDependOnEachOtherFailInsteadOfWaitingForever() {
        val c = withDependencies(mapOf("a" to setOf("b"), "b" to setOf("a")))
        c.request("a")
        settle(c)
        assertNull(c.getAs<Res>("a")); assertNull(c.getAs<Res>("b"))
        assertFalse("nothing is left loading", c.isLoading())
        assertTrue(builds.isEmpty())
        assertEquals(2, logged.size)
        assertTrue(logged.toString(), logged.all { it.startsWith("Failed to load asset") })
        val reasons = recorder.throwables.map { it.message }
        assertTrue(reasons.toString(), reasons.all { it == "Cyclic asset dependencies: a -> b" || it == "Cyclic asset dependencies: b -> a" })
    }

    @Test
    fun anAssetThatNeedsItselfFails() {
        val c = withDependencies(mapOf("a" to setOf("a")))
        c.request("a")
        settle(c)
        assertNull(c.getAs<Res>("a"))
        assertFalse(c.isLoading())
        assertEquals(listOf("Cyclic asset dependencies: a"), recorder.throwables.map { it.message })
    }

    @Test
    fun aLongerCycleFailsEveryMemberAndEverythingThatNeedsIt() {
        // a -> b -> c -> a is a cycle; d needs a and e needs d, and neither is part of it
        val c = withDependencies(mapOf("a" to setOf("b"), "b" to setOf("c"), "c" to setOf("a"), "d" to setOf("a"), "e" to setOf("d")))
        c.request("e"); c.request("a")
        settle(c)
        for (name in listOf("a", "b", "c")) assertNull(name, c.getAs<Res>(name))
        for (name in listOf("d", "e")) {
            assertNull(name, c.getAs<Res>(name))
            assertTrue(name, (c.state(name) as AssetState.Failed).reason is DependencyFailedException)
        }
        assertFalse(c.isLoading())
        assertTrue(builds.isEmpty())
    }

    @Test
    fun aDiamondOfDependenciesIsNotACycle() {
        val c = withDependencies(mapOf("a" to setOf("b", "c"), "b" to setOf("d"), "c" to setOf("d")))
        c.request("a")
        settle(c)
        assertEquals(setOf("a", "b", "c", "d"), builds.toSet())
        assertEquals("d is built once, before the others", "d", builds.first())
        assertEquals(emptyList<String>(), logged)
    }

    @Test
    fun aCycleIsFoundWhenItsLastMemberIsPreparedEvenIfTheOthersAreAlreadyBlocked() {
        val c = withDependencies(mapOf("a" to setOf("b"), "b" to setOf("a")))
        c.request("a")
        runBackground(); c.update(2) // a is prepared and blocked on b, which is requested but not prepared yet
        assertTrue(c.isLoading("a")); assertEquals(emptyList<String>(), logged)
        runBackground(); c.update(2) // b is prepared: it closes the cycle
        assertFalse(c.isLoading("a")); assertFalse(c.isLoading("b"))
        assertEquals(2, logged.size)
    }

    @Test
    fun retainKeepsWhatARetainedAssetNeeds() {
        val c = withDependencies(mapOf("terr" to setOf("tex")))
        c.request("terr"); runBackground(); c.update(); runBackground(); c.update(3)
        val tex = c.getAs<Res>("tex")!!
        c.retain(setOf("terr"))
        assertFalse("needed by a retained asset", tex.disposed)
        assertSame(tex, c.getAs<Res>("tex"))
        c.retain(emptySet())
        assertTrue(tex.disposed)
        assertNull(c.getAs<Res>("terr"))
    }

    @Test
    fun getAsReadsABuiltAssetAsItsKind() {
        val c = cache()
        c.request("a"); runBackground(); c.update()
        assertEquals("a", c.getAs<Res>("a")!!.name)
        assertNull(c.getAs<String>("a"))
        assertNull(c.getAs<Res>("missing"))
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
        c.update()
        assertEquals(listOf("a"), prepares)
        assertEquals("a", c.getAs<Res>("a")!!.name)
    }

    @Test
    fun aReplacementPreparedWhileInvalidatedAgainIsDiscardedOnce() {
        val discarded = mutableListOf<String>()
        val (c, run) = revisions(discarded)
        c.request("a")
        run()
        c.update()
        val first = c.getAs<Res>("a")!!
        repeat(3) {
            c.invalidate(setOf("a"))
            c.request("a")
            run()
        }
        c.update(10)
        assertEquals("only the latest revision is built", listOf("a#1", "a#4"), builds)
        assertEquals(listOf("a#2", "a#3"), discarded)
        assertTrue(first.disposed)
        assertEquals("a#4", c.getAs<Res>("a")!!.name)
        assertFalse(c.getAs<Res>("a")!!.disposed)
        assertEquals(2, c.version("a"))
    }

    @Test
    fun anUploadInProgressForASupersededRevisionIsDiscardedOnceAndNeverBuilt() {
        val discarded = mutableListOf<String>()
        val (c, run, progress) = revisions(discarded, slices = 3)
        c.request("a")
        run()
        c.update(1) // slice 1 of a#1
        assertEquals(mapOf("a#1" to 1), progress)
        c.invalidate(setOf("a"))
        c.request("a")
        run()
        c.update(1) // a#1 is superseded: discarded without another slice; a#2 gets this step's slice
        assertEquals(listOf("a#1"), discarded)
        assertEquals(mapOf("a#1" to 1, "a#2" to 1), progress)
        assertTrue(builds.isEmpty())
        c.update(1)
        c.update(1)
        assertEquals(listOf("a#2"), builds)
        assertEquals(listOf("a#1"), discarded)
        assertEquals("a#2", c.getAs<Res>("a")!!.name)
    }

    @Test
    fun aFailedAssetIsRetriedAfterItChanges() {
        val discarded = mutableListOf<String>()
        val (c, run) = revisions(discarded, fail = setOf(1))
        c.request("a")
        run()
        c.update()
        assertNull(c.getAs<Res>("a"))
        assertEquals(1, logged.size)
        c.request("a")
        run()
        assertEquals("not retried without a change", 1, logged.size)
        assertTrue(builds.isEmpty())

        c.invalidate(setOf("a"))
        c.request("a")
        run()
        c.update()
        assertEquals("a#2", c.getAs<Res>("a")!!.name)
        assertEquals(1, logged.size)
    }

    @Test
    fun aBrokenRevisionRemovesTheOldAssetAndIsReportedOnce() {
        val discarded = mutableListOf<String>()
        val (c, run) = revisions(discarded, fail = setOf(2, 3))
        c.request("a")
        run()
        c.update()
        val first = c.getAs<Res>("a")!!
        c.invalidate(setOf("a"))
        c.request("a")
        run()
        c.update()
        assertNull(c.getAs<Res>("a"))
        assertTrue(first.disposed)
        assertEquals(1, logged.size)
        c.request("a")
        run()
        c.update()
        assertEquals("one report per revision", 1, logged.size)
        c.invalidate(setOf("a"))
        c.request("a")
        run()
        c.update()
        assertEquals(2, logged.size)
        assertNull(c.getAs<Res>("a"))
    }

    @Test
    fun retainingWithoutAReplacementPendingDisposesTheOldAndTheReplacementIsDropped() {
        val discarded = mutableListOf<String>()
        val (c, run) = revisions(discarded)
        c.request("a")
        run()
        c.update()
        val first = c.getAs<Res>("a")!!
        c.invalidate(setOf("a"))
        c.request("a")
        run()
        c.retain(emptySet())
        assertTrue(first.disposed)
        c.update()
        assertEquals(listOf("a#2"), discarded)
        assertEquals(listOf("a#1"), builds)
        assertNull(c.getAs<Res>("a"))
    }

    @Test
    fun disposeReleasesAPendingReplacementToo() {
        val discarded = mutableListOf<String>()
        val (c, run) = revisions(discarded)
        c.request("a")
        run()
        c.update()
        val first = c.getAs<Res>("a")!!
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
        a.update()
        b.update()
        val ax = a.getAs<Res>("x")!!
        val bx = b.getAs<Res>("x")!!
        assertEquals("one/x", ax.name)
        assertEquals("two/x", bx.name)

        a.invalidate(setOf("x"))
        a.request("x")
        runBackground()
        a.update()
        assertTrue(ax.disposed)
        assertFalse(bx.disposed)
        assertSame(bx, b.getAs<Res>("x"))
        assertEquals(2, a.version("x"))
        assertEquals(1, b.version("x"))
    }

    /** A loader that prepares from the meta the storage read, tagging the result with [kind]. */
    private inner class KindLoader(val kind: String, val needs: Set<String> = emptySet()) :
        AssetLoader<Unit, String, Res> {
        override fun loadPrepared(meta: AssetMeta<Any>): Prepared<Unit, String> = Prepared("$kind:${meta.name}")
        override fun prepare(name: String): Prepared<Unit, String>? = error("a typed storage prepares from the meta")
        override fun dependencies(staged: String): Set<String> = needs
        override fun build(staged: String, assets: BuiltAssets): Res = Res(staged)
        override fun discard(model: Unit) = Unit
    }

    @Test
    fun theMetaTypePicksTheLoaderAndWhatNothingCanLoadFailsAsAbsent() {
        val types = mapOf("m" to MetaType.MODEL, "t" to MetaType.TERRAIN, "tex" to MetaType.TEXTURE, "sky" to MetaType.SKYBOX)
        val c = AssetStorage(log, executor) { name ->
            types[name]?.let {
                AssetMeta<Any>(
                    name = name,
                    type = it,
                    additional = Unit
                )
            }
        }
        c.register(KindLoader("model"), MetaType.MODEL)
        c.register(KindLoader("terrain", setOf("tex")), MetaType.TERRAIN)
        c.register(KindLoader("texture"), MetaType.TEXTURE, MetaType.PIXMAP_TEXTURE)
        for (name in listOf("m", "t", "sky", "nope")) c.request(name)
        settle(c)
        assertEquals("model:m", c.getAs<Res>("m")!!.name)
        assertEquals("terrain:t", c.getAs<Res>("t")!!.name)
        assertEquals("the terrain's texture is routed by its own meta", "texture:tex", c.getAs<Res>("tex")!!.name)
        for (name in listOf("sky", "nope")) {
            assertNull(name, c.getAs<Res>(name))
            assertTrue(name, (c.state(name) as AssetState.Failed).reason is AssetAbsentException)
        }
        assertFalse(c.isLoading())
    }
}
