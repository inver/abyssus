/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.brush.GridRect
import net.nevinsky.abyssus.lib.core.editor.brush.StampBrush

/**
 * The brush of the Paint Texture mode: a [StampBrush] over the [draft] that stamps a [SplatTool] along the world-space
 * drag path. One stroke is one instance, like `SculptBrush`. Pure math: it runs on the AWT thread and touches no
 * `Gdx.*`.
 */
class SplatBrush(draft: SplatDraft, exclusive: Boolean, world: Matrix4) {
    private val brush = StampBrush(SplatTarget(draft, exclusive), world)

    /** Stamps [tool] along the segment from [from] to [to]; returns the rectangle of texels that changed. */
    fun stroke(from: Vector3, to: Vector3, radius: Float, strength: Float, tool: SplatTool, reversed: Boolean): GridRect =
        brush.stroke(from, to, radius, strength, tool, reversed)
}
