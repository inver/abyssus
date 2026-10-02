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

package net.nevinsky.abyssus.model

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.core.ModelBatch
import net.nevinsky.abyssus.core.ModelInstance
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.core.shader.DefaultShaderProvider
import net.nevinsky.abyssus.core.shader.ShaderProvider
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Real GL upload and draw of a mesh with more than 65,535 vertices. Opt-in: `-Dabyssus.glTests=true`. */
class LargeMeshGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    @Test
    fun uploadsBoundsAndDrawsAllVertices() {
        val grid = GridModel(300)
        val file = grid.write()
        val loader = AssimpModelLoader()
        val data = loader.loadData(FileHandle(file))

        TestGl.run {
            val model = loader.build(data, FileHandle(file))
            val shaders = DefaultShaderProvider()
            try {
                val mesh = model.meshes.single()
                assertEquals(grid.vertexCount, mesh.numVertices)
                assertEquals(grid.indexCount, mesh.numIndices)

                // reads positions through the 32-bit index buffer: a 16-bit index would stop at row 218
                val instance = ModelInstance(model)
                val box = instance.calculateBoundingBox(BoundingBox())!!
                val far = (grid.size - 1).toFloat()
                assertEquals(0f, box.min.x, 1e-4f)
                assertEquals(0f, box.min.z, 1e-4f)
                assertEquals(far, box.max.x, 1e-4f)
                assertEquals(far, box.max.z, 1e-4f)

                val camera = PerspectiveCamera(67f, 320f, 240f).apply {
                    position.set(far / 2, 200f, far / 2 + 200f)
                    lookAt(far / 2, 0f, far / 2)
                    near = 0.1f
                    this.far = 2000f
                    update()
                }
                val batch = ModelBatch(shaders)
                batch.begin(camera)
                batch.render(instance, ShaderProvider.DEFAULT_SHADER_KEY)
                batch.end()
                assertEquals("GL error after drawing", GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            } finally {
                shaders.dispose()
                model.dispose()
            }
        }
    }
}
