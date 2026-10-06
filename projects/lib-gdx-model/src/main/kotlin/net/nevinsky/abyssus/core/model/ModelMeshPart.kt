/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.model

import com.badlogic.gdx.math.collision.BoundingBox
class ModelMeshPart {
    var id: String? = null
    lateinit var indices: IntArray
    var primitiveType: Int = 0
    var boundingBox: BoundingBox? = null
}
