/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import net.nevinsky.abyssus.lib.core.util.WATER_DIRECTIONAL_CAPACITY

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.util.newSolidTexture
import net.nevinsky.abyssus.lib.core.util.newSolidCubemap
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage

/** The classpath folder of the water shaders, for [ShaderStorage.withResources]. */
const val WATER_SHADER_ROOT = "/shader/water"

/** [this] that also finds the packaged water shaders. */
fun ShaderStorage.withWaterShaders(): ShaderStorage = withResources(WATER_SHADER_ROOT, WaterDrawable::class.java)

/**
 * The frame a water surface is drawn into: the [camera] and viewport the scene copy and depth targets were made for,
 * the animation time, and the scene's light, fog and sky colors. Arrays are `r, g, b` triples; the directional lights
 * are [directionalCount] `(x, y, z)` directions the light travels along and their colors.
 */
class WaterFrame(
    val camera: Camera,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val seconds: Double,
    val ambient: FloatArray,
    val skyColor: FloatArray,
    val fogColor: FloatArray,
    val fogK: Float,
    val directionalCount: Int,
    val directionalDirections: FloatArray,
    val directionalColors: FloatArray,
)

/**
 * What a surface samples: a copy of the opaque scene ([sceneColor]), the packed linear depth of everything opaque
 * ([sceneDepth]) and of terrain alone ([terrainDepth]), and the planar [reflection] of its level. Without the first
 * three the water is plain tinted water; without a reflection it shows the frame's sky color.
 */
class WaterTextures(
    val sceneColor: GLTexture?,
    val sceneDepth: GLTexture?,
    val terrainDepth: GLTexture?,
    val reflection: GLTexture?,
    /** The sky's lighting cube, shown where the level has no planar reflection; null: the frame's sky colour. */
    val skyCube: GLTexture? = null,
)

/**
 * Draws water surfaces: a bounded grid per surface (see [WaterMeshPlan]) lifted by the waves in the vertex shader and
 * shaded by `water.frag`. Meshes are kept for the surfaces seen lately and dropped when many pile up. Needs the GL
 * context current for every call; nothing here is shared between contexts.
 */
