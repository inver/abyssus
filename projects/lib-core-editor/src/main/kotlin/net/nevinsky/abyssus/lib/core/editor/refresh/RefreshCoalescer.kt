/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.refresh

/**
 * Decides when a view must rebuild, for change events that arrive in bursts from any thread. It holds no timer, thread or
 * UI type: [mark] says whether the caller must schedule a run, and the run brackets its rebuild with [begin] and [finish].
 *
 * - A burst of [mark]s before the run starts yields one run: only the first returns true.
 * - A mark that arrives while a rebuild runs is kept; [finish] then returns true and the caller schedules a follow-up run,
 *   so the view never settles on a state older than the last event.
 * - After [dispose] nothing is scheduled: [mark] returns false and [begin] returns null, so a pending run does nothing.
 */
class RefreshCoalescer {
    private val dirty = LinkedHashSet<String>()
    private var scheduled = false
    private var rebuilding = false
    private var disposed = false

    /** True when [key] changed and the caller must now schedule a run; false when a run is already pending, or after [dispose]. */
    @Synchronized
    fun mark(key: String): Boolean {
        if (disposed) return false
        dirty += key
        if (scheduled || rebuilding) return false
        scheduled = true
        return true
    }

    /** Starts the scheduled run: the keys that changed since the last run, or null when the run has nothing to do (disposed). */
    @Synchronized
    fun begin(): Set<String>? {
        scheduled = false
        if (disposed) return null
        rebuilding = true
        return LinkedHashSet(dirty).also { dirty.clear() }
    }

    /** Ends the run. True when events arrived during it: the caller must schedule another run. */
    @Synchronized
    fun finish(): Boolean {
        rebuilding = false
        if (disposed || dirty.isEmpty()) return false
        scheduled = true
        return true
    }

    /** True while a run is scheduled or rebuilding. */
    @get:Synchronized
    val pending: Boolean get() = scheduled || rebuilding

    /** Drops pending work and refuses more. */
    @Synchronized
    fun dispose() {
        disposed = true
        dirty.clear()
        scheduled = false
    }
}
