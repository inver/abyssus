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

package net.nevinsky.abyssus.assets.sky.procedural

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
 * A sky computed per pixel by the asset's own GLSL: one fullscreen triangle, no cube. Built on the GL thread; throws
 * when the shaders do not compile, which the asset cache logs once and remembers.
 */
class ProceduralSky(prepared: PreparedProceduralSky) : Sky {
    private val CAMERA_HEIGHT = 100.0

    private val params = prepared.params
    private val program = ShaderProgram(prepared.vertex, prepared.fragment).also {
        if (!it.isCompiled) {
            val log = it.log
            it.dispose()
            throw IllegalStateException("Procedural sky shader failed to compile: $log")
        }
    }
    private val mesh = Mesh(true, 3, 0, VertexAttribute(Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE)).also {
        it.setVertices(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
    }
    private val invViewProj = Matrix4()

    /** Draws the atmosphere seen from [camera]'s orientation with the sun toward [sun]. The caller sets depth and cull state. */
    override fun draw(camera: Camera, sun: Vector3) {
        invViewProj.set(camera.view)
        invViewProj.setTranslation(0f, 0f, 0f)
        invViewProj.mulLeft(camera.projection)
        invViewProj.inv()
        program.bind()
        program.setUniformMatrix("u_invViewProj", invViewProj)
        program.setUniformf("u_sunDir", sun.x, sun.y, sun.z)
        program.setUniformf("u_cameraHeight", CAMERA_HEIGHT.toFloat())
        program.setUniformf("u_planetRadius", params.planetRadius)
        program.setUniformf("u_atmosphereRadius", params.atmosphereRadius)
        program.setUniformf("u_betaRayleigh", params.betaRayleigh[0], params.betaRayleigh[1], params.betaRayleigh[2])
        program.setUniformf("u_betaMie", params.betaMie)
        program.setUniformf("u_heightRayleigh", params.heightRayleigh)
        program.setUniformf("u_heightMie", params.heightMie)
        program.setUniformf("u_mieG", params.mieG)
        program.setUniformf("u_sunIntensity", params.sunIntensity)
        mesh.render(program, GL20.GL_TRIANGLES)
    }

    override fun dispose() {
        mesh.dispose()
        program.dispose()
    }
}
