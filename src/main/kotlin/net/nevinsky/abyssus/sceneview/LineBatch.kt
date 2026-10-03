/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.assets.ShaderSource
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.utils.Disposable

/** Somewhere to draw colored line segments; the marker and gizmo geometry is written against it so tests need no GL. */
interface LineSink {
    fun line(from: Vec3, to: Vec3, color: Rgba)
}

/**
 * Draws colored line segments in world space with the camera given to [begin], batched into one dynamic mesh.
 * Call only with the GL context current.
 */
class LineBatch(shaders: ShaderSource) : LineSink, Disposable {
    private val program = shaders.program("lines")
    private val mesh = Mesh(
        false, MAX_VERTICES, 0,
        VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
        VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"),
    )
    private val vertices = FloatArray(MAX_VERTICES * FLOATS_PER_VERTEX)
    private var count = 0
    private var depthTest = true
    private var camera: Camera? = null

    /** Starts a pass over [camera]; lines are hidden behind nearer geometry only when [depthTest] is on. */
    fun begin(camera: Camera, depthTest: Boolean) {
        this.camera = camera
        this.depthTest = depthTest
        count = 0
    }

    override fun line(from: Vec3, to: Vec3, color: Rgba) {
        if (count + 2 > MAX_VERTICES) flush()
        put(from, color)
        put(to, color)
    }

    private fun put(p: Vec3, c: Rgba) {
        val i = count * FLOATS_PER_VERTEX
        vertices[i] = p.x
        vertices[i + 1] = p.y
        vertices[i + 2] = p.z
        vertices[i + 3] = c.r
        vertices[i + 4] = c.g
        vertices[i + 5] = c.b
        vertices[i + 6] = c.a
        count++
    }

    private fun flush() {
        val camera = camera
        if (count == 0 || camera == null) {
            count = 0
            return
        }
        if (depthTest) Gdx.gl.glEnable(GL20.GL_DEPTH_TEST) else Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        program.bind()
        program.setUniformMatrix("u_projView", camera.combined)
        mesh.setVertices(vertices, 0, count * FLOATS_PER_VERTEX)
        mesh.render(program, GL20.GL_LINES, 0, count)
        count = 0
    }

    /** Draws what is left of the pass and leaves depth testing on. */
    fun end() {
        flush()
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        camera = null
    }

    override fun dispose() {
        mesh.dispose()
        program.dispose()
    }

    private companion object {
        const val MAX_VERTICES = 8192
        const val FLOATS_PER_VERTEX = 7
    }
}
