/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.Rgba
import kotlin.math.cos
import kotlin.math.sin

/** The brush footprint uses the same terrain-local scaled axes as FoliageBrush. */
internal class FoliageBrushCursor(val terrain: TerrainTarget, val at: Vector3, val radius: Float) {
    fun draw(lines: LineBatch) {
        val sx = Vector3(1f, 0f, 0f).rot(terrain.world).len()
        val sz = Vector3(0f, 0f, 1f).rot(terrain.world).len()
        if (sx <= 0f || sz <= 0f || terrain.world.det() == 0f) return
        val centre = Vector3(at).mul(Matrix4(terrain.world).inv())
        fun point(i: Int): Vec3? {
            val angle = i * Math.PI * 2.0 / 64
            val x = centre.x + radius * cos(angle).toFloat() / sx
            val z = centre.z + radius * sin(angle).toFloat() / sz
            val y = terrain.data.heightAt(x, z) ?: return null
            val p = Vector3(x, y + 0.03f, z).mul(terrain.world)
            return Vec3(p.x, p.y, p.z)
        }
        var previous = point(0)
        for (i in 1..64) {
            val next = point(i)
            if (previous != null && next != null) lines.line(previous, next, Rgba(1f, 0.8f, 0.2f, 1f))
            previous = next
        }
    }
}
