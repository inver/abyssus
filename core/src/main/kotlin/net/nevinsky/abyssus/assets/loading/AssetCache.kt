/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.assets.AssetLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor

/**
 * Loads assets named by the scene in two steps: [prepare] runs on [executor] (file IO, parsing; no GL), [build] runs
 * on the GL thread from [pump] and creates the GPU resources. Every name is loaded once and shared by all entities
 * using it; a name that fails is remembered as failed (and logged once) so it is not retried every frame.
 * Everything except [prepare] must be called on the GL thread.
 *
 * GPU work that is slow (uploading big textures) can be split: [advance] is called with the prepared data before
 * [build], once per [pump] step, and does one slice of it; the asset is built when it returns true.
 */
class AssetCache<D : Any, T : Disposable>(
    private val executor: Executor,
    private val prepare: (String) -> D?,
    private val build: (String, D) -> T?,
    private val advance: (D) -> Boolean = { true },
    private val discard: (D) -> Unit = {},
    private val log: AssetLog,
) : Disposable {
    private sealed interface State {
        /** One per request: a result is only taken while its request is still the current state of its name. */
        class Loading : State
        data object Failed : State
        class Ready<T>(val value: T) : State
    }

    private class Prepared<D>(val name: String, val request: State.Loading, val data: D?, val error: Throwable?)

    private val states = HashMap<String, State>()
    private val prepared = ConcurrentLinkedQueue<Prepared<D>>()

    /** Requests still wanted; pool threads skip or drop the work of the others (forgotten, abandoned or disposed). */
    private val live: MutableSet<State.Loading> = ConcurrentHashMap.newKeySet()

    /** Prepared assets whose GPU work is under way (GL thread only). */
    private val waiting = ArrayDeque<Prepared<D>>()

    /** True while any requested asset has not finished loading (neither built nor failed). */
    fun isLoading(): Boolean = states.values.any { it is State.Loading }

    /** Starts loading [name] unless it is already loading, loaded or failed. */
    fun request(name: String) {
        if (states.containsKey(name)) return
        val request = State.Loading()
        states[name] = request
        live += request
        executor.execute {
            if (request !in live) return@execute
            val result: Prepared<D> = try {
                Prepared(name, request, prepare(name), null)
            } catch (e: Throwable) {
                Prepared(name, request, null, e)
            }
            prepared.add(result)
            // dropped meanwhile: nothing on the GL thread may ever see it again, so release it here
            if (request !in live && prepared.remove(result)) result.data?.let(discard)
        }
    }

    /**
     * Does up to [maxSteps] slices of GPU work: [advance] for the asset that has waited longest, and its [build] once
     * that has no work left. Returns true when at least one asset changed state.
     */
    fun pump(maxSteps: Int = 1): Boolean {
        var changed = false
        while (true) prepared.poll()?.let(waiting::addLast) ?: break
        var steps = 0
        while (steps < maxSteps) {
            val p = waiting.removeFirstOrNull() ?: break
            if (states[p.name] !== p.request) { // forgotten while loading
                p.data?.let(discard)
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
                if (!advance(data)) {
                    waiting.addLast(p) // more to do next time; others get their turn first
                    continue
                }
                changed = true
                live -= p.request
                val value = build(p.name, data)
                if (value == null) fail(p.name, null) else states[p.name] = State.Ready(value)
            } catch (e: Throwable) {
                changed = true
                live -= p.request
                data.let(discard)
                fail(p.name, e)
            }
        }
        return changed
    }

    private fun fail(name: String, error: Throwable?) {
        states[name] = State.Failed
        if (error != null) log.warn("Failed to load asset '$name'", error) else log.warn("Asset '$name' is missing or unreadable", null)
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
            (state as? State.Ready<T>)?.value?.dispose()
        }
    }

    /**
     * Forgets every asset without disposing it, so each is loaded again. For when the GL context the assets were
     * created in is gone (abandoned): its objects must not be used, and cannot be released either.
     */
    fun abandon() {
        states.clear()
        live.clear()
        discardPending()
    }

    private fun discardPending() {
        while (true) prepared.poll()?.data?.let(discard) ?: break
        while (waiting.isNotEmpty()) waiting.removeFirst().data?.let(discard)
    }

    override fun dispose() {
        retain(emptySet())
        live.clear()
        discardPending()
    }
}
