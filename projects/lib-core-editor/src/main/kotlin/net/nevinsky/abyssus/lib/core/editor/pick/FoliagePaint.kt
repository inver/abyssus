/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3

/**
 * What the scene view asks of the foliage panel while the Paint Foliage mode paints (design decision 8): the
 * stroke's world-space points on the terrain, in press-drag-release order. The caller holds the brush and the draft,
 * edits the mask in memory and writes no file before [released]; [cancelled] puts the mask back as it was at the
 * press.
 */
interface FoliagePaint {
    /** The press at the world [at] on the [terrain]; false refuses the stroke, so the gesture stays idle. */
    fun pressed(terrain: TerrainTarget, at: Vector3): Boolean

    /** The drag to the world [to] on the terrain; [erase] is the mode's sign as it is now (Shift flips it). */
    fun dragged(to: Vector3, erase: Boolean)

    /** The button was released: the stroke's mask and matching bake are written as one undoable step. */
    fun released()

    /** Esc (or the terrain leaving the scene): the stroke is discarded and nothing is written. */
    fun cancelled()
}
