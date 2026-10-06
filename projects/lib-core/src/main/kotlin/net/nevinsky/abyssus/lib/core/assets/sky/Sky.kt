/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable

/**
 * A built sky that draws itself as the background: following [Camera]'s orientation but not its position. The caller
 * turns depth testing, depth writes and culling off around [draw]. GL thread only, with the context current.
 */
interface Sky : Disposable {
    /** Draws the sky seen from [camera]; a sky lit by the sun shines from [sun] (a unit vector toward it). */
    fun draw(camera: Camera, sun: Vector3)

    /** Sets [out] to [camera]'s projection times its view with the translation dropped, so a sky stays at infinity. */
    fun rotationOnlyViewProj(camera: Camera, out: Matrix4): Matrix4 {
        out.set(camera.view)
        out.setTranslation(0f, 0f, 0f)
        return out.mulLeft(camera.projection)
    }
}
