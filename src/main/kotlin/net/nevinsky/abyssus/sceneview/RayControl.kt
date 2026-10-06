/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.ray.RayModeSnapshot

import com.intellij.openapi.Disposable

/**
 * The runtime switch of one Scene view's Ray Tracing mode, for anything outside the view that needs to flip it (the
 * Abyssus Properties panel). All members belong to the AWT thread. It holds no persisted preference: the mode lives in the
 * view and ends with it.
 */
interface RayControl {
    /** The view's current mode; null when this view has no ray tracing at all. */
    val mode: RayModeSnapshot?

    /** Switches ray tracing on or off for the view, exactly like its toolbar toggle. */
    fun setRequested(enabled: Boolean)

    /** Checks the GPU again after a failure, like the toolbar's Retry. */
    fun retry()

    /** Calls [listener] on the AWT thread after the mode changes, until [parent] is disposed. */
    fun addListener(parent: Disposable, listener: () -> Unit)
}

/** Implemented by a [SceneView] that has a runtime Ray Tracing switch; other views simply do not implement it. */
interface RayControlProvider {
    val rayControl: RayControl?
}
