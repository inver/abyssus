/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.ray.RayModePhase

import net.nevinsky.abyssus.raytracing.*
import java.util.concurrent.atomic.AtomicBoolean

internal class RayViewCompleted<T>(private val completed: RayRenderCompleted, val metadata: T) {
    val frame get() = completed.frame
    val batch get() = completed.batch
    val offeredNanos get() = completed.offeredNanos
}

/**
 * A view's CPU boundary. Control changes, offers and reads never call native code or await the worker. The metadata
 * value must be frozen by its caller; it travels with the submitted input so older motion frames retain their camera.
 */
internal class RayViewRuntime<T> internal constructor(
    private val service: RayBackendService,
    internal val viewId: String,
    internal val limits: RayLimits,
    internal val qualityLimits: RayQualityLimits,
) : AutoCloseable {
    val mode = RayModeState()
    private val lock = Any()
    private val closed = AtomicBoolean()
    @Volatile private var visible = true
    private var scheduler: RayRenderScheduler? = null
    // At startup there is no native session yet. Keep one replaceable input until the scheduler can accept it.
    private var preparingInput: RayRenderInput? = null

    fun setRequested(enabled: Boolean) {
        if (closed.get()) return
        if (!enabled) {
            mode.off()
            clearPublication()
            service.deactivate(this)
        } else if (!mode.requested && mode.toggleEnabled && mode.phase != RayModePhase.Failed) {
            val revision = mode.request()
            if (visible) service.activate(this, revision, retry = false)
        }
    }

    fun retry() {
        if (closed.get()) return
        val revision = mode.retry() ?: return
        clearPublication()
        if (visible) service.activate(this, revision, retry = true)
    }

    /** Scene conversion can request whole-view fallback before any native submission is made. */
    fun fail(detail: String?) {
        if (closed.get() || !mode.requested) return
        service.noteFallback(this, detail)
        mode.failed(mode.snapshot.revision, detail)
        clearPublication()
        service.deactivate(this)
    }

    fun setVisible(value: Boolean) {
        if (closed.get() || visible == value) return
        visible = value
        if (!value) {
            mode.rechecking()
            clearPublication()
            service.deactivate(this)
        } else if (mode.requested) service.activate(this, mode.snapshot.revision, retry = false)
    }

    fun offer(input: RayRenderInput, metadata: T) = synchronized(lock) {
        if (closed.get() || !visible || !mode.requested) return@synchronized
        val owned = input.copy(metadata = metadata)
        val current = scheduler
        if (current == null) preparingInput = owned else current.offer(owned)
    }

    fun latest(): RayViewCompleted<T>? = synchronized(lock) {
        if (closed.get() || !visible || !mode.active) return@synchronized null
        val value = scheduler?.latest() ?: return@synchronized null
        @Suppress("UNCHECKED_CAST")
        RayViewCompleted(value, value.batch.input.metadata as T)
    }

    internal fun wanted(revision: Long) = !closed.get() && visible && mode.requested && mode.snapshot.revision == revision

    /** Installing and accepting the startup input share one CPU lock, preventing a lost offer during native probe. */
    internal fun install(value: RayRenderScheduler, revision: Long): Boolean = synchronized(lock) {
        if (!wanted(revision)) return@synchronized false
        scheduler = value
        preparingInput?.let(value::offer)
        preparingInput = null
        true
    }

    /** Reject previous settings immediately while preserving the installed native session/scheduler. */
    internal fun invalidateSettingsWork() = synchronized(lock) {
        scheduler?.cancel()
        preparingInput = null
    }

    internal fun clearPublication() = synchronized(lock) {
        scheduler?.cancel()
        scheduler = null
        preparingInput = null
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        mode.off()
        clearPublication()
        service.remove(this)
    }
}
