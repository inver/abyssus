/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The wire pass on its own (opt-in GL): a wire is drawn to scale, and a distant 0.5 mm one is faint but there. */
class WireRendererGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val size = 200
    /** Shining along the wire: the half vector is not across it, so there is no glint. */
    private val alongTheWire = Vector3(-1f, 0f, 0f)

    /** Shining down onto a level wire: the half vector is across it, so it glints. */
    private val overhead = Vector3(0f, -1f, 0f)

    private fun camera() = PerspectiveCamera(67f, size.toFloat(), size.toFloat()).apply {
        position.set(0f, 0f, 0f)
        lookAt(0f, 0f, -1f)
        near = 0.1f
        far = 100f
        update()
    }

    /** The red channel (0..255) of the pixels in column [x], rows [from]..[to], after drawing [wire] in [sun]'s light. */
    private fun draw(wire: LineSegment, sun: Vector3, x: Int, from: Int, to: Int): List<Int> {
        val renderer = WireRenderer()
        try {
            Gdx.gl.glViewport(0, 0, size, size)
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            renderer.draw(listOf(wire), camera(), sun)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            val pixels = ScreenUtils.getFrameBufferPixels(0, 0, size, size, false)
            return (from..to).map { y -> pixels[(y * size + x) * 4].toInt() and 0xff }
        } finally {
            renderer.dispose()
        }
    }

    @Test
    fun aWireIsDrawnToScaleAndADistantHalfMillimetreOneIsFaint() = TestGl.run {
        // 20 cm thick, 2 m away: about 15 pixels wide and opaque
        val thick = LineSegment(Vector3(-5f, 0f, -2f), Vector3(5f, 0f, -2f), Color.WHITE, thickness = 0.2f)
        val near = draw(thick, alongTheWire, size / 2, 0, size - 1)
        assertTrue("a thick wire shows ${near.count { it > 200 }} bright rows", near.count { it > 200 } in 8..25)

        // 0.5 mm, 15 m away: one pixel wide at about 12% opacity
        val thin = LineSegment(Vector3(-5f, 0f, -15f), Vector3(5f, 0f, -15f), Color.WHITE, thickness = 0.0005f)
        val lit = draw(thin, alongTheWire, size / 2, 0, size - 1).filter { it > 0 }
        assertTrue("the thin wire lit ${lit.size} rows", lit.size in 1..3)
        assertTrue("the thin wire peaks at ${lit.max()}", lit.max() in 10..60)

        // in the sun's reflection the same wire glints brighter
        val glint = draw(thin, overhead, size / 2, 0, size - 1).max()
        assertTrue("the glint peaks at $glint, the plain wire at ${lit.max()}", glint > lit.max() + 40)
    }
}
