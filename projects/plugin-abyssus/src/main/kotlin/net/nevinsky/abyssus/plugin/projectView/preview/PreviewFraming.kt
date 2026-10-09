/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView.preview

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.gdx.editor.content.Vec3
import kotlin.math.max
import kotlin.math.sin

/** Where the preview camera looks from: an orbit [target] and [distance], and the clip planes. */
data class PreviewFrame(val target: Vec3, val distance: Float, val near: Float, val far: Float)

/**
 * Frames an imported model in the preview, without Swing or GL. The model stands on y = 0, centred on X and Z, with
 * the [size] the import reports; the 1 m reference post stands beside it ([postX]). The camera orbits the middle of
 * both at a distance that keeps their bounding sphere inside the narrower field of view.
 */
class PreviewFraming(private val fovDegrees: Float = FOV_DEGREES) {
    /** The X of the 1 m post: just beyond the model's -X side. */
    fun postX(size: Vector3): Float = -(size.x / 2f + max(POST_GAP, size.x * 0.1f))

    /** The model's and the post's bounds. */
    fun bounds(size: Vector3): BoundingBox {
        val box = BoundingBox(Vector3(-size.x / 2f, 0f, -size.z / 2f), Vector3(size.x / 2f, size.y, size.z / 2f))
        val post = postX(size)
        box.ext(Vector3(post - POST_WIDTH, 0f, -POST_WIDTH)).ext(Vector3(post + POST_WIDTH, POST_HEIGHT, POST_WIDTH))
        return box
    }

    fun frame(size: Vector3, aspect: Float): PreviewFrame {
        val box = bounds(size)
        val centre = box.getCenter(Vector3())
        val radius = max(box.getDimensions(Vector3()).len() / 2f, MIN_RADIUS)
        val vertical = Math.toRadians(fovDegrees / 2.0)
        val horizontal = Math.atan(Math.tan(vertical) * aspect.coerceAtLeast(0.1f))
        val half = minOf(vertical, horizontal)
        val distance = (radius / sin(half)).toFloat() * MARGIN
        return PreviewFrame(Vec3(centre.x, centre.y, centre.z), distance, max(distance / 1000f, 0.001f), distance + radius * 4f)
    }

    companion object {
        const val FOV_DEGREES = 50f
        const val POST_HEIGHT = 1f
        const val POST_WIDTH = 0.02f
        private const val POST_GAP = 0.25f
        private const val MIN_RADIUS = 0.05f
        private const val MARGIN = 1.1f
    }
}
