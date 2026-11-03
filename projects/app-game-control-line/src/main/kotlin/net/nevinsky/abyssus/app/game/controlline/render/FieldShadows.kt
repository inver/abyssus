/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Vector4
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Pool
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.Renderable
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasView
import net.nevinsky.abyssus.lib.gdx.shader.ShadowLightKind
import net.nevinsky.abyssus.lib.gdx.shader.ShadowLightRecord
import net.nevinsky.abyssus.lib.render.shadow.DepthCasters
import net.nevinsky.abyssus.lib.render.shadow.sunShadowBias
import net.nevinsky.abyssus.lib.render.shadow.sunShadowCamera

/** Side of the square shadow map, in texels. */
const val SHADOW_MAP_SIZE = 2048

/** The RGBA8888 value of the map's clear colour. */
private const val WHITE = -1

/** Side of the square the sun's shadow covers around the pilot, in metres: the field, the facilities, street and railway. */
const val SHADOW_EXTENT = 160f

/**
 * The sun's shadow for the game (design decision "Game shadows"): one [SHADOW_MAP_SIZE] depth map drawn from
 * [sunShadowCamera] with `gdx-model`'s [ModelDepthShaderProvider], read by the models through a
 * [ShadowAtlasAttribute] with a single full-map tile, and by the terrain shader through [bind]. Models and terrain cast
 * and receive; the sky and the control lines do not. GL thread only.
 */
class FieldShadows : Disposable {
    private val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, true).apply {
        colorBufferTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        colorBufferTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
    }
    private val depth = DepthCasters()
    private var camera = OrthographicCamera()
    private var attribute: ShadowAtlasAttribute? = null
    private var light: DirectionalLight? = null

    val texture: Texture get() = framebuffer.colorBufferTexture

    /** World to light clip space of the current map. */
    val matrix: Matrix4 get() = camera.combined

    val bias: Float get() = sunShadowBias(camera, SHADOW_MAP_SIZE)

    /** Aims the map at [center] for [sun] (whose direction points away from the sun). */
    fun aim(sun: DirectionalLight, towardSun: Vector3, center: Vector3) {
        val aimed = sunShadowCamera(towardSun, center, SHADOW_EXTENT, SHADOW_MAP_SIZE)
        if (light !== sun || attribute == null || !aimed.combined.`val`.contentEquals(camera.combined.`val`)) {
            camera = aimed
            light = sun
            attribute = ShadowAtlasAttribute(
                texture,
                listOf(
                    ShadowLightRecord(
                        "sun", ShadowLightKind.DIRECTIONAL, sun, listOf(ShadowAtlasView(camera.combined, Vector4(0f, 0f, 1f, 1f))),
                        Vector3(camera.position), 1f, bias,
                    )
                ),
            )
        }
    }

    /**
     * How many texels of the last [render] hold something that was drawn (the map is cleared to white, the far depth).
     * Reads the map back, so only tests call it.
     */
    internal fun castTexels(): Int {
        framebuffer.begin()
        try {
            val pixmap = Pixmap.createFromFrameBuffer(0, 0, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE)
            try {
                var count = 0
                for (y in 0 until SHADOW_MAP_SIZE) for (x in 0 until SHADOW_MAP_SIZE) if (pixmap.getPixel(x, y) != WHITE) count++
                return count
            } finally {
                pixmap.dispose()
            }
        } finally {
            framebuffer.end()
        }
    }

    /** The attribute that makes the model shaders read this map; set it on the models' environment. */
    fun attribute(): ShadowAtlasAttribute? = attribute

    /**
     * The map's camera with its frustum current: the volume a foliage drawable culls its casters against, so a tree
     * outside the viewer's frustum that shades the map is still kept. Valid after [aim].
     */
    fun casterCamera(): OrthographicCamera = camera.also { it.update(true) }

    /**
     * Draws the depth of [models], [terrains] (mesh to world transform) and the extra casters [extra] adds (foliage
     * OBJECT copies) into the map.
     */
    fun render(
        models: List<ModelInstance>,
        terrains: List<Pair<TerrainMesh, Matrix4>>,
        extra: (Array<Renderable>, Pool<Renderable>) -> Unit = { _, _ -> },
    ) {
        depth.begin()
        for (instance in models) instance.getRenderables(depth.renderables, depth.pool)
        for ((mesh, world) in terrains) depth.renderables.add(mesh.depthRenderable(world, depth.pool.obtain()))
        extra(depth.renderables, depth.pool)

        val gl = Gdx.gl
        framebuffer.begin()
        try {
            gl.glDisable(GL20.GL_BLEND)
            gl.glDisable(GL20.GL_DITHER)
            gl.glColorMask(true, true, true, true)
            gl.glClearColor(1f, 1f, 1f, 1f)
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            depth.pass.radialDepth = false
            depth.submit(camera)
        } finally {
            framebuffer.end()
            gl.glEnable(GL20.GL_DITHER)
        }
    }

    override fun dispose() {
        depth.dispose()
        framebuffer.dispose()
    }
}
