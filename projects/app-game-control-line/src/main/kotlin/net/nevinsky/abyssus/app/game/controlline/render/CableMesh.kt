/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.RenderableProvider
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Pool
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute

/** Cables drawn at once, and points in one: two control lines of two points leave room for a bent line later. */
const val CABLE_MAX_COUNT = 4
const val CABLE_MAX_POINTS = 16

/**
 * The control lines as geometry the model pipeline draws: one dynamic mesh of fixed size, rewritten every frame by [update]
 * without allocating, drawn as steel PBR material through the same `ModelBatch` and environment as the models, so the sun,
 * the sky light and the shadow light it and the output transform ends it. It is a [RenderableProvider]: hand it to
 * `ModelBatch.render`. GL thread only.
 */
class CableMesh : RenderableProvider, Disposable {
    private val geometry = CableGeometry()
    private val mesh = Mesh(
        false, CABLE_MAX_COUNT * CABLE_MAX_POINTS * CABLE_SIDES, CABLE_MAX_COUNT * (CABLE_MAX_POINTS - 1) * CABLE_SIDES * 6,
        VertexAttribute.Position(), VertexAttribute.Normal(),
    )
    private val vertices = FloatArray(mesh.maxVertices * CABLE_VERTEX_FLOATS)
    private val indices = ShortArray(mesh.maxIndices)
    private val radii = FloatArray(CABLE_MAX_POINTS)
    private val materials = kotlin.Array(CABLE_MAX_COUNT) {
        Material(ColorAttribute.createDiffuse(1f, 1f, 1f, 1f), PBRFloatAttribute.createMetallic(METALLIC), PBRFloatAttribute.createRoughness(ROUGHNESS))
    }

    /** Where each cable of the last [update] starts in [indices], and how many indices it has. */
    private val starts = IntArray(CABLE_MAX_COUNT)
    private val counts = IntArray(CABLE_MAX_COUNT)
    private var drawn = 0

    /** Cables kept by the last [update]. */
    val count: Int get() = drawn

    /** The vertices and indices of the last [update], for tests. */
    internal fun vertexData(): FloatArray = vertices
    internal fun indexData(): ShortArray = indices
    internal fun indexRange(cable: Int): IntRange = starts[cable] until starts[cable] + counts[cable]

    /**
     * Rewrites the mesh for [cables] seen from [camera] on a viewport [viewportHeight] pixels tall. A cable beyond
     * [CABLE_MAX_COUNT] is dropped and the points of one beyond [CABLE_MAX_POINTS] are dropped from its end.
     */
    fun update(cables: List<Cable>, camera: Camera, viewportHeight: Float) {
        drawn = minOf(cables.size, CABLE_MAX_COUNT)
        val fov = (camera as? PerspectiveCamera)?.fieldOfView ?: FALLBACK_FOV
        var firstVertex = 0
        var firstIndex = 0
        for (c in 0 until drawn) {
            val cable = cables[c]
            val points = if (cable.points.size > CABLE_MAX_POINTS) cable.points.subList(0, CABLE_MAX_POINTS) else cable.points
            for (i in points.indices) radii[i] = geometry.radiusAt(cable.diameter / 2f, points[i].dst(camera.position), fov, viewportHeight)
            (materials[c].get(ColorAttribute.Diffuse) as ColorAttribute).color.set(cable.color)
            val written = geometry.build(points, radii, vertices, firstVertex, indices, firstIndex)
            starts[c] = firstIndex
            counts[c] = written
            firstVertex += geometry.vertexCount(points.size)
            firstIndex += written
        }
        if (drawn > 0) {
            mesh.setVertices(vertices, 0, firstVertex * CABLE_VERTEX_FLOATS)
            mesh.setIndices(indices, 0, firstIndex)
        }
    }

    override fun getRenderables(renderables: Array<Renderable>, pool: Pool<Renderable>) {
        for (c in 0 until drawn) {
            renderables.add(pool.obtain().also {
                it.material = materials[c]
                it.meshPart.set("cable$c", mesh, starts[c], counts[c], GL20.GL_TRIANGLES)
                it.worldTransform.idt()
            })
        }
    }

    override fun dispose() = mesh.dispose()

    private companion object {
        /** Dark, polished steel: the lines are lit mostly by what they reflect. */
        const val METALLIC = 0.9f
        const val ROUGHNESS = 0.4f
        const val FALLBACK_FOV = 67f
    }
}
