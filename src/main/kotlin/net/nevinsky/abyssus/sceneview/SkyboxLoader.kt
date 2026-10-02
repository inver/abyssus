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

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.core.loader.Pixmaps

/** The six decoded faces of a skybox asset, in libGDX cube map order. Released when built or discarded. */
class PreparedSkybox(val faces: List<Pixmap>) {
    fun dispose() = faces.forEach(Pixmap::dispose)
}

/** A skybox on the GPU: a unit cube sampled from a cube map. */
class SkyboxCube(prepared: PreparedSkybox) : Disposable {
    private val cubemap = prepared.faces.let { Cubemap(it[0], it[1], it[2], it[3], it[4], it[5]) }
    private val mesh = Mesh(true, 8, 36, VertexAttribute.Position()).also {
        it.setVertices(floatArrayOf(-1f, -1f, -1f, 1f, -1f, -1f, 1f, 1f, -1f, -1f, 1f, -1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f, 1f))
        it.setIndices(shortArrayOf(
            0, 1, 2, 2, 3, 0, 4, 6, 5, 6, 4, 7, 0, 3, 7, 7, 4, 0,
            1, 5, 6, 6, 2, 1, 3, 2, 6, 6, 7, 3, 0, 4, 5, 5, 1, 0,
        ))
    }

    init {
        prepared.dispose()
    }

    fun draw(program: ShaderProgram) {
        cubemap.bind(0)
        program.setUniformi("u_cubemap", 0)
        mesh.render(program, GL20.GL_TRIANGLES)
    }

    override fun dispose() {
        mesh.dispose()
        cubemap.dispose()
    }
}

/** Skybox assets: the six face images decoded off the GL thread, then uploaded as one cube map. */
class SkyboxLoader : AssetLoader<PreparedSkybox, SkyboxCube> {
    /**
     * Mundus builds the cube map from (back, front, left, right, bottom, top) as (+X, -X, +Y, -Y, +Z, -Z); the same
     * order is kept so a skybox looks here as it does in the editor.
     */
    override fun prepare(files: ProjectAssetFiles, name: String): PreparedSkybox? {
        val skybox = files.skybox(name) ?: return null
        val faces = ArrayList<Pixmap>(6)
        try {
            for (f in listOf(skybox.back, skybox.front, skybox.left, skybox.right, skybox.bottom, skybox.top)) {
                faces += Pixmaps.load(FileHandle(f))
            }
        } catch (e: Throwable) {
            faces.forEach(Pixmap::dispose)
            throw e
        }
        return PreparedSkybox(faces)
    }

    override fun build(prepared: PreparedSkybox) = SkyboxCube(prepared)

    override fun discard(prepared: PreparedSkybox) = prepared.dispose()
}
