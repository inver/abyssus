/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.dummy

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import kotlin.math.hypot
import kotlin.math.tan
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.scene.DummyPlacement
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent

/** One line of a dummy marker: from the marker's centre to the end of [axis] (0 X red, 1 Y green, 2 Z blue). */
data class MarkerLine(val axis: Int, val from: Vector3, val to: Vector3)

/**
 * The geometry of a dummy's marker (OpenSpec `scene-dummies`, "Dummies are drawn as markers"): three lines along the
 * dummy's world axes, each a fixed size on screen, and the click test that picks it. Pure: no GL.
 */
class DummyMarkerGeometry {
    /** The X, Y and Z lines of a marker at [transform] (its world pose; scale ignored), each [length] world units
     * long. */
    fun lines(transform: PlacementTransform, length: Float): List<MarkerLine> {
        val rotation = Quaternion(transform.rotation).nor()
        val centre = Vector3(transform.position)
        return listOf(Vector3.X, Vector3.Y, Vector3.Z).mapIndexed { axis, unit ->
            val direction = rotation.transform(Vector3(unit))
            MarkerLine(axis, Vector3(centre), Vector3(centre).mulAdd(direction, length))
        }
    }

    /**
     * The world length that spans [pixels] on screen at [distance] from a perspective camera of vertical
     * [fieldOfView] degrees drawing [viewportHeight] pixels high.
     */
    fun worldLength(distance: Float, fieldOfView: Float, viewportHeight: Float, pixels: Float = MARKER_PIXELS): Float {
        if (viewportHeight <= 0f || distance <= 0f) {
            return 0f
        }
        val visible = 2f * distance * tan(Math.toRadians(fieldOfView / 2.0)).toFloat()
        return visible / viewportHeight * pixels
    }

    /**
     * The dummies whose markers are drawn: every enabled dummy while Show Dummies ([shown]) is on, else only the
     * [selectedId] one. A dummy disabled by itself or an ancestor is not in [content] at all.
     */
    fun visible(content: SceneContent, shown: Boolean, selectedId: Long?): List<DummyPlacement> =
        if (shown) {
            content.dummies
        } else {
            content.dummies.filter { it.entityId == selectedId }
        }

    /**
     * The dummy whose drawn marker a click at ([x], [y]) (origin top-left) of a [width] x [height] view picks: the
     * nearest to the camera of those within [PICK_RADIUS] pixels of their centre. CPU only.
     */
    fun pick(markers: List<DummyPlacement>, camera: Camera, x: Int, y: Int, width: Int, height: Int): Long? {
        var best: Long? = null
        var bestDistance = Float.MAX_VALUE
        val screen = Vector3()
        for (marker in markers) {
            screen.set(marker.transform.position)
            camera.project(screen, 0f, 0f, width.toFloat(), height.toFloat())
            if (screen.z !in 0f..1f) {
                continue
            }
            if (!picks(screen.x, height - screen.y, x.toFloat(), y.toFloat())) {
                continue
            }
            val distance = marker.transform.position.dst(camera.position)
            if (distance < bestDistance) {
                bestDistance = distance
                best = marker.entityId
            }
        }
        return best
    }

    /** Whether a click at ([x], [y]) picks a marker whose centre is on screen at ([centreX], [centreY]). */
    fun picks(centreX: Float, centreY: Float, x: Float, y: Float, radius: Float = PICK_RADIUS): Boolean =
        hypot(x - centreX, y - centreY) <= radius
}

/** The on-screen length of each marker line, in pixels. */
const val MARKER_PIXELS = 24f

/** How near (pixels) to a marker's centre a click picks it. */
const val PICK_RADIUS = 8f
