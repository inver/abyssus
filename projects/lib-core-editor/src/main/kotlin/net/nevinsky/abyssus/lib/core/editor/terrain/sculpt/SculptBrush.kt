/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.brush.GridRect
import net.nevinsky.abyssus.lib.core.editor.brush.StampBrush

/**
 * The brush of the Sculpt Terrain mode: a [StampBrush] over the [draft] that stamps a [SculptTool] along the
 * world-space drag path and adds each stamp's changes to the draft. A vertex within the radius weighs
 * `strength * smoothstep(1 - d / radius)`.
 *
 * One stroke is one instance, like `FoliageBrush`. Pure math over the draft: it runs on the AWT thread and touches no
 * `Gdx.*`.
 */
class SculptBrush(draft: SculptDraft, size: Int, world: Matrix4) {
    private val brush = StampBrush(SculptTarget(draft, size), world)

    /**
     * Stamps [tool] along the segment from [from] to [to] and returns the rectangle of vertices whose height changed;
     * empty when nothing did. [inverted] is the Shift state, [radius] is in world units.
     */
    fun stroke(
        from: Vector3,
        to: Vector3,
        radius: Float,
        strength: Float,
        tool: SculptTool,
        inverted: Boolean
    ): GridRect = brush.stroke(from, to, radius, strength, tool, inverted)
}
