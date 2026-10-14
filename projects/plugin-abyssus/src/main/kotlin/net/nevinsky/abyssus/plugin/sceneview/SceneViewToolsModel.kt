/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.pick.GizmoSpace

/** How the view shades what it draws. */
enum class Shading { LIT, UNLIT }

/** Which layers a view draws; per view and never saved. [shadows] only matters while [shading] is [Shading.LIT]. */
data class ViewDisplay(
    val shadows: Boolean = true,
    val fog: Boolean = true,
    val grid: Boolean = true,
    val shading: Shading = Shading.LIT,
) {
    /** Whether the shadow pass runs. */
    val drawsShadows: Boolean get() = shadows && shading == Shading.LIT
}

/** The step snapping starts with when first turned on, and the angle step of rotate drags. */
const val DEFAULT_SNAP_STEP = 0.5f
const val SNAP_ANGLE_DEGREES = 15f

/**
 * What the user chose in one scene view besides the tool: gizmo space, snapping, shading and layer toggles. Nothing
 * here is written to a scene. [onChanged] runs after a real change only, on the thread that changed it (the EDT).
 */
class SceneViewToolsModel {
    var onChanged: (() -> Unit)? = null

    var space: GizmoSpace = GizmoSpace.WORLD
        set(value) = change(field, value) { field = value }

    var snapOn: Boolean = false
        set(value) = change(field, value) { field = value }

    var snapStep: Float = DEFAULT_SNAP_STEP
        set(value) {
            require(value > 0f) { "snap step must be positive" }
            change(field, value) { field = value }
        }

    var display: ViewDisplay = ViewDisplay()
        private set

    fun setShadows(on: Boolean) = update { it.copy(shadows = on) }
    fun setFog(on: Boolean) = update { it.copy(fog = on) }
    fun setGrid(on: Boolean) = update { it.copy(grid = on) }
    fun setShading(shading: Shading) = update { it.copy(shading = shading) }

    private fun update(f: (ViewDisplay) -> ViewDisplay) {
        val next = f(display)
        change(display, next) { display = next }
    }

    private fun <T> change(old: T, new: T, assign: () -> Unit) {
        if (old == new) return
        assign()
        onChanged?.invoke()
    }
}
