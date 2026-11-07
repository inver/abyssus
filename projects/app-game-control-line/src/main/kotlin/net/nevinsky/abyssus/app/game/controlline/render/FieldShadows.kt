/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.environment.SpotLight
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Vector4
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Pool
import kotlin.math.max
import kotlin.math.tan
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasView
import net.nevinsky.abyssus.lib.gdx.shader.ShadowLightKind
import net.nevinsky.abyssus.lib.gdx.shader.ShadowLightRecord
import net.nevinsky.abyssus.lib.render.shadow.DepthCasters
import net.nevinsky.abyssus.lib.render.shadow.cameraUp
import net.nevinsky.abyssus.lib.render.shadow.sunShadowBias
import net.nevinsky.abyssus.lib.render.shadow.sunShadowCamera

/** Side of the square shadow map, in texels. */
const val SHADOW_MAP_SIZE = 2048

/** The RGBA8888 value of the map's clear colour. */
private const val WHITE = -1

/**
 * Side of the square the sun's shadow covers around the pilot, in metres: the field, the facilities, street and
 * railway.
 */
const val SHADOW_EXTENT = 160f

/**
 * The sun's shadow for the game (design decision "Game shadows"): one [SHADOW_MAP_SIZE] depth map drawn from
 * [sunShadowCamera] with `gdx-model`'s [ModelDepthShaderProvider], read by the models through a [ShadowAtlasAttribute]
 * with a single full-map tile, and by the terrain shader through [bind]. Models and terrain cast and receive; the sky
 * and the control lines do not. GL thread only.
 */
class FieldShadows : Disposable {
    private val framebuffer =
        FrameBuffer(Pixmap.Format.RGBA8888, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, true).apply {
            colorBufferTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            colorBufferTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
        }
    private val depth = DepthCasters()
    private var camera = OrthographicCamera()
    private var attribute: ShadowAtlasAttribute? = null
    private var light: DirectionalLight? = null

    val texture: Texture
        get() = framebuffer.colorBufferTexture

    /** World to light clip space of the current map. */
    val matrix: Matrix4
        get() = camera.combined

    val bias: Float
        get() = sunShadowBias(camera, SHADOW_MAP_SIZE)

