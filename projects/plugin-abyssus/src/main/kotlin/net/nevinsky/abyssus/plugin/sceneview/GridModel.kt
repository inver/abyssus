/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3

/** The ground grid the view draws under the scene: unit lines on the XZ plane. Needs a GL context to build. */
object GridModel {
    const val HALF_EXTENT = 50

    /** Lines need normals for the default shader to apply ambient light; emissive keeps the grid visible without it. */
    fun build(): Model {
        val builder = ModelBuilder()
        builder.begin()
        val material = Material(ColorAttribute.createDiffuse(Color.WHITE), ColorAttribute.createEmissive(0.25f, 0.25f, 0.25f, 1f))
        val part = builder.part("grid", GL20.GL_LINES, (Usage.Position or Usage.Normal).toLong(), material)
        val info = VertexInfo()
        val n = HALF_EXTENT.toFloat()
        for (i in -HALF_EXTENT..HALF_EXTENT) {
            val f = i.toFloat()
            val a = part.vertex(info.set(Vector3(f, 0f, -n), Vector3.Y, null, null))
            val b = part.vertex(info.set(Vector3(f, 0f, n), Vector3.Y, null, null))
            part.line(a, b)
            val c = part.vertex(info.set(Vector3(-n, 0f, f), Vector3.Y, null, null))
            val d = part.vertex(info.set(Vector3(n, 0f, f), Vector3.Y, null, null))
            part.line(c, d)
        }
        return builder.end()
    }
}
