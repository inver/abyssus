/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3

/**
 * What a Sculpt Terrain stroke calls while the left button sculpts. The same shape as [FoliagePaint]: [pressed] starts a
 * stroke (false refuses it), [dragged] moves it, [released] commits it as one undoable step and [cancelled] discards it.
 * The stroke edits heights in memory and writes no file before [released].
 */
interface TerrainSculpt {
    /** The press at the world [at] on the [terrain]; false refuses the stroke, so the gesture stays idle. */
    fun pressed(terrain: TerrainTarget, at: Vector3): Boolean

    /** The drag to the world [to] on the terrain; [inverted] is the tool's sign as it is now (Shift flips it). */
    fun dragged(to: Vector3, inverted: Boolean)

    /** The button was released: the stroke's files are written as one undoable step. */
    fun released()

    /** Esc (or the terrain leaving the scene): the stroke is discarded and nothing is written. */
    fun cancelled()
}
