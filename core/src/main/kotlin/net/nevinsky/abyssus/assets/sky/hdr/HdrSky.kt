/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.assets.sky.hdr

import net.nevinsky.abyssus.assets.ShaderSource
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.assets.sky.Sky

/**
 * A built HDR sky: draws its equirectangular image as the background, tone mapped by [curve], and holds
 * the [environment] the scene is lit by. GL thread only.
 */
class HdrSky(val environment: HdrEnvironment, shaders: ShaderSource, private val curve: ToneCurve) : Sky {
    private val program = shaders.program("hdrsky.vert", "hdr_common.glsl", "hdrsky.frag")
    private val mesh = Mesh(true, 3, 0, VertexAttribute(Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE)).also {
        it.setVertices(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
    }
    private val invViewProj = Matrix4()

    /** Draws the sky seen from [camera]'s orientation. The caller sets depth and cull state. */
    override fun draw(camera: Camera, sun: Vector3) {
        invViewProj.set(camera.view)
        invViewProj.setTranslation(0f, 0f, 0f)
        invViewProj.mulLeft(camera.projection)
        invViewProj.inv()
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