    /** Aims the map at [center] for [sun] (whose direction points away from the sun). */
    fun aim(sun: DirectionalLight, towardSun: Vector3, center: Vector3) {
        val aimed = sunShadowCamera(towardSun, center, SHADOW_EXTENT, SHADOW_MAP_SIZE)
        if (light !== sun || attribute == null || !aimed.combined.`val`.contentEquals(camera.combined.`val`)) {
            camera = aimed
            light = sun
            attribute =
                ShadowAtlasAttribute(
                    texture,
                    listOf(
                        ShadowLightRecord(
                            "sun",
                            ShadowLightKind.DIRECTIONAL,
                            sun,
                            listOf(ShadowAtlasView(camera.combined, Vector4(0f, 0f, 1f, 1f))),
                            Vector3(camera.position),
                            1f,
                            bias,
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
                for (y in 0 until SHADOW_MAP_SIZE) for (x in 0 until SHADOW_MAP_SIZE) if (
                    pixmap.getPixel(x, y) != WHITE
                ) {
                    count++
                }
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
     * How many times [render] drew the sun's map: a scene without a sun never does (for tests; the interior map's are
     * [spotTiles] and [pointBakes]).
     */
    internal var renders = 0
        private set

    /** How many depth shaders the last [render] drew its casters with (each bound once). */
    val depthShaders: Int
        get() = depth.lastShaderGroups

    /**
     * Draws the depth of [models], [terrains] (mesh to world transform) and the extra casters [extra] adds (foliage
     * OBJECT copies) into the map.
     */
    fun render(
        models: List<ModelInstance>,
        terrains: List<Pair<TerrainMesh, Matrix4>>,
        extra: (Array<Renderable>, Pool<Renderable>) -> Unit = { _, _ -> },
    ) {
        renders++
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

    /** How many spot lights the last [renderInterior] drew a tile for (for tests). */
    internal var spotTiles = 0
        private set

    /** How many point lights have their cube baked into the interior map (for tests). */
    internal var pointShadows = 0
        private set

    /** How many times the point lights' cubes were baked (for tests): once per scene. */
    internal var pointBakes = 0
        private set

    // a scene without a sun has its own, larger map: spot tiles that are drawn every frame and point light cubes that
    // are
    // drawn once
    private var interior: FrameBuffer? = null
    private var baked: List<Pair<PointCaster, List<PerspectiveCamera>>> = emptyList()

    /** The interior map, made on first use. */
    private fun interiorMap(): FrameBuffer =
        interior
            ?: FrameBuffer(Pixmap.Format.RGBA8888, INTERIOR_MAP_SIZE, INTERIOR_MAP_SIZE, true).apply {
                colorBufferTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                colorBufferTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
                interior = this
            }

    /**
     * For a scene without a sun (OpenSpec `control-line-pbr-rendering`, "Lights cast shadows in a scene without a
     * sun"): its [spots] (at most [SPOT_SHADOW_TILES], in the order given) cast shadows from [models], drawn every
     * frame into the interior map's spot tiles, and its [points] (at most [POINT_SHADOW_LIGHTS]) cast shadows from the
     * static [staticModels], their cube faces drawn once and kept until [forgetPoints] or other point lights are asked
     * for. A light is known to the shaders by its libGDX object, the one the models are lit with. No light: no
     * attribute.
     */
    fun renderInterior(
        spots: List<SpotCaster>,
        models: List<ModelInstance>,
        points: List<PointCaster>,
        staticModels: () -> List<ModelInstance>?,
    ) {
        light = null
        val drawn = spots.take(SPOT_SHADOW_TILES).mapNotNull { spot -> spotCamera(spot)?.let { spot to it } }
        spotTiles = drawn.size
        val wanted = points.take(POINT_SHADOW_LIGHTS)
        if (wanted.map { it.id } != baked.map { it.first.id }) {
            staticModels()?.let { bakePoints(wanted, it) }
        }
        pointShadows = baked.size
        if (drawn.isEmpty() && baked.isEmpty()) {
            attribute = null
            return
        }
        val map = interiorMap()
        if (drawn.isNotEmpty()) {
            depth.begin()
            for (instance in models) instance.getRenderables(depth.renderables, depth.pool)
            drawTiles(map, drawn.indices.map { spotTile(it) }, false) { i ->
                depth.pass.radialDepth = false
                depth.submit(drawn[i].second)
            }
        }
        val records = ArrayList<ShadowLightRecord>()
        drawn.forEachIndexed { i, (spot, view) ->
            records +=
                ShadowLightRecord(
                    spot.id,
                    ShadowLightKind.SPOT,
                    spot.light,
                    listOf(ShadowAtlasView(view.combined.cpy(), uvOf(spotTile(i)))),
                    Vector3(spot.position),
                    spot.range,
                    // as the editor's spot shadows: a slope-free bias that follows the cone's width
                    0.00005f * tan(Math.toRadians(view.fieldOfView / 2.0)).toFloat(),
                )
        }
        baked.forEachIndexed { n, (point, faces) ->
            records +=
                ShadowLightRecord(
                    point.id,
                    ShadowLightKind.POINT,
                    point.light,
                    faces.mapIndexed { f, face -> ShadowAtlasView(face.combined.cpy(), uvOf(pointTile(n * 6 + f))) },
                    Vector3(point.position),
                    point.range,
                    // as the editor's point shadows
                    0.5f / POINT_TILE_SIZE,
                )
        }
        attribute = ShadowAtlasAttribute(map.colorBufferTexture, records)
    }

    /** Drops the baked point light cubes: the next [renderInterior] bakes them again (another scene). */
    fun forgetPoints() {
        baked = emptyList()
        pointShadows = 0
    }

    /** Draws the six faces of each of [points] from [statics] into the point tiles (radial depth over the range). */
    private fun bakePoints(points: List<PointCaster>, statics: List<ModelInstance>) {
        val cubes = points.map { point -> point to cubeFaces(point) }
        pointBakes++
        val map = interiorMap()
        depth.begin()
        for (instance in statics) instance.getRenderables(depth.renderables, depth.pool)
        val tiles = cubes.indices.flatMap { n -> (0 until 6).map { pointTile(n * 6 + it) } }
        drawTiles(map, tiles, true) { i ->
            val (point, faces) = cubes[i / 6]
            depth.pass.radialDepth = true
            depth.pass.lightPosition.set(point.position)
            depth.pass.far = point.range
            depth.submit(faces[i % 6])
        }
        depth.pass.radialDepth = false
        baked = cubes
    }

    /**
     * Clears each of [tiles] (x, y, size in texels of [map]) to the far depth and draws [draw] of its index into it;
     * [all] clears the point tiles' whole region first (a bake), else only the tiles drawn.
     */
    private fun drawTiles(map: FrameBuffer, tiles: List<IntArray>, all: Boolean, draw: (Int) -> Unit) {
        val gl = Gdx.gl
        map.begin()
        try {
            gl.glDisable(GL20.GL_BLEND)
            gl.glDisable(GL20.GL_DITHER)
            gl.glColorMask(true, true, true, true)
            gl.glClearColor(1f, 1f, 1f, 1f)
            gl.glEnable(GL20.GL_SCISSOR_TEST)
            if (all) {
                // the point region: the right half and the bottom-left quarter of the map
                val half = INTERIOR_MAP_SIZE / 2
                val regions = listOf(intArrayOf(half, 0, half, INTERIOR_MAP_SIZE), intArrayOf(0, half, half, half))
                for ((x, y, w, h) in regions) {
                    gl.glScissor(x, y, w, h)
                    gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                }
            }
            tiles.forEachIndexed { i, (x, y, size) ->
                gl.glViewport(x, y, size, size)
                gl.glScissor(x, y, size, size)
                if (!all) {
                    gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                }
                draw(i)
            }
        } finally {
            gl.glDisable(GL20.GL_SCISSOR_TEST)
            map.end()
            gl.glEnable(GL20.GL_DITHER)
        }
    }

    /** The six 90 degree views of [point], in the order the shaders pick a face (+X, -X, +Y, -Y, +Z, -Z). */
    private fun cubeFaces(point: PointCaster): List<PerspectiveCamera> =
        CUBE_FACES.map { (direction, up) ->
            PerspectiveCamera(90f, 1f, 1f).apply {
                position.set(point.position)
                this.direction.set(direction)
                this.up.set(up)
                near = max(0.02f, point.range / 10000f).coerceAtMost(point.range / 2f)
                far = point.range
                update(true)
            }
        }

    /** The perspective view of [spot]'s cone, or null for a spot that cannot have one. */
    private fun spotCamera(spot: SpotCaster): PerspectiveCamera? {
        if (spot.coneAngle <= 0f || spot.coneAngle >= 180f || spot.range <= 0f || spot.direction.isZero) {
            return null
        }
        return PerspectiveCamera(spot.coneAngle, 1f, 1f).apply {
            position.set(spot.position)
            direction.set(spot.direction).nor()
            up.set(cameraUp(direction))
            near = max(0.02f, spot.range / 10000f).coerceAtMost(spot.range / 2f)
            far = spot.range
            update(true)
        }
    }

    override fun dispose() {
        depth.dispose()
        framebuffer.dispose()
        interior?.dispose()
        interior = null
    }
}

/** How many spot lights can cast shadows at once, in a scene without a sun. */
const val SPOT_SHADOW_TILES = 4

/** How many point lights can cast shadows at once, in a scene without a sun (each a cube of six tiles). */
const val POINT_SHADOW_LIGHTS = 8

/** Side of the interior map (a scene without a sun), in texels: 4 spot tiles and 48 point tiles. */
const val INTERIOR_MAP_SIZE = 4096

/** Side of a spot light's tile in the interior map. */
private const val SPOT_TILE_SIZE = 1024

/** Side of a point light cube face's tile in the interior map. */
private const val POINT_TILE_SIZE = 512

/** Spot tile [i] (x, y, size): the four 1024 tiles of the map's top-left quarter. */
private fun spotTile(i: Int) = intArrayOf(i % 2 * SPOT_TILE_SIZE, i / 2 * SPOT_TILE_SIZE, SPOT_TILE_SIZE)

/** Point tile [i] (x, y, size): 32 in the map's right half, then 16 in its bottom-left quarter, 512 each. */
private fun pointTile(i: Int): IntArray {
    val half = INTERIOR_MAP_SIZE / 2
    val perRow = half / POINT_TILE_SIZE
    return if (i < perRow * (INTERIOR_MAP_SIZE / POINT_TILE_SIZE)) {
        intArrayOf(half + i % perRow * POINT_TILE_SIZE, i / perRow * POINT_TILE_SIZE, POINT_TILE_SIZE)
    } else {
        val j = i - perRow * (INTERIOR_MAP_SIZE / POINT_TILE_SIZE)
        intArrayOf(j % perRow * POINT_TILE_SIZE, half + j / perRow * POINT_TILE_SIZE, POINT_TILE_SIZE)
    }
}

/** A tile's place in the map as the shaders take it: x, y, width, height in 0..1. */
private fun uvOf(tile: IntArray): Vector4 {
    val m = INTERIOR_MAP_SIZE.toFloat()
    return Vector4(tile[0] / m, tile[1] / m, tile[2] / m, tile[2] / m)
}

/** The faces of a point light's cube (direction, up), in GL's face order, as the editor draws them. */
private val CUBE_FACES =
    listOf(
        Vector3(1f, 0f, 0f) to Vector3(0f, -1f, 0f),
        Vector3(-1f, 0f, 0f) to Vector3(0f, -1f, 0f),
        Vector3(0f, 1f, 0f) to Vector3(0f, 0f, 1f),
        Vector3(0f, -1f, 0f) to Vector3(0f, 0f, -1f),
        Vector3(0f, 0f, 1f) to Vector3(0f, -1f, 0f),
        Vector3(0f, 0f, -1f) to Vector3(0f, -1f, 0f),
    )

/**
 * A point light that casts a shadow: [light] is the object the model shaders are lit with (the atlas finds a light's
 * tiles by it), [range] how far its cube reaches.
 */
class PointCaster(val id: String, val light: PointLight, val position: Vector3, val range: Float)

/**
 * A spot light that casts a shadow: [light] is the object the model shaders are lit with (the atlas finds a light's
 * tile by it), the rest its cone.
 */
class SpotCaster(
    val id: String,
    val light: SpotLight,
    val position: Vector3,
    val direction: Vector3,
    val coneAngle: Float,
    val range: Float,
)
