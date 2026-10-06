/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.content.Rgba

import com.badlogic.gdx.math.collision.BoundingBox

/** The highlight drawn around the selected entity: the twelve edges of its world bounds. */
object SelectionBox {
    private val HIGHLIGHT = Rgba(1f, 0.72f, 0.15f, 1f)

    fun draw(out: LineSink, box: BoundingBox) {
        val a = box.min
        val b = box.max
        val p = { x: Float, y: Float, z: Float -> Vec3(x, y, z) }
        val corners = listOf(
            p(a.x, a.y, a.z), p(b.x, a.y, a.z), p(b.x, a.y, b.z), p(a.x, a.y, b.z),
            p(a.x, b.y, a.z), p(b.x, b.y, a.z), p(b.x, b.y, b.z), p(a.x, b.y, b.z),
        )
        for (ring in 0..1) for (i in 0 until 4) out.line(corners[ring * 4 + i], corners[ring * 4 + (i + 1) % 4], HIGHLIGHT)
        for (i in 0 until 4) out.line(corners[i], corners[4 + i], HIGHLIGHT)
    }
}
