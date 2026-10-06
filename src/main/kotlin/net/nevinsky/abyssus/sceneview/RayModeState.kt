/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.ray.RayModePhase
import net.nevinsky.abyssus.editor.ray.RayModeSnapshot
import net.nevinsky.abyssus.editor.ray.RayBackendSelection

import net.nevinsky.abyssus.raytracing.RayBackendInfo

/**
 * Pure per-view transitions. Revision tokens reject late preparation/completion after off, retry, hide or close.
 * It holds no camera, selection or drag state: switching renderers cannot mutate the interaction model.
 */
internal class RayModeState {
    @Volatile var snapshot = RayModeSnapshot()
        private set

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    /** Called after every transition, from whichever thread made it; a listener must only schedule UI work, never block. */
    fun addListener(listener: () -> Unit) { listeners += listener }

    private fun changed() { listeners.forEach { it() } }
    val phase get() = snapshot.phase
    val requested get() = snapshot.requested
    val active get() = snapshot.active
    val toggleEnabled get() = snapshot.toggleEnabled
    val backendInfo get() = snapshot.backendInfo
    val failure get() = snapshot.failure
    val unavailable get() = snapshot.unavailable

    @Synchronized fun request(): Long {
        if (snapshot.requested) return snapshot.revision
        snapshot = RayModeSnapshot(RayModePhase.Checking, snapshot.revision + 1)
        changed()
        return snapshot.revision
    }

    @Synchronized fun retry(): Long? {
        if (snapshot.phase != RayModePhase.Failed) return null
        return request()
    }

    @Synchronized fun off() {
        snapshot = RayModeSnapshot(revision = snapshot.revision + 1)
        changed()
    }

    /** Hidden views keep the toolbar request, but revoke any worker result from the previous visible lifetime. */
    @Synchronized fun rechecking(): Long? {
        if (!snapshot.requested) return null
        snapshot = RayModeSnapshot(RayModePhase.Checking, snapshot.revision + 1)
        changed()
        return snapshot.revision
    }

    @Synchronized fun preparing(revision: Long, info: RayBackendInfo) {
        if (snapshot.revision != revision || snapshot.phase != RayModePhase.Checking) return
        snapshot = snapshot.copy(phase = RayModePhase.Preparing, backendInfo = info)
        changed()
    }

    @Synchronized fun prepared(revision: Long) {
        if (snapshot.revision != revision || snapshot.phase != RayModePhase.Preparing) return
        snapshot = snapshot.copy(phase = RayModePhase.Active)
        changed()
    }

    @Synchronized fun unavailable(revision: Long, reason: RayBackendSelection.Unavailable) {
        if (snapshot.revision != revision || snapshot.phase != RayModePhase.Checking) return
        snapshot = snapshot.copy(phase = RayModePhase.Unavailable, unavailable = reason)
        changed()
    }

    @Synchronized fun failed(revision: Long, detail: String?) {
        if (snapshot.revision != revision || !snapshot.requested) return
        snapshot = snapshot.copy(phase = RayModePhase.Failed, failure = detail)
        changed()
    }
}
