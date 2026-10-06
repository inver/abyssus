/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.pick.LineSink
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation

/** An overlay of one view and the name of the plugin it comes from, for the error that switches it off. */
class NamedOverlay(val source: String, val overlay: SceneOverlay)

/**
 * The overlays of one Scene view. An overlay that throws while drawing is switched off for this view and disposed,
 * with one error to [logError] naming its plugin; the others and the scene keep drawing.
 */
class SceneOverlayHost(overlays: List<NamedOverlay>, private val logError: (String, Throwable) -> Unit) {
    private val active = overlays.toMutableList()

    /** The overlays still drawing. */
    val overlays: List<NamedOverlay> get() = active.toList()

    fun draw(view: OverlayView, lines: LineSink) {
        for (named in active.toList()) {
            runCatchingKeepingCancellation { named.overlay.draw(view, lines) }.onFailure { error ->
                active.remove(named)
                logError("Scene overlay of ${named.source} failed and is switched off for this view", error)
                runCatchingKeepingCancellation { named.overlay.dispose() }
            }
        }
    }

    fun dispose() {
        for (named in active) runCatchingKeepingCancellation { named.overlay.dispose() }
        active.clear()
    }
}
