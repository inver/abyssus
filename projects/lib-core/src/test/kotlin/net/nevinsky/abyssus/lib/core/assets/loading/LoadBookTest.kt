/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.loading.exception.DependencyFailedException
import net.nevinsky.abyssus.lib.core.assets.loading.exception.MissingDependencyException
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

/** The revision and dependency bookkeeping with fake loads: no GL, no loader, no executor. */
class LoadBookTest {
    private class Fake(
        override val id: Long,
        override val name: String,
        override var phase: Phase,
        override var dependencies: Set<String>? = null,
        override var error: Throwable? = null,
        override var stale: Boolean = false,
    ) : LoadView

    private val book = LoadBook<Fake>()
    private var ids = 0L

    private fun live(name: String, phase: Phase, deps: Set<String>? = null, stale: Boolean = false) =
        Fake(++ids, name, phase, deps, stale = stale).also { book.lives[name] = it }

    private fun revision(name: String, phase: Phase = Phase.Preparing) =
        Fake(++ids, name, phase).also { book.revisions[name] = it }

    @Test
    fun aMessageIsOwnedOnlyByTheGenerationItWasRequestedFor() {
        val current = live("a", Phase.Ready)
        val next = revision("a")
        assertSame(next, book.owner("a", next.id))
        assertSame(current, book.owner("a", current.id))
        assertNull("a generation nobody holds", book.owner("a", next.id + 100))
        book.revisions.remove("a")
        assertNull("the dropped replacement no longer owns its message", book.owner("a", next.id))
    }

    @Test
    fun theReplacementIsTheEntryAndBothAreActive() {
        val current = live("a", Phase.Ready)
        val next = revision("a")
        assertSame(next, book.entry("a"))
        assertEquals(listOf(current, next), book.active())
    }

    @Test
    fun aDependencyIsSatisfiedOnlyWhenBuiltCurrentAndWithoutAReplacementLoading() {
        val user = live("user", Phase.Waiting, setOf("dep"))
        assertFalse("missing", book.depsSatisfied(user))
        val dep = live("dep", Phase.Uploading)
        assertFalse("not built", book.depsSatisfied(user))
        dep.phase = Phase.Ready
        assertTrue(book.depsSatisfied(user))
        dep.stale = true
        assertFalse("marked changed", book.depsSatisfied(user))
        dep.stale = false
        revision("dep")
        assertFalse("a replacement is loading", book.depsSatisfied(user))
        assertEquals("a built, current dependency is not unmet", setOf("other"), book.unmet(setOf("dep", "other")))
    }

    @Test
    fun aFailedOrMissingDependencyIsAProblemOfTheDependent() {
        val user = live("user", Phase.Waiting, setOf("gone"))
        assertTrue(book.dependencyProblem(user) is MissingDependencyException)
        val failure = IllegalStateException("boom")
        live("gone", Phase.Failed).error = failure
        val problem = book.dependencyProblem(user)
        assertTrue(problem is DependencyFailedException)
        assertSame(failure, problem!!.cause)
        book.lives["gone"]!!.phase = Phase.Uploading
        assertNull("still loading is no problem", book.dependencyProblem(user))
    }

    @Test
    fun loadsWaitingForEachOtherAreACycleAndBuiltOnesAreNot() {
        live("a", Phase.Waiting, setOf("b"))
        live("b", Phase.Waiting, setOf("c"))
        live("c", Phase.Waiting, setOf("a"))
        live("d", Phase.Waiting, setOf("e"))
        live("e", Phase.Ready)
        assertEquals(setOf("a", "b", "c"), book.cyclicNames())
        book.lives["c"]!!.phase = Phase.Ready
        assertTrue(book.cyclicNames().isEmpty())
    }

    @Test
    fun retainKeepsWhatTheKeptAssetsNeedTransitively() {
        live("scene", Phase.Ready, setOf("terrain"))
        live("terrain", Phase.Ready, setOf("splat"))
        live("splat", Phase.Ready, emptySet())
        live("unrelated", Phase.Ready, emptySet())
        assertEquals(setOf("scene", "terrain", "splat"), book.withDependencies(setOf("scene")))
    }

    @Test
    fun phasesDecideWhetherALoadIsLoadingOrBlocked() {
        assertFalse(book.loading(Fake(1, "f", Phase.Failed)))
        assertFalse(book.loading(Fake(2, "r", Phase.Ready)))
        assertTrue("a ready asset marked changed is about to reload", book.loading(Fake(3, "s", Phase.Ready, stale = true)))
        assertTrue(book.loading(Fake(4, "p", Phase.Preparing)))
        assertTrue(book.blocksOnDependencies(Fake(5, "w", Phase.UploadDone)))
        assertFalse(book.blocksOnDependencies(Fake(6, "p", Phase.Preparing)))
    }
}

/** Logging stays once per load, and generations stay ordered, through the real storage with fake resources. */
class AssetStorageRevisionLoggingTest {
    private class Res : Disposable {
        override fun dispose() = Unit
    }

    private val queue = ArrayDeque<Runnable>()
    private val executor = Executor { queue += it }
    private val recorder = RecordingLogger()
    private var fail = true

    private val loader = object : AssetLoader<Unit, String, Res> {
        override fun loadPrepared(meta: AssetMeta<Any>): Prepared<Unit, String>? = null
        override fun prepare(name: String): Prepared<Unit, String>? =
            if (name == "needs-bad") Prepared("needs-bad") else if (fail) null else Prepared(name)
        override fun dependencies(staged: String): Set<String> = if (staged == "needs-bad") setOf("bad") else emptySet()
        override fun build(staged: String, assets: BuiltAssets): Res = Res()
        override fun discard(model: Unit) = Unit
    }

    private fun pump(storage: AssetStorage) {
        repeat(5) {
            while (queue.isNotEmpty()) queue.removeFirst().run()
            storage.update()
        }
    }

    @Test
    fun aFailureIsLoggedOncePerLoadNotPerFrameOrDependent() {
        val storage = AssetStorage(executor, loader, recorder)
        storage.request("bad")
        storage.request("needs-bad")
        pump(storage)
        pump(storage)
        assertEquals("bad and needs-bad: one line each", 2, recorder.warnings.size)
        fail = false
        storage.invalidate(setOf("bad"))
        storage.request("bad")
        pump(storage)
        assertTrue(storage.isReady("bad"))
        assertEquals("a successful reload adds no line", 2, recorder.warnings.size)
    }

    @Test
    fun aSupersededLoadNeverReplacesTheLatest() {
        fail = false
        val storage = AssetStorage(executor, loader, recorder)
        storage.request("a")
        pump(storage)
        val first = storage.get("a")
        storage.invalidate(setOf("a"))
        storage.request("a")
        storage.invalidate(setOf("a"))
        storage.request("a")
        pump(storage)
        assertTrue(storage.isReady("a"))
        assertFalse("the built asset was replaced exactly by the latest", first === storage.get("a"))
        assertEquals(2L, storage.version("a"))
    }
}
