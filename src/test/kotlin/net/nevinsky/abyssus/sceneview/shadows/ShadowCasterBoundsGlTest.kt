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

package net.nevinsky.abyssus.sceneview.shadows

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.core.Renderable
import net.nevinsky.abyssus.core.mesh.Mesh
import net.nevinsky.abyssus.sceneview.GlHarness
import net.nevinsky.abyssus.sceneview.SceneRenderParams
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ShadowCasterBoundsGlTest {
    @Test fun currentBonesAndNodeTransformsFitBeyondTheFirstPose() {
        assumeTrue(GlHarness.enabled)
        var checked = false
        val result = GlHarness.render(SceneRenderParams.DEFAULT, 2) { _, frame ->
            if (frame != 0) return@render
            val mesh = Mesh(true, 3, 3, VertexAttribute.Position(), VertexAttribute.BoneWeight(0)).apply {
                setVertices(floatArrayOf(-1f,0f,0f,0f,1f, 1f,0f,0f,0f,1f, 0f,1f,0f,0f,1f))
                setIndices(intArrayOf(0,1,2))
            }
            try {
                val renderable = Renderable().apply {
                    meshPart.set("animated", mesh, 0, 3, GL20.GL_TRIANGLES)
                    bones = arrayOf(Matrix4())
                }
                val bounds = ShadowCasterBounds()
                assertEquals(1f, bounds.world(renderable).max.y, 0f)
                renderable.bones!![0].setToTranslation(0f, 12f, 0f)
                renderable.worldTransform.setToTranslation(3f, 0f, 0f)
                val posed = bounds.world(renderable)
                assertEquals(13f, posed.max.y, 0f)
                assertEquals(4f, posed.max.x, 0f)
                val projection = ShadowProjection().directional(Vector3(0f,-1f,0f), posed, listOf(posed))!!
                val clip = Vector3(3f,13f,0f).prj(projection.combined)
                assertTrue(clip.x in -1f..1f && clip.y in -1f..1f && clip.z in -1f..1f)
                checked = true
            } finally { mesh.dispose() }
        }
        assertNull(result.error)
        assertTrue(checked)
    }
}
