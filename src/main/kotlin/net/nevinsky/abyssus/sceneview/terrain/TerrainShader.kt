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

package net.nevinsky.abyssus.sceneview.terrain

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.Matrix3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.sceneview.FogParams
import net.nevinsky.abyssus.sceneview.LightSet
import net.nevinsky.abyssus.sceneview.Rgba
import net.nevinsky.abyssus.sceneview.Shaders
import net.nevinsky.abyssus.sceneview.TerrainEntity

/**
 * Draws terrains: splat-blended textures with the scene's ambient, directional and point lights and fog. A small
 * program of its own, since the terrain material is not something the model shaders know.
 */
class TerrainShader : Disposable {
    private val program = Shaders.load("terrain")
    private val normalMatrix = Matrix3()

    /** Bound to the sampler units of the layers a terrain does not have, so no sampler is left without a texture. */
    private val blank = Texture(Pixmap(1, 1, Pixmap.Format.RGBA8888).also { it.setColor(1f, 1f, 1f, 1f); it.fill() }, false)

    fun draw(camera: Camera, terrains: Collection<TerrainEntity>, ambient: Rgba?, fog: FogParams?, lights: LightSet) {
        if (terrains.isEmpty()) return
        program.bind()
        program.setUniformMatrix("u_projViewTrans", camera.combined)
        program.setUniformf("u_cameraPos", camera.position)
        val a = ambient
        program.setUniformf("u_ambient", a?.r ?: 0f, a?.g ?: 0f, a?.b ?: 0f)
        program.setUniformf("u_fogColor", fog?.color?.r ?: 0f, fog?.color?.g ?: 0f, fog?.color?.b ?: 0f)
        program.setUniformf("u_fogK", fog?.shaderCoefficient ?: 0f)
        setLights(lights)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        for (t in terrains) {
            program.setUniformMatrix("u_worldTrans", t.world)
            program.setUniformMatrix("u_normalMatrix", normalMatrix.set(t.world).inv().transpose())
            t.terrain.draw(program, blank)
        }
    }

    private fun setLights(lights: LightSet) {
        val dirs = FloatArray(MAX * 3)
        val dirColors = FloatArray(MAX * 3)
        lights.directional.forEachIndexed { i, d ->
            dirs[i * 3] = d.direction.x; dirs[i * 3 + 1] = d.direction.y; dirs[i * 3 + 2] = d.direction.z
            dirColors[i * 3] = d.color.r; dirColors[i * 3 + 1] = d.color.g; dirColors[i * 3 + 2] = d.color.b
        }
        val pos = FloatArray(MAX * 3)
        val pointColors = FloatArray(MAX * 3)
        val pointRanges = FloatArray(MAX)
        lights.point.forEachIndexed { i, p ->
            pos[i * 3] = p.position.x; pos[i * 3 + 1] = p.position.y; pos[i * 3 + 2] = p.position.z
            pointRanges[i] = p.range
            pointColors[i * 3] = p.color.r; pointColors[i * 3 + 1] = p.color.g; pointColors[i * 3 + 2] = p.color.b
        }
        program.setUniformi("u_numDirectional", lights.directional.size)
        program.setUniform3fv("u_dirDirection", dirs, 0, MAX * 3)
        program.setUniform3fv("u_dirColor", dirColors, 0, MAX * 3)
        program.setUniformi("u_numPoint", lights.point.size)
        program.setUniform3fv("u_pointPosition", pos, 0, MAX * 3)
        program.setUniform3fv("u_pointColor", pointColors, 0, MAX * 3)
        program.setUniform1fv("u_pointRange", pointRanges, 0, MAX)
    }

    override fun dispose() {
        program.dispose()
        blank.dispose()
    }

    private companion object {
        const val MAX = 5 // at least LightSet.MAX_POINT and MAX_DIRECTIONAL; the array size in terrain.frag
    }
}
