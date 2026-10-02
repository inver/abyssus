/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.nevinsky.abyssus.core.model

import com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.GdxRuntimeException

/**
 * Returned by a [ModelLoader], contains meshes, materials, nodes and animations. OpenGL resources like textures
 * or vertex buffer objects are not stored. Instead, a ModelData instance needs to be converted to a Model first.
 *
 * @author badlogic
 */
class ModelData {
    var id: String? = null
    val version: IntArray = IntArray(2)
    val meshes: Array<ModelMesh> = Array<ModelMesh>()
    val materials: Array<ModelMaterial> = Array<ModelMaterial>()
    val nodes: Array<ModelNode> = Array<ModelNode>()
    val animations: Array<ModelAnimation> = Array<ModelAnimation>()

    fun addMesh(mesh: ModelMesh) {
        for (other in meshes) {
            if (other.id == mesh.id) {
                throw GdxRuntimeException("Mesh with id '" + other.id + "' already in model")
            }
        }
        meshes.add(mesh)
    }
}
