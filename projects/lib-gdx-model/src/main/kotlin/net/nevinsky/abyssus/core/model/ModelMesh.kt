/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.model

import com.badlogic.gdx.graphics.VertexAttribute

class ModelMesh {
    var id: String? = null
    lateinit var attributes: Array<VertexAttribute>
    lateinit var vertices: FloatArray
    lateinit var parts: Array<ModelMeshPart>
}
