/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import net.nevinsky.abyssus.lib.core.editor.brush.MaskRect
import net.nevinsky.abyssus.lib.core.editor.brush.emptyMaskRect

/** The heights of a sculpted terrain: each [base] height plus its [delta], one float add per vertex. */
fun composeSculpt(base: FloatArray, delta: FloatArray): FloatArray {
    require(base.size == delta.size) { "base has ${base.size} heights, delta ${delta.size}" }
    return FloatArray(base.size) { base[it] + delta[it] }
}

/**
 * The heights a sculpting stroke edits in memory: copies of the terrain's composed heights and of its additive delta
 * over an unchanging [base]. Strokes call [add], which moves a vertex in both copies, so the views can show
 * [composed] while the stroke runs; [recompose] then makes [composed] exactly `base + delta` again, which is what
 * a commit writes. [revision] moves with every change and [changed] is the rectangle changed since the draft was made,
 * which is what a view copies into its own terrain when it sees a new revision.
 *
 * Pure data with no platform state; the plugin keeps one per terrain that is being sculpted.
 */
class SculptDraft(
    val resolution: Int,
    val base: FloatArray,
    val composed: FloatArray,
    val delta: FloatArray,
) {
    init {
        val count = resolution * resolution
        require(base.size == count && composed.size == count && delta.size == count) { "grids must hold $resolution squared heights" }
    }

    var revision: Int = 0
        private set

    /** Everything changed since the draft was made; a commit writes files only when this is not empty. */
    var changed: MaskRect = emptyMaskRect()
        private set

    /** Moves the vertex at [x],[z] by [change]; a zero change is ignored. */
    fun add(x: Int, z: Int, change: Float) {
        if (change == 0f) return
        val i = z * resolution + x
        delta[i] += change
        composed[i] += change
        changed = changed.union(MaskRect(x, z, x, z))
        revision++
    }

    /** Makes [composed] exactly [base] plus [delta] over the whole grid, as a commit and Regenerate compute it. */
    fun recompose() {
        for (i in composed.indices) composed[i] = base[i] + delta[i]
    }
}
