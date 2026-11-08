/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.core.editor.pick.LineSink

/** The highlight drawn around the selected entity: the twelve edges of its world bounds. */
object SelectionBox {
    private val HIGHLIGHT = Color(1f, 0.72f, 0.15f, 1f)

    /** The color of the selected part's outline, apart from the entity's. */
    private val PART = Color(0.2f, 0.85f, 1f, 1f)

    fun draw(out: LineSink, box: BoundingBox) = draw(out, box, Matrix4(), HIGHLIGHT)

    /** The outline of a selected part: its own [local] bounds posed with [world]. */
    fun drawPart(out: LineSink, local: BoundingBox, world: Matrix4) = draw(out, local, world, PART)

    private fun draw(out: LineSink, box: BoundingBox, world: Matrix4, color: Color) {
        val a = box.min
        val b = box.max
        val p = { x: Float, y: Float, z: Float -> Vector3(x, y, z).mul(world) }
        val corners =
            listOf(
                p(a.x, a.y, a.z),
                p(b.x, a.y, a.z),
                p(b.x, a.y, b.z),
                p(a.x, a.y, b.z),
                p(a.x, b.y, a.z),
                p(b.x, b.y, a.z),
                p(b.x, b.y, b.z),
                p(a.x, b.y, b.z),
            )
        for (ring in 0..1) for (i in 0 until 4) out.line(
            corners[ring * 4 + i],
            corners[ring * 4 + (i + 1) % 4],
            color,
        )
        for (i in 0 until 4) out.line(corners[i], corners[4 + i], color)
    }
}
