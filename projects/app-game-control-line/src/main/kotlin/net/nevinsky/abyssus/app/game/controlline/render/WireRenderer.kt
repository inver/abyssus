/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.gdx.shader.srgbToLinear
import kotlin.math.min

/** The vertical field of view (degrees) assumed for a camera that is not a perspective one. */
private const val DEFAULT_FIELD_OF_VIEW = 67f

/** The room of the renderer: 6 vertices a segment. */
private const val WIRE_VERTICES = 6 * 4096

/** The corners of a wire segment's two triangles: (0 start / 1 end, side -1 / +1). */
private val QUAD = listOf(
    intArrayOf(0, -1),
    intArrayOf(0, 1),
    intArrayOf(1, 1),
    intArrayOf(0, -1),
    intArrayOf(1, 1),
    intArrayOf(1, -1)
)

/** Draws wires to scale (the [LineSegment]s with a thickness). GL thread only. */
class WireRenderer : Disposable {
    private val immediate = ImmediateModeRenderer20(WIRE_VERTICES, false, true, 0)
    private val along = Vector3()
    private val side = Vector3()
    private val toViewer = Vector3()
    private val toSun = Vector3()
    private val middle = Vector3()
    private val shade = Color()

    /**
     * Draws [wires] seen from [camera]: each segment a quad facing the camera, as wide as the wire is or one pixel,
     * with the opacity of the share of that pixel it covers, and a glint where the sun (shining along [sunDirection])
     * reflects off it ([wireLook], [wireGlint]). Segments beyond the renderer's room are left out. Into a [linear] HDR
     * frame the colours are decoded from sRGB (the final pass encodes them) and the alpha is kept as coverage.
     */
    fun draw(wires: List<LineSegment>, camera: Camera, sunDirection: Vector3, linear: Boolean = false) {
        val fov = (camera as? PerspectiveCamera)?.fieldOfView ?: DEFAULT_FIELD_OF_VIEW
        val height = Gdx.graphics.height.takeIf { it > 0 }?.toFloat() ?: camera.viewportHeight
        toSun.set(sunDirection).scl(-1f).nor()
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        blendForCoverage(linear)
        immediate.begin(camera.combined, GL20.GL_TRIANGLES)
        var room = WIRE_VERTICES
        for (wire in wires) {
            if (room < QUAD.size) {
                break
            }
            middle.set(wire.from).add(wire.to).scl(0.5f)
            toViewer.set(camera.position).sub(middle)
            val distance = toViewer.len()
            if (distance < 1e-3f) {
                continue
            }
            toViewer.scl(1f / distance)
            along.set(wire.to).sub(wire.from)
            if (along.len2() < 1e-12f) {
                continue
            }
            along.nor()
            side.set(along).crs(toViewer)
            if (side.len2() < 1e-8f) {
                continue
            }
            val look = wireLook(wire.thickness, distance, fov, height)
            side.nor().scl(look.width / 2f)
            val glint = wireGlint(along, toSun, toViewer)
            shade.set(wire.color)
            shade.r = min(1f, shade.r + glint)
            shade.g = min(1f, shade.g + glint)
            shade.b = min(1f, shade.b + glint)
            shade.a = min(1f, look.opacity + glint * 0.5f)
            if (linear) {
                decode(shade)
            }
            room -= QUAD.size
            for (corner in QUAD) {
                val end = if (corner[0] == 0) {
                    wire.from
                } else {
                    wire.to
                }
                immediate.color(shade)
                immediate.vertex(end.x + side.x * corner[1], end.y + side.y * corner[1], end.z + side.z * corner[1])
            }
        }
        immediate.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() = immediate.dispose()
}

/**
 * Blending for overlays: straight alpha, and into a [linear] HDR frame an alpha that adds up as coverage (the frame's
 * final pass divides the colour by it).
 */
internal fun blendForCoverage(linear: Boolean) {
    if (linear) {
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
    } else {
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }
}

/** [color] (display colour) decoded to linear light in place, alpha kept: for a linear HDR frame. */
internal fun decode(color: Color): Color =
    color.also {
        it.r = srgbToLinear(it.r)
        it.g = srgbToLinear(it.g)
        it.b = srgbToLinear(it.b)
    }
