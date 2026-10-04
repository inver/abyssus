/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.sky

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4

/** One triangle covering the whole viewport (2D positions, `a_position`); the caller disposes the mesh. GL thread only. */
fun createFullscreenTriangle(): Mesh =
    Mesh(true, 3, 0, VertexAttribute(Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE)).also {
        it.setVertices(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
    }

/** Sets [out] to [camera]'s projection times its view with the translation dropped, so a sky stays at infinity. */
fun rotationOnlyViewProj(camera: Camera, out: Matrix4): Matrix4 {
    out.set(camera.view)
    out.setTranslation(0f, 0f, 0f)
    return out.mulLeft(camera.projection)
}
