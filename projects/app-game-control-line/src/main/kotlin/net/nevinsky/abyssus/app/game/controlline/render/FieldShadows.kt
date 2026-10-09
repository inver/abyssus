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
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Vector4
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Pool
import net.nevinsky.abyssus.lib.gdx.ModelInstance
import net.nevinsky.abyssus.lib.gdx.Renderable
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.lib.gdx.shader.ModelDepthShaderProvider
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasView
import net.nevinsky.abyssus.lib.gdx.shader.ShadowDepthPass
import net.nevinsky.abyssus.lib.gdx.shader.ShadowLightKind
import net.nevinsky.abyssus.lib.gdx.shader.ShadowLightRecord
import kotlin.math.abs

/** Side of the square shadow map, in texels. */
const val SHADOW_MAP_SIZE = 2048

/** Side of the square the sun's shadow covers around the pilot, in metres: the field, the facilities, street and railway. */
const val SHADOW_EXTENT = 160f

/** How far the light camera sits back along the sun, and how deep it sees: tall scenery upwind still casts. */
private const val SHADOW_DEPTH = 400f

/**
 * The sun's orthographic shadow camera: [extent] metres square around [center], looking along the light ([towardSun]
 * reversed). Its centre is snapped to whole texels of a [resolution] map, so a moving centre does not make edges
 * shimmer. No GL.
 */
fun sunShadowCamera(towardSun: Vector3, center: Vector3, extent: Float = SHADOW_EXTENT, resolution: Int = SHADOW_MAP_SIZE): OrthographicCamera {
    val direction = Vector3(towardSun).nor().scl(-1f)
    val up = if (abs(direction.y) > 0.98f) Vector3.Z else Vector3.Y
    val right = Vector3(direction).crs(up).nor()
    val lightUp = Vector3(right).crs(direction).nor()
    val texel = extent / resolution
    val x = Math.round(center.dot(right) / texel) * texel
    val y = Math.round(center.dot(lightUp) / texel) * texel
    val snapped = Vector3(right).scl(x).mulAdd(lightUp, y).mulAdd(direction, center.dot(direction))
    return OrthographicCamera().apply {
        viewportWidth = extent
        viewportHeight = extent
        position.set(snapped).mulAdd(direction, -SHADOW_DEPTH / 2f)
        this.direction.set(direction)
        this.up.set(lightUp)
        near = 1f
        far = SHADOW_DEPTH
        update(false) // the map is drawn whole: no frustum culling, so no frustum
    }
}

/** The depth bias for [camera]'s map: a quarter texel's worth of depth, as the editor's directional shadows use. */
fun sunShadowBias(camera: OrthographicCamera, resolution: Int = SHADOW_MAP_SIZE): Float =
    0.25f * camera.viewportWidth / (camera.far - camera.near) / resolution

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
    private val pass = ShadowDepthPass()
    private val shaders = ModelDepthShaderProvider(pass)
    private val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 1))
    private val pool = object : Pool<Renderable>() {
        override fun newObject() = Renderable()
    }
    private val renderables = Array<Renderable>()
    private var camera = OrthographicCamera()
    private var attribute: ShadowAtlasAttribute? = null
    private var light: DirectionalLight? = null

    val texture: Texture get() = framebuffer.colorBufferTexture

    /** World to light clip space of the current map. */
    val matrix: Matrix4 get() = camera.combined

    val bias: Float get() = sunShadowBias(camera)

    /** Aims the map at [center] for [sun] (whose direction points away from the sun). */
    fun aim(sun: DirectionalLight, towardSun: Vector3, center: Vector3) {
        val aimed = sunShadowCamera(towardSun, center)
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

    /** The attribute that makes the model shaders read this map; set it on the models' environment. */
    fun attribute(): ShadowAtlasAttribute? = attribute

    /** Draws the depth of [models] and [terrains] (mesh to world transform) into the map. */
    fun render(models: List<ModelInstance>, terrains: List<Pair<TerrainMesh, Matrix4>>) {
        pool.freeAll(renderables)
        renderables.clear()
        for (instance in models) instance.getRenderables(renderables, pool)
        for ((mesh, world) in terrains) renderables.add(mesh.depthRenderable(world, pool.obtain()))

        val gl = Gdx.gl
        framebuffer.begin()
        try {
            gl.glDisable(GL20.GL_BLEND)
            gl.glDisable(GL20.GL_DITHER)
            gl.glColorMask(true, true, true, true)
            gl.glClearColor(1f, 1f, 1f, 1f)
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            pass.radialDepth = false
            context.begin()
            try {
                for (renderable in renderables) {
                    val shader = shaders.get(renderable) ?: continue
                    shader.begin(camera, context)
                    try {
                        shader.render(renderable)
                    } finally {
                        shader.end()
                    }
                }
            } finally {
                context.end()
            }
        } finally {
            framebuffer.end()
            gl.glEnable(GL20.GL_DITHER)
        }
    }

    override fun dispose() {
        shaders.dispose()
        framebuffer.dispose()
        pool.freeAll(renderables)
        renderables.clear()
    }
}
