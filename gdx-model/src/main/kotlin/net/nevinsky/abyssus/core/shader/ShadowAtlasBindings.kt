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

package net.nevinsky.abyssus.core.shader

import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4

/** Uniform packing shared by model consumers and dedicated surface shaders. The caller binds the atlas to [unit]. */
class ShadowAtlasBindings {
    fun bind(program: ShaderProgram, atlas: ShadowAtlasAttribute?, directional: List<ShadowLightRecord?>,
             points: List<ShadowLightRecord?>, spots: List<ShadowLightRecord?>, unit: Int) {
        fun location(name: String) = program.fetchUniformLocation(name, false)
        fun scalar(name: String, value: Float) { val loc = location(name); if (loc >= 0) program.setUniformf(loc, value) }
        fun array(name: String, values: FloatArray) { val loc = location("$name[0]"); if (loc >= 0) program.setUniform1fv(loc, values, 0, values.size) }
        val sampler = location("u_shadowAtlas"); if (sampler >= 0) program.setUniformi(sampler, unit)
        scalar("u_shadowEnabled", if (atlas != null) 1f else 0f)
        val texel = location("u_shadowTexel")
        if (texel >= 0) program.setUniformf(texel, 1f / (atlas?.atlas?.width ?: 4096), 1f / (atlas?.atlas?.height ?: 4096))
        val matrices = FloatArray(16 * 16)
        val identity = Matrix4().`val`
        for (i in 0 until 16) identity.copyInto(matrices, i * 16)
        val tiles = FloatArray(16 * 4)
        val positions = FloatArray(16 * 3)
        val fars = FloatArray(16) { 1f }
        val bias = FloatArray(16)
        val starts = mutableMapOf<String, Int>()
        var index = 0
        atlas?.records?.forEach { record ->
            require(index + record.views.size <= 16) { "A shadow atlas supports at most 16 views" }
            starts[record.lightId] = index
            record.views.forEach { view ->
                view.matrix.`val`.copyInto(matrices, index * 16)
                val uv = view.uvTransform
                tiles[index * 4] = uv.x; tiles[index * 4 + 1] = uv.y; tiles[index * 4 + 2] = uv.z; tiles[index * 4 + 3] = uv.w
                positions[index * 3] = record.position.x; positions[index * 3 + 1] = record.position.y; positions[index * 3 + 2] = record.position.z
                fars[index] = record.far; bias[index] = record.depthBias
                index++
            }
        }
        location("u_shadowMatrices[0]").takeIf { it >= 0 }?.let { program.setUniformMatrix4fv(it, matrices, 0, matrices.size) }
        location("u_shadowTiles[0]").takeIf { it >= 0 }?.let { program.setUniform4fv(it, tiles, 0, tiles.size) }
        location("u_shadowPositions[0]").takeIf { it >= 0 }?.let { program.setUniform3fv(it, positions, 0, positions.size) }
        array("u_shadowFars", fars); array("u_shadowBias", bias)
        val dirs = FloatArray(2) { -1f }; val local = FloatArray(30) { -1f }; val cones = FloatArray(5) { -1f }
        directional.take(2).forEachIndexed { i, record -> record?.let { starts[it.lightId]?.let { start -> dirs[i] = start.toFloat() } } }
        points.take(5).forEachIndexed { i, record -> record?.let { starts[it.lightId]?.let { start ->
            if (it.views.size == 6) for (face in 0..5) local[i * 6 + face] = (start + face).toFloat()
        } } }
        spots.take(5).forEachIndexed { i, record -> record?.let { starts[it.lightId]?.let { start -> cones[i] = start.toFloat() } } }
        array("u_dirShadowTile", dirs); array("u_pointShadowTiles", local); array("u_spotShadowTile", cones)
    }
}
