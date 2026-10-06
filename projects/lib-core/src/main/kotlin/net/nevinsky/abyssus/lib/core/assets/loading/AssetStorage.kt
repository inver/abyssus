/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor

/**
 * Loads assets named by the scene in two steps: [AssetLoader.prepare] runs on [executor] (file IO, parsing; no GL),
 * [AssetLoader.build] runs on the GL thread from [pump] and creates the GPU resources. Every name is loaded once and shared by all entities
 * using it; a name that fails is remembered as failed (and logged once) so it is not retried every frame.
 * Everything except [AssetLoader.prepare] must be called on the GL thread.
 *
 * GPU work that is slow (uploading big textures) can be split: [AssetLoader.upload] is called with the prepared data
 * before [AssetLoader.build], once per [pump] step, and does one slice of it; the asset is built when it returns true.
 *
 * An asset may need others (a terrain's splat textures): [AssetLoader.dependencies] names them, the storage requests
 * them when the asset has been prepared and holds its upload and build until each is built or has failed, and
 * [retain] keeps what a retained asset needs. A loader reads a dependency at use time through the [BuiltAssets] it is
 * handed, never by calling another loader. Assets that depend on each other in a cycle cannot be built, so each member
 * fails (with a message naming the cycle) as soon as the last of them has been prepared, and is absent for whatever
 * needs it.
 *
 * [invalidate] marks names as changed on disk. A loaded asset stays in use until its replacement is built, then the
 * two are swapped in one step, so a consumer never sees a half-replaced asset; a name that was loading or failed is
 * forgotten and loaded again by the next [request]. A load that finishes for a superseded request is discarded and
 * can never replace the latest one.
 */
