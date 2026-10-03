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

import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.core.Renderable
import net.nevinsky.abyssus.core.mesh.Mesh
import java.util.IdentityHashMap

/** Conservative bounds for normalized skin weights: each posed vertex lies within its bone contributions' union.
 * Mesh positions are read once; subsequent frames transform only per-bone boxes, including node animations. */
class ShadowCasterBounds {
    private val meshes = IdentityHashMap<Mesh, Map<Int, BoundingBox>>()

    fun world(renderable: Renderable): BoundingBox {
        val mesh = renderable.meshPart.mesh!!
        val contributions = meshes.getOrPut(mesh) { read(mesh) }
        val bounds = BoundingBox().inf()
        for ((bone, local) in contributions) {
            val posed = BoundingBox(local)
            renderable.bones?.getOrNull(bone)?.let(posed::mul)
            bounds.ext(posed.mul(renderable.worldTransform))
        }
        return bounds
    }

    fun retain(meshesInUse: Set<Mesh>) { meshes.keys.retainAll(meshesInUse) }
    fun clear() = meshes.clear()

    private fun read(mesh: Mesh): Map<Int, BoundingBox> {
        val attributes = mesh.vertexAttributes
        val position = attributes.findByUsage(VertexAttributes.Usage.Position) ?: return emptyMap()
        val stride = mesh.vertexSize / 4
        val vertices = FloatArray(mesh.numVertices * stride)
        mesh.getVertices(vertices)
        val weights = (0 until attributes.size()).map { attributes[it] }
            .filter { it.usage == VertexAttributes.Usage.BoneWeight }.map { it.offset / 4 }
        val boxes = mutableMapOf<Int, BoundingBox>()
        val point = Vector3()
        for (vertex in 0 until mesh.numVertices) {
            val start = vertex * stride
            val offset = start + position.offset / 4
            point.set(vertices[offset], if (position.numComponents > 1) vertices[offset + 1] else 0f,
                if (position.numComponents > 2) vertices[offset + 2] else 0f)
            var skinned = false
            for (weight in weights) if (vertices[start + weight + 1] > 0f) {
                boxes.getOrPut(vertices[start + weight].toInt()) { BoundingBox().inf() }.ext(point)
                skinned = true
            }
            if (!skinned) boxes.getOrPut(-1) { BoundingBox().inf() }.ext(point)
        }
        return boxes
    }
}
