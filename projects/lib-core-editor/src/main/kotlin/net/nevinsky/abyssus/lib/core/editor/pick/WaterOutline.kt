/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import net.nevinsky.abyssus.lib.core.editor.content.Rgba
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.water.WaterSettings

/** The highlight of a selected water surface: its extent as a rectangle at the mean level. Pure; draws through a [LineSink]. */
class WaterOutline {
    fun draw(out: LineSink, surface: WaterSettings, color: Rgba = SELECTED_WATER_OUTLINE) {
        val hw = (surface.width / 2).toFloat()
        val hl = (surface.length / 2).toFloat()
        val x = surface.x.toFloat()
        val y = surface.level.toFloat()
        val z = surface.z.toFloat()
        val corners = listOf(Vec3(x - hw, y, z - hl), Vec3(x + hw, y, z - hl), Vec3(x + hw, y, z + hl), Vec3(x - hw, y, z + hl))
        for (i in corners.indices) out.line(corners[i], corners[(i + 1) % corners.size], color)
    }
}

/** The colour the selected extent is drawn in (the same amber as the selection box). */
val SELECTED_WATER_OUTLINE = Rgba(1f, 0.72f, 0.15f, 1f)