class WaterDrawable(shaders: ShaderStorage, private val planner: WaterMeshPlanner = WaterMeshPlanner()) : Disposable {
    private val program = shaders.withWaterShaders().program("water")
    private val blank = newSolidTexture()
    /** Bound to the sky cube's unit when there is no sky cube, so no cube sampler is left without a cube. */
    private val blankCube = newSolidCubemap()
    private val meshes = object : LinkedHashMap<String, Mesh>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Mesh>): Boolean {
            val drop = size > MAX_MESHES
            if (drop) eldest.value.dispose()
            return drop
        }
    }
    private val waveCache = HashMap<Triple<Double, Double, Double>, WaterWaves>()

    /** How many meshes are alive, for tests. */
    val meshCount: Int get() = meshes.size

    /** The waves of a surface's settings, shared between frames. */
    fun wavesOf(surface: WaterSettings): WaterWaves {
        if (waveCache.size > MAX_MESHES * 4) waveCache.clear()
        return waveCache.getOrPut(Triple(surface.waveAmplitude, surface.waveLength, surface.waveSpeed)) {
            WaterWaves(surface.waveAmplitude, surface.waveLength, surface.waveSpeed)
        }
    }

    fun draw(surface: WaterSettings, frame: WaterFrame, textures: WaterTextures) {
        val camera = frame.camera
        val waves = wavesOf(surface)
        val plan = planner.plan(surface.width, surface.length, surface.waveLength)
        val mesh = meshes.getOrPut("${plan.cellsX}x${plan.cellsZ}:${surface.width}:${surface.length}") {
            Mesh(true, plan.vertexCount, plan.indexCount, VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position")).also {
                it.setVertices(plan.vertices())
                it.setIndices(plan.indices())
            }
        }
        val hasDepth = textures.sceneColor != null && textures.sceneDepth != null && textures.terrainDepth != null
        program.bind()
        bind(0, textures.sceneColor)
        bind(1, textures.sceneDepth)
        bind(2, textures.terrainDepth)
        bind(3, textures.reflection)
        (textures.skyCube ?: blankCube).bind(4)
        // an unused uniform may be optimised out of the program: setting it is then skipped, never an error
        seti("u_sceneColor", 0)
        seti("u_sceneDepth", 1)
        seti("u_terrainDepth", 2)
        seti("u_reflection", 3)
        seti("u_skyCube", 4)
        seti("u_hasSkyCube", if (textures.skyCube != null) 1 else 0)
        seti("u_hasReflection", if (textures.reflection != null) 1 else 0)
        seti("u_hasDepth", if (hasDepth) 1 else 0)
        matrix("u_projViewTrans", camera.combined)
        matrix("u_invProjView", camera.invProjectionView)
        setf("u_cameraPos", camera.position.x, camera.position.y, camera.position.z)
        setf("u_cameraForward", camera.direction.x, camera.direction.y, camera.direction.z)
        setf("u_viewport", frame.viewportWidth.toFloat(), frame.viewportHeight.toFloat())
        setf("u_far", camera.far)
        setf("u_origin", surface.x.toFloat(), surface.level.toFloat(), surface.z.toFloat())
        array4("u_waves", waves.uniform())
        val phases = waves.phases(frame.seconds)
        setf("u_phases", phases[0], phases[1], phases[2], phases[3])
        setf("u_cell", plan.cellSize.toFloat())
        setf("u_calmRange", (surface.waveAmplitude * 2.0 + 0.02).toFloat())  // the waves die out over this much terrain depth
        setf("u_time", (frame.seconds % TIME_PERIOD).toFloat())
        setf("u_level", surface.level.toFloat())
        setf("u_amplitude", surface.waveAmplitude.toFloat())
        setf("u_tint", surface.tintR.toFloat(), surface.tintG.toFloat(), surface.tintB.toFloat())
        setf("u_clarity", surface.clarity.toFloat())
        setf("u_foamAmount", surface.foamAmount.toFloat())
        setf("u_foamWidth", surface.foamWidth.toFloat())
        setf("u_ambient", frame.ambient[0], frame.ambient[1], frame.ambient[2])
        setf("u_skyColor", frame.skyColor[0], frame.skyColor[1], frame.skyColor[2])
        seti("u_numDirectional", frame.directionalCount)
        array3("u_dirDirection", frame.directionalDirections)
        array3("u_dirColor", frame.directionalColors)
        setf("u_fogColor", frame.fogColor[0], frame.fogColor[1], frame.fogColor[2])
        setf("u_fogK", frame.fogK)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        // The window depth buffer is coarse far from the camera: bias the surface toward it so the buffer does not cut the
        // shoreline raggedly; the fragment shader's exact depth test then decides what the scene hides.
        Gdx.gl.glEnable(GL20.GL_POLYGON_OFFSET_FILL)
        Gdx.gl.glPolygonOffset(-1f, -4f)
        try {
            mesh.render(program, GL20.GL_TRIANGLES)
        } finally {
            Gdx.gl.glDisable(GL20.GL_POLYGON_OFFSET_FILL)
        }
    }

    private fun location(name: String): Int = program.fetchUniformLocation(name, false)

    private fun seti(name: String, value: Int) = location(name).takeIf { it >= 0 }?.let { program.setUniformi(it, value) }

    private fun setf(name: String, a: Float) = location(name).takeIf { it >= 0 }?.let { program.setUniformf(it, a) }

    private fun setf(name: String, a: Float, b: Float) = location(name).takeIf { it >= 0 }?.let { program.setUniformf(it, a, b) }

    private fun setf(name: String, a: Float, b: Float, c: Float) = location(name).takeIf { it >= 0 }?.let { program.setUniformf(it, a, b, c) }

    private fun setf(name: String, a: Float, b: Float, c: Float, d: Float) =
        location(name).takeIf { it >= 0 }?.let { program.setUniformf(it, a, b, c, d) }

    private fun matrix(name: String, value: com.badlogic.gdx.math.Matrix4) = location(name).takeIf { it >= 0 }?.let { program.setUniformMatrix(it, value) }

    private fun array3(name: String, values: FloatArray) = location(name).takeIf { it >= 0 }?.let { program.setUniform3fv(it, values, 0, values.size) }

    private fun array4(name: String, values: FloatArray) = location(name).takeIf { it >= 0 }?.let { program.setUniform4fv(it, values, 0, values.size) }

    private fun bind(unit: Int, texture: GLTexture?) {
        (texture ?: blank).bind(unit)
    }

    /** The context these were made in is gone: forget them without GL calls. */
    fun abandon() {
        meshes.clear()
        waveCache.clear()
    }

    override fun dispose() {
        meshes.values.forEach(Mesh::dispose)
        meshes.clear()
        program.dispose()
        blank.dispose()
        blankCube.dispose()
    }
}

/** The most directional lights the water shader reads. */
const val MAX_DIRECTIONAL = WATER_DIRECTIONAL_CAPACITY

private const val MAX_MESHES = 16

/** The animation time is reduced to this period for the shader's float precision. */
private const val TIME_PERIOD = 4096.0
