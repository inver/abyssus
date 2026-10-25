/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.brush

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.lib.core.editor.pick.BrushStroke
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget

/**
 * The lifecycle the terrain brush commands share (Paint Foliage, Sculpt Terrain): one stroke of type [S] at a time, a
 * press that starts one only under the [mode] of the terrain it was made on, and drags that go to the stroke in
 * progress. The commit and the discard of a stroke are not shared: a foliage stroke stages its bake on a background
 * thread, a sculpt stroke plans its files at once, so each command writes its own [released] and [cancelled].
 * AWT thread.
 */
abstract class TerrainStrokeCommand<S : Any>(protected val mode: () -> BrushMode?) : BrushStroke {
    /** The stroke in progress, or null. */
    protected var active: S? = null

    /** Starts a stroke at the world [at] on [terrain] under [mode], or null to refuse the press. Does not set [active]. */
    protected abstract fun begin(terrain: TerrainTarget, at: Vector3, mode: BrushMode): S?

    /** The stamp of the press itself, after [active] is set. */
    protected abstract fun press(stroke: S, at: Vector3, mode: BrushMode)

    /** A drag of the stroke in progress to the world [to]. */
    protected abstract fun drag(stroke: S, to: Vector3, reversed: Boolean)

    final override fun pressed(terrain: TerrainTarget, at: Vector3): Boolean {
        val current = mode() ?: return false
        if (active != null || current.entityId != terrain.entityId) return false
        val stroke = begin(terrain, at, current) ?: return false
        active = stroke
        press(stroke, at, current)
        return true
    }

    final override fun dragged(to: Vector3, reversed: Boolean) {
        val stroke = active ?: return
        drag(stroke, to, reversed)
    }

    /** Drops a stroke in progress; the view is closing or the mode ended. */
    open fun dispose() = cancelled()
}
