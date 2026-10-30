/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

/**
 * A resource made while one GL context was current. [generation] names that context: once it is abandoned or released the
 * handle is stale, and nothing may delete the underlying GL object through a later context.
 */
class Owned<T : Any> internal constructor(
    val value: T,
    val generation: Int,
    internal val release: (T) -> Unit,
    internal val abandon: (T) -> Unit,
)

/**
 * Owns the GL resources a renderer makes in one context, with the context's generation explicit. It does no GL itself:
 * the release and abandon functions it is given do.
 *
 * - [own] registers a resource in the current generation.
 * - [releaseAll] (valid context only) and [release] call the resources' release functions, newest first, and forget them. A
 *   handle of an older generation is never released: its context is gone.
 * - [abandonAll] (no GL, any time, including when a hidden canvas lost its context) calls each resource's abandon function
 *   (GL-free cleanup of CPU state), forgets them without releasing, and starts the next generation.
 *
 * Releasing and abandoning stay separate operations; this is not a service container and resolves nothing.
 * Failures in one release do not stop the others; the first is rethrown after the last, the rest added as suppressed.
 */
class RenderResources {
    private val owned = ArrayList<Owned<*>>()

    /** The generation new resources are made in; [abandonAll] and [releaseAll] move to the next. */
    var generation: Int = 0
        private set

    /** The number of resources this owner still holds. */
    val size: Int get() = owned.size

    /** Registers [value] in the current generation. [abandon] runs on [abandonAll] and must not touch GL. */
    fun <T : Any> own(value: T, release: (T) -> Unit, abandon: (T) -> Unit = {}): Owned<T> =
        Owned(value, generation, release, abandon).also { owned += it }

    /** Releases [handle] when it belongs to the current generation and is still held; false for a stale or unknown handle. */
    fun <T : Any> release(handle: Owned<T>): Boolean {
        if (handle.generation != generation || !owned.remove(handle)) return false
        handle.release(handle.value)
        return true
    }

    /** Releases every held resource of the current generation, newest first, then starts the next generation. */
    fun releaseAll() {
        val held = owned.toList().asReversed()
        owned.clear()
        generation++
        var failure: Throwable? = null
        for (handle in held) {
            try {
                @Suppress("UNCHECKED_CAST")
                (handle as Owned<Any>).release(handle.value)
            } catch (e: Exception) {
                if (failure == null) failure = e else failure.addSuppressed(e)
            }
        }
        failure?.let { throw it }
    }

    /** Forgets every held resource without releasing it, and starts the next generation. No GL. */
    fun abandonAll() {
        val held = owned.toList().asReversed()
        owned.clear()
        generation++
        for (handle in held) {
            @Suppress("UNCHECKED_CAST")
            (handle as Owned<Any>).abandon(handle.value)
        }
    }
}
