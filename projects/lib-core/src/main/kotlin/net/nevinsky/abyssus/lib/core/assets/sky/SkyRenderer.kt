/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique

/**
 * A built sky that draws itself as the background: following [Camera]'s orientation but not its position. The caller
 * turns depth testing, depth writes and culling off around [draw]. GL thread only, with the context current.
 */
interface SkyRenderer : Disposable {
    /** Draws the sky seen from [camera] as [frame] describes it; cube and HDR skies ignore it. */
    fun draw(camera: Camera, frame: SkyFrame)

    /** Sets [out] to [camera]'s projection times its view with the translation dropped, so a sky stays at infinity. */
    fun rotationOnlyViewProj(camera: Camera, out: Matrix4): Matrix4 {
        out.set(camera.view)
        out.setTranslation(0f, 0f, 0f)
        return out.mulLeft(camera.projection)
    }
}

/**
 * What a sky is drawn with in one frame: the [sun] (a unit vector toward it), the view's time in [timeSeconds] (clouds
 * drift by it), and the cloud [technique] to use in place of the asset's own (null: the asset's). [clouds] false draws
 * the sky without its clouds (a baked ray tracing sky).
 */
data class SkyFrame(
    val sun: Vector3,
    val timeSeconds: Double = 0.0,
    val technique: CloudTechnique? = null,
    val clouds: Boolean = true,
)
