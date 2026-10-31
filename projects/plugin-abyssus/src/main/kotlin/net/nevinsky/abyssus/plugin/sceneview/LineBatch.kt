/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import com.badlogic.gdx.graphics.Color
import net.nevinsky.abyssus.lib.core.editor.pick.LineSink

/**
 * Draws colored line segments in world space with the camera given to [begin], batched into one dynamic mesh.
 * Call only with the GL context current.
 */
class LineBatch(shaders: ShaderStorage) : LineSink, Disposable {
    private val program = shaders.program("lines")
    private val mesh = Mesh(
        false, MAX_VERTICES, 0,
        VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
        VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"),
    )
    private val vertices = FloatArray(MAX_VERTICES * FLOATS_PER_VERTEX)
    private var count = 0

    /** The triangles of the wide lines: two per line, six vertices. */
    private val quads = FloatArray(MAX_VERTICES * FLOATS_PER_VERTEX)
    private var quadCount = 0
    private var depthTest = true
    private var camera: Camera? = null

    /** Starts a pass over [camera]; lines are hidden behind nearer geometry only when [depthTest] is on. */
    fun begin(camera: Camera, depthTest: Boolean) {
        this.camera = camera
        this.depthTest = depthTest
        count = 0
        quadCount = 0
    }

    /**
     * A line [widthPx] pixels wide: a strip of two triangles across the line, as wide on the screen at both ends however
     * far they are (the camera's viewport height gives the pixel size).
     */
    override fun thickLine(from: Vector3, to: Vector3, color: Color, widthPx: Float) {
        val cam = camera as? PerspectiveCamera ?: return line(from, to, color)
        if (quadCount + 6 > MAX_VERTICES) flush()
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val d = cam.direction
        // across the line and the view direction, so the strip faces the camera
        var sx = dy * d.z - dz * d.y
        var sy = dz * d.x - dx * d.z
        var sz = dx * d.y - dy * d.x
        val length = kotlin.math.sqrt(sx * sx + sy * sy + sz * sz)
        if (length < 1e-6f) return line(from, to, color)
        sx /= length; sy /= length; sz /= length
        val perPixel =
            2f * kotlin.math.tan(Math.toRadians(cam.fieldOfView.toDouble() / 2.0)).toFloat() / cam.viewportHeight

        fun half(p: Vector3): Float {
            val depth = (p.x - cam.position.x) * d.x + (p.y - cam.position.y) * d.y + (p.z - cam.position.z) * d.z
            return widthPx / 2f * perPixel * kotlin.math.max(depth, cam.near)
        }

        val ha = half(from)
        val hb = half(to)
        val a0 = Vector3(from.x - sx * ha, from.y - sy * ha, from.z - sz * ha)
        val a1 = Vector3(from.x + sx * ha, from.y + sy * ha, from.z + sz * ha)
        val b0 = Vector3(to.x - sx * hb, to.y - sy * hb, to.z - sz * hb)
        val b1 = Vector3(to.x + sx * hb, to.y + sy * hb, to.z + sz * hb)
        for (corner in listOf(a0, a1, b0, a1, b1, b0)) putQuad(corner, color)
    }

    private fun putQuad(p: Vector3, c: Color) {
        val i = quadCount * FLOATS_PER_VERTEX
        quads[i] = p.x; quads[i + 1] = p.y; quads[i + 2] = p.z
        quads[i + 3] = c.r; quads[i + 4] = c.g; quads[i + 5] = c.b; quads[i + 6] = c.a
        quadCount++
    }

    override fun line(from: Vector3, to: Vector3, color: Color) {
        if (count + 2 > MAX_VERTICES) flush()
        put(from, color)
        put(to, color)
    }

    private fun put(p: Vector3, c: Color) {
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
        if ((count == 0 && quadCount == 0) || camera == null) {
            count = 0
            quadCount = 0
            return
        }
        if (depthTest) Gdx.gl.glEnable(GL20.GL_DEPTH_TEST) else Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        program.bind()
        program.setUniformMatrix("u_projView", camera.combined)
        if (count > 0) {
            mesh.setVertices(vertices, 0, count * FLOATS_PER_VERTEX)
            mesh.render(program, GL20.GL_LINES, 0, count)
        }
        if (quadCount > 0) {
            mesh.setVertices(quads, 0, quadCount * FLOATS_PER_VERTEX)
            mesh.render(program, GL20.GL_TRIANGLES, 0, quadCount)
        }
        count = 0
        quadCount = 0
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
