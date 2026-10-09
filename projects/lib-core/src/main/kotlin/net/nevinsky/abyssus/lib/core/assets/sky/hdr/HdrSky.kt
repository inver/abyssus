/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.hdr

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.core.util.GeometryUtils.Companion.createFullscreenTriangle
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.SkyRenderer
import net.nevinsky.abyssus.lib.core.assets.sky.SkyFrame

/**
 * A built HDR sky: draws its equirectangular image as the background, tone mapped by [curve], and holds
 * the [environment] the scene is lit by. GL thread only.
 */
class HdrSky(val environment: HdrEnvironment, shaders: ShaderStorage, private val curve: ToneCurve) : SkyRenderer {
    private val program = shaders.program("hdrsky.vert", "hdr_common.glsl", "hdrsky.frag")
    private val mesh = createFullscreenTriangle()
    private val invViewProj = Matrix4()

    /** Draws the sky seen from [camera]'s orientation. The caller sets depth and cull state. */
    override fun draw(camera: Camera, frame: SkyFrame) {
        rotationOnlyViewProj(camera, invViewProj).inv()
        program.bind()
        environment.equirect.bind(0)
        program.setUniformi("u_equirect", 0)
        program.setUniformMatrix("u_invViewProj", invViewProj)
        program.setUniformf("u_exposure", curve.exposure)
        mesh.render(program, GL20.GL_TRIANGLES)
    }

    override fun dispose() {
        mesh.dispose()
        program.dispose()
        environment.dispose()
    }
}
