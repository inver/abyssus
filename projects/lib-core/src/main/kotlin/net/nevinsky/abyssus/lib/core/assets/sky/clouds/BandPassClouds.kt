/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderSource
import net.nevinsky.abyssus.lib.core.util.GeometryUtils.Companion.createFullscreenTriangle

/**
 * Clouds drawn one fullscreen pass per band, the band's fragment shader [fragment] (after `clouds_common.glsl` and
 * `clouds_light.glsl`) deciding what each pixel sees of it. Far bands first, so lower ones hide higher ones.
 */
open class BandPassClouds(shaders: ShaderSource, fragment: String, private val field: CloudField) : CloudRenderer() {
    private val program: ShaderProgram =
        shaders.program("clouds.vert", "clouds_common.glsl", "clouds_light.glsl", fragment)
    private val mesh: Mesh = createFullscreenTriangle()

    override fun draw(scene: CloudScene) {
        withPremultipliedBlend {
            program.bind()
            setCloudFrame(program, scene)
            for (band in scene.clouds.settings.bandsFarToNear()) {
                setCloudBand(program, band, field, scene.timeSeconds)
                mesh.render(program, GL20.GL_TRIANGLES)
            }
        }
    }

    override fun dispose() {
        mesh.dispose()
        program.dispose()
    }
}

/** The fast technique: each band a flat layer at its mid altitude, with 2D coverage. */
class LayeredClouds(shaders: ShaderSource, field: CloudField) : BandPassClouds(shaders, "clouds_layered.frag", field)

/** Each band as eight stacked shells from base to top: thickness, parallax and darker bases. */
class ShellClouds(shaders: ShaderSource, field: CloudField) : BandPassClouds(shaders, "clouds_shells.frag", field)
