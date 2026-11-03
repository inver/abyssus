/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3

/**
 * What the scene view asks of a terrain brush (Paint Foliage, Sculpt Terrain, later others) while the left button
 * strokes the terrain: the stroke's world-space points on the terrain, in press-drag-release order. The implementation
 * holds the brush and its draft, edits them in memory and writes no file before [released]; [cancelled] puts everything
 * back as it was at the press.
 */
interface BrushStroke {
    /** The press at the world [at] on the [terrain]; false refuses the stroke, so the gesture stays idle. */
    fun pressed(terrain: TerrainTarget, at: Vector3): Boolean

    /** The drag to the world [to] on the terrain; [reversed] is the mode's sign as it is now (Erase, an inverted tool, Shift). */
    fun dragged(to: Vector3, reversed: Boolean)

    /** The button was released: the stroke's files are written as one undoable step. */
    fun released()

    /** Esc (or the terrain leaving the scene): the stroke is discarded and nothing is written. */
    fun cancelled()
}
