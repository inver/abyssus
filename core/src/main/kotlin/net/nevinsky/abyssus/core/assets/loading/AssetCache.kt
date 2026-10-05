/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assets.loading

import com.badlogic.gdx.utils.Disposable
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor

/**
 * Loads assets named by the scene in two steps: [prepare] runs on [executor] (file IO, parsing; no GL), [build] runs
 * on the GL thread from [pump] and creates the GPU resources, both through [loader]. Every name is loaded once and shared by all entities
 * using it; a name that fails is remembered as failed (and logged once) so it is not retried every frame.
 * Everything except [prepare] must be called on the GL thread.
 *
 * GPU work that is slow (uploading big textures) can be split: [AssetLoader.upload] is called with the prepared data
 * before [AssetLoader.build], once per [pump] step, and does one slice of it; the asset is built when it returns true.
 *
 * [invalidate] marks names as changed on disk. A loaded asset stays in use until its replacement is built, then the
 * two are swapped in one step, so a consumer never sees a half-replaced asset; a name that was loading or failed is
 * forgotten and loaded again by the next [request]. A load that finishes for a superseded request is discarded and
 * can never replace the latest one.
 */
class AssetCache<D : Any, T : Disposable>(
    private val executor: Executor,
    private val prepare: (String) -> D?,
    private val loader: AssetLoader<D, T>,
    private val log: Logger,
) : Disposable {
    private sealed interface State {
        /** One per request: a result is only taken while its request is still the current state of its name. */
        class Loading : State
        data object Failed : State

        /** A built asset; [pending] is the request that will replace it, [stale] that one must be started. */
        class Ready<T>(val value: T, var pending: Loading? = null, var stale: Boolean = false) : State
    }

    private class Prepared<D>(val name: String, val request: State.Loading, val data: D?, val error: Throwable?)

    private val states = HashMap<String, State>()

    /** How many assets were published under each name; changes exactly when [get] starts returning another asset. */
    private val versions = HashMap<String, Long>()
    private val prepared = ConcurrentLinkedQueue<Prepared<D>>()

    /** Requests still wanted; pool threads skip or drop the work of the others (forgotten, abandoned or disposed). */
    private val live: MutableSet<State.Loading> = ConcurrentHashMap.newKeySet()

    /** Prepared assets whose GPU work is under way (GL thread only). */
    private val waiting = ArrayDeque<Prepared<D>>()

    /** True while any requested asset has not finished loading (neither built nor failed). */
    fun isLoading(): Boolean = states.values.any { it is State.Loading || (it as? State.Ready<*>)?.let { r -> r.pending != null || r.stale } == true }

    /** The number of assets built under [name] so far: another value means [get] returns a new asset. */
    fun version(name: String): Long = versions[name] ?: 0L

    /**
     * Marks [names] as changed. A loaded asset is replaced once the next [request] has loaded and built its new
     * revision (the old one is disposed then); a loading request is dropped and a failed name forgotten, so the next
     * [request] loads it again from the current files. Unknown names and every other asset are untouched.
     */
    @Suppress("UNCHECKED_CAST")
    fun invalidate(names: Set<String>) {
        for (name in names) {
            when (val state = states[name] ?: continue) {
                is State.Loading -> {
                    live -= state
                    states.remove(name)
                }

                State.Failed -> states.remove(name)
                is State.Ready<*> -> {
                    state.pending?.let { live -= it }
                    state.pending = null
                    state.stale = true
                }
            }
        }
    }

    /** Starts loading [name] unless it is already loading, loaded and current, or failed. */
    fun request(name: String) {
        val existing = states[name]
        val request = State.Loading()
        when {
            existing == null -> states[name] = request
            existing is State.Ready<*> && existing.stale -> {
                existing.stale = false
                existing.pending = request
            }

            else -> return
        }
        live += request
        log.atDebug().log { "Loading asset '$name'" }
        executor.execute {
            if (request !in live) return@execute
            val started = System.nanoTime()
            val result: Prepared<D> = try {
                Prepared(name, request, prepare(name), null)
            } catch (e: Throwable) {
                Prepared(name, request, null, e)
            }
            log.atDebug().log { "Prepared asset '$name' in ${(System.nanoTime() - started) / 1_000_000} ms${if (result.data == null) " (nothing to build)" else ""}" }
            prepared.add(result)
            // dropped meanwhile: nothing on the GL thread may ever see it again, so release it here
            if (request !in live && prepared.remove(result)) result.data?.let(loader::discard)
        }
    }

    /**
     * Does up to [maxSteps] slices of GPU work: [AssetLoader.upload] for the asset that has waited longest, and its [AssetLoader.build] once
     * that has no work left. Returns true when at least one asset changed state.
     */
    fun pump(maxSteps: Int = 1): Boolean {
        var changed = false
        while (true) prepared.poll()?.let(waiting::addLast) ?: break
        var steps = 0
        while (steps < maxSteps) {
            val p = waiting.removeFirstOrNull() ?: break
            if (!isCurrent(p.name, p.request)) { // forgotten, or superseded by a newer revision, while loading
                p.data?.let(loader::discard)
                continue
            }
            val data = p.data
            if (data == null) {
                changed = true
                live -= p.request
                fail(p.name, p.error)
                continue
            }
            steps++
            try {
                if (!loader.upload(data)) {
                    waiting.addLast(p) // more to do next time; others get their turn first
                    continue
                }
                changed = true
                live -= p.request
                publish(p.name, loader.build(data))
            } catch (e: Throwable) {
                changed = true
                live -= p.request
                loader.discard(data)
                fail(p.name, e)
            }
        }
        return changed
    }

    @Suppress("UNCHECKED_CAST")
    private fun isCurrent(name: String, request: State.Loading): Boolean = when (val state = states[name]) {
        is State.Loading -> state === request
        is State.Ready<*> -> state.pending === request
        else -> false
    }

    /** Makes [value] the asset of [name] and disposes the one it replaces. */
    @Suppress("UNCHECKED_CAST")
    private fun publish(name: String, value: T) {
        val old = (states[name] as? State.Ready<T>)?.value
        states[name] = State.Ready(value)
        versions[name] = version(name) + 1
        old?.dispose()
        log.atDebug().log { "Asset '$name' is ready" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fail(name: String, error: Throwable?) {
        (states[name] as? State.Ready<T>)?.value?.dispose() // a revision that cannot load replaces the old one with nothing
        states[name] = State.Failed
        if (error != null) log.warn("Failed to load asset '$name'", error) else log.warn("Asset '$name' is missing or unreadable")
    }

    @Suppress("UNCHECKED_CAST")
    fun get(name: String): T? = (states[name] as? State.Ready<T>)?.value

    /** Disposes every asset not in [names] and forgets it (a later request loads it again). */
    @Suppress("UNCHECKED_CAST")
    fun retain(names: Set<String>) {
        val it = states.entries.iterator()
        while (it.hasNext()) {
            val (name, state) = it.next()
            if (name in names) continue
            it.remove()
            if (state is State.Loading) live -= state
            (state as? State.Ready<T>)?.let { ready ->
                ready.pending?.let { live -= it }
                ready.value.dispose()
            }
        }
    }

    /**
     * Forgets every asset without disposing it, so each is loaded again. For when the GL context the assets were
     * created in is gone (abandoned): its objects must not be used, and cannot be released either.
     */
    fun abandon() {
        states.clear()
        versions.clear()
        live.clear()
        discardPending()
    }

    private fun discardPending() {
        while (true) prepared.poll()?.data?.let(loader::discard) ?: break
        while (waiting.isNotEmpty()) waiting.removeFirst().data?.let(loader::discard)
    }

    override fun dispose() {
        retain(emptySet())
        live.clear()
        discardPending()
    }
}