class AssetStorage<D : Any, T : Disposable>(
    private val executor: Executor,
    private val loader: AssetLoader<D, T>,
    private val log: Logger,
) : Disposable, BuiltAssets {
    private sealed interface State {
        /** One load request; its identity (not its value) tells a superseded request from the current one. */
        class Loading : State
        data object Failed : State

        /** A built asset; [pending] is the request that will replace it, [stale] that one must be started. */
        class Ready<T>(val value: T, var pending: Loading? = null, var stale: Boolean = false) : State
    }

    private class Prepared<D>(val name: String, val request: State.Loading, val data: D?, val error: Throwable?) {
        /** The names [data] needs, once asked for (and requested) on the GL thread. */
        var dependencies: Set<String>? = null
    }

    private val states = HashMap<String, State>()

    /** How many assets were published under each name; changes exactly when [get] starts returning another asset. */
    private val versions = HashMap<String, Long>()

    /** What each built asset needed when it was built. */
    private val requirements = HashMap<String, Set<String>>()
    private val prepared = ConcurrentLinkedQueue<Prepared<D>>()

    /** Requests still wanted; pool threads skip or drop the work of the others (forgotten, abandoned or disposed). */
    private val live: MutableSet<State.Loading> = ConcurrentHashMap.newKeySet()

    /** Prepared assets whose GPU work is under way (GL thread only). */
    private val waiting = ArrayDeque<Prepared<D>>()

    /** True while any requested asset has not finished loading (neither built nor failed). */
    fun isLoading(): Boolean =
        states.values.any { it is State.Loading || (it as? State.Ready<*>)?.let { r -> r.pending != null || r.stale } == true }

    /** True while [name] is requested but neither built (and current) nor failed; false for a name never requested. */
    fun isLoading(name: String): Boolean = when (val state = states[name]) {
        is State.Loading -> true
        is State.Ready<*> -> state.pending != null || state.stale
        else -> false
    }

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
        when (existing) {
            null -> states[name] = request
            is State.Ready<*> if existing.stale -> {
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
                Prepared(name, request, loader.prepare(name), null)
            } catch (e: Throwable) {
                Prepared(name, request, null, e)
            }
            log.atDebug()
                .log { "Prepared asset '$name' in ${(System.nanoTime() - started) / 1_000_000} ms${if (result.data == null) " (nothing to build)" else ""}" }
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
        var blocked = 0
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
            try {
                val needs = p.dependencies ?: loader.dependencies(data).also { needs ->
                    findCycle(p.name, needs)?.let { breakCycle(p.name, it) } // throws: this asset fails too
                    p.dependencies = needs
                    needs.forEach(::request)
                }
                if (needs.any(::isLoading)) { // a dependency is still being loaded: wait, without using a step
                    waiting.addLast(p)
                    if (++blocked > waiting.size) break // everything waiting is blocked: nothing can progress now
                    continue
                }
                blocked = 0
                steps++
                if (!loader.upload(data)) {
                    waiting.addLast(p) // more to do next time; others get their turn first
                    continue
                }
                changed = true
                live -= p.request
                publish(p.name, loader.build(data, this), needs)
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
    private fun publish(name: String, value: T, needs: Set<String>) {
        val old = (states[name] as? State.Ready<T>)?.value
        states[name] = State.Ready(value)
        requirements[name] = needs
        versions[name] = version(name) + 1
        old?.dispose()
        log.atDebug().log { "Asset '$name' is ready" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fail(name: String, error: Throwable?) {
        (states[name] as? State.Ready<T>)?.value?.dispose() // a revision that cannot load replaces the old one with nothing
        states[name] = State.Failed
        requirements.remove(name)
        if (error != null) log.warn(
            "Failed to load asset '$name'",
            error
        ) else log.warn("Asset '$name' is missing or unreadable")
    }

    @Suppress("UNCHECKED_CAST")
    override fun get(name: String): T? = (states[name] as? State.Ready<T>)?.value

    /** The built asset of [name] as an [R]; null when it is not built or is of another kind. */
    inline fun <reified R : Any> getAs(name: String): R? = get(name) as? R

    /** Disposes every asset neither in [names] nor needed by one that is, and forgets it (a later request loads it again). */
    @Suppress("UNCHECKED_CAST")
    fun retain(names: Set<String>) {
        val keep = withDependencies(names)
        val it = states.entries.iterator()
        while (it.hasNext()) {
            val (name, state) = it.next()
            if (name in keep) continue
            it.remove()
            requirements.remove(name)
            if (state is State.Loading) live -= state
            (state as? State.Ready<T>)?.let { ready ->
                ready.pending?.let { live -= it }
                ready.value.dispose()
            }
        }
    }

    /**
     * The assets that would wait on each other forever if [name] waited for [needs]: a path of names from [name] back to
     * itself through assets that are prepared and blocked on their dependencies, or null when [name] closes no cycle.
     * A cycle not through [name] was found when its last member asked for its dependencies.
     */
    private fun findCycle(name: String, needs: Set<String>): List<String>? {
        val blocked = HashMap<String, Set<String>>()
        for (w in waiting) w.dependencies?.takeIf { isCurrent(w.name, w.request) }?.let { blocked[w.name] = it }
        blocked[name] = needs
        val visited = HashSet<String>()
        fun walk(node: String, path: List<String>): List<String>? {
            for (next in blocked[node].orEmpty()) {
                if (next == name) return path
                if (next in blocked && visited.add(next)) walk(next, path + next)?.let { return it }
            }
            return null
        }
        return walk(name, listOf(name))
    }

    /**
     * Fails every asset of [cycle] but the one currently being pumped, which the caller fails by the exception this
     * throws: none of them can be built before the others, so each is left unbuilt (and absent for whatever needs it)
     * instead of everything waiting forever.
     */
    private fun breakCycle(current: String, cycle: List<String>): Nothing {
        val error = IllegalStateException("dependency cycle: ${(cycle + cycle.first()).joinToString(" -> ")}")
        for (member in cycle) {
            if (member == current) continue
            val item = waiting.firstOrNull { it.name == member && isCurrent(it.name, it.request) } ?: continue
            waiting.remove(item)
            live -= item.request
            item.data?.let(loader::discard)
            fail(member, error)
        }
        throw error
    }

    /** [names] and, transitively, what the built or waiting assets among them need. */
    private fun withDependencies(names: Set<String>): Set<String> {
        val keep = HashSet(names)
        val waitingNeeds = waiting.mapNotNull { p -> p.dependencies?.let { p.name to it } }.toMap()
        var frontier: Collection<String> = names
        while (frontier.isNotEmpty()) {
            frontier = frontier.flatMap { (requirements[it] ?: waitingNeeds[it]).orEmpty() }.filter(keep::add)
        }
        return keep
    }

    /**
     * Forgets every asset without disposing it, so each is loaded again. For when the GL context the assets were
     * created in is gone (abandoned): its objects must not be used, and cannot be released either.
     */
    fun abandon() {
        states.clear()
        versions.clear()
        requirements.clear()
        live.clear()
        discardPending()
    }

    private fun discardPending() {
        while (true) {
            prepared.poll()?.data?.let(loader::discard) ?: break
        }
        while (waiting.isNotEmpty()) {
            waiting.removeFirst().data?.let(loader::discard)
        }
    }

    override fun dispose() {
        retain(emptySet())
        live.clear()
        discardPending()
    }
}

/** The built assets of a storage, for a loader to read the ones its asset needs. GL thread only. */
fun interface BuiltAssets {
    /** The built asset of [name]; null when it is not built (or has failed). */
    fun get(name: String): Disposable?
}

/**
 * Turns one asset folder into a GPU object, in the steps [AssetStorage] runs: [prepare] off the GL thread (file IO,
 * decoding), then [upload] one slice per frame and [build] on the GL thread. A loader never calls another loader: what
 * an asset needs from another asset it names in [dependencies] and reads from the [BuiltAssets] given to [build].
 */
interface AssetLoader<P : Any, T : Disposable> {

    fun loadPrepared(meta: AssetMeta<Any>): P?

    /** Reads and decodes the asset [name]; null when it has no usable files. No GL; runs on a pool thread. */
    fun prepare(name: String): P?

    /** Does one slice of the GPU upload; true when nothing is left. */
    fun upload(prepared: P): Boolean = true

    /** The names of the assets [prepared] needs built before it is uploaded. Asked on the GL thread, once. */
    fun dependencies(prepared: P): Set<String> = emptySet()

    /** Creates the GPU object; [assets] holds the built assets of [dependencies] (and any other the storage has). */
    fun build(prepared: P, assets: BuiltAssets): T

    /** Releases whatever [prepared] still holds. Safe to call more than once. */
    fun discard(prepared: P)
}
