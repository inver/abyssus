/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.water

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.core.water.WaterReflectionCamera

/**
 * The view through the water surface at [level]: [source]'s projection with the scene mirrored across the plane (the
 * view matrix is `source.view * mirror`), so the real scene drawn through it lands where its reflection is seen by the
 * real camera, and a water fragment samples the image at its own screen position. Position, direction and up are the
 * mirrored ones (lights, fog and sorting read them), the near and far planes and the frustum are the real camera's.
 * Triangles drawn through it have the opposite winding: the pass draws with clockwise front faces.
 *
 * The camera follows [source] each time [update] runs, so it never needs setting up by hand.
 */
internal class MirroredCamera(
    private val source: PerspectiveCamera,
    level: Float,
    private val reflection: WaterReflectionCamera = WaterReflectionCamera(),
) : PerspectiveCamera(source.fieldOfView, source.viewportWidth, source.viewportHeight) {
    // the superclass constructor calls update() before these exist
    private var ready = false
    private var mirror = Matrix4()
    var level: Float = level
        set(value) {
            field = value
            if (ready) mirror = reflection.matrix(value)
        }

    init {
        mirror = reflection.matrix(level)
        ready = true
        update()
    }

    override fun update() = update(true)

    override fun update(updateFrustum: Boolean) {
        if (!ready) return super.update(updateFrustum)
        fieldOfView = source.fieldOfView
        viewportWidth = source.viewportWidth
        viewportHeight = source.viewportHeight
        near = source.near
        far = source.far
        val pose = reflection.pose(source.position, source.direction, source.up, level)
        position.set(pose.position)
        direction.set(pose.direction)
        up.set(pose.up)
        projection.set(source.projection)
        view.set(source.view).mul(mirror)
        combined.set(projection).mul(view)
        if (updateFrustum) {
            invProjectionView.set(combined)
            Matrix4.inv(invProjectionView.`val`)
            // a point is seen when its mirror image is in the main frustum: mirror the main frustum's planes (rebuilding them from
            // the mirrored matrix would turn every normal inside out, since the mirror reverses the winding of the corners)
            frustum.update(source.invProjectionView)
            for (plane in frustum.planes) {
                val n = plane.normal
                plane.set(n.x, -n.y, n.z, plane.d + 2f * level * n.y)
            }
        }
    }
}
