/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import net.nevinsky.abyssus.lib.core.editor.dummy.DummyMarkerGeometry
import net.nevinsky.abyssus.lib.core.editor.pick.LineSink
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent

/**
 * Draws the dummies' markers (OpenSpec `scene-dummies`, "Dummies are drawn as markers"), beside the camera and light
 * markers: three lines at each dummy's world pose, red X, green Y and blue Z, each [MARKER_PIXELS] long on screen.
 * Every enabled dummy is drawn while Show Dummies is on; the selected one always, brighter and over everything. Owns no
 * GL objects: it draws through the frame's [LineSink].
 */
internal class DummyOverlay {
    private val geometry = DummyMarkerGeometry()

    /** One pass: the unselected markers depth-tested ([onTop] false), then the selected one on top. */
    fun draw(
        lines: LineSink,
        content: SceneContent,
        shown: Boolean,
        selectedId: Long?,
        camera: Camera,
        fieldOfView: Float,
        viewportHeight: Int,
        onTop: Boolean,
    ) {
        for (dummy in geometry.visible(content, shown, selectedId)) {
            val selected = dummy.entityId == selectedId
            if (selected != onTop) {
                continue
            }
            val distance = dummy.transform.position.dst(camera.position)
            val length = geometry.worldLength(distance, fieldOfView, viewportHeight.toFloat())
            for (line in geometry.lines(dummy.transform, length)) {
                lines.line(line.from, line.to, colorOf(line.axis, selected))
            }
        }
    }

    private fun colorOf(axis: Int, selected: Boolean): Color =
        when (axis) {
            0 -> if (selected) {
                SELECTED_X
            } else {
                AXIS_X
            }
            1 -> if (selected) {
                SELECTED_Y
            } else {
                AXIS_Y
            }
            else -> if (selected) {
                SELECTED_Z
            } else {
                AXIS_Z
            }
        }
}

private val AXIS_X = Color(0.86f, 0.25f, 0.25f, 1f)
private val AXIS_Y = Color(0.30f, 0.75f, 0.35f, 1f)
private val AXIS_Z = Color(0.25f, 0.50f, 0.90f, 1f)
private val SELECTED_X = Color(1f, 0.55f, 0.55f, 1f)
private val SELECTED_Y = Color(0.60f, 1f, 0.60f, 1f)
private val SELECTED_Z = Color(0.55f, 0.75f, 1f, 1f)
