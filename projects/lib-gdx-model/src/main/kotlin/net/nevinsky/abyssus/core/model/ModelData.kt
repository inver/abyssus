/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.model

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
