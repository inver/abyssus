/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.hdr

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.util.GeometryUtils.Companion.createFullscreenTriangle
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Build steps of an HDR environment, one per frame. */
const val HDR_BUILD_STEPS = 9

/** Face size of the specular cube's level 0. */
const val SPECULAR_SIZE = 256

/** Mip levels of the specular cube (256 down to 8); level n is prefiltered for roughness n / (levels - 1). */
const val SPECULAR_LEVELS = 6

/** Face size of the irradiance cube. */
const val IRRADIANCE_SIZE = 32

private const val SOURCE_LEVELS = 9 // 256 down to 1
private const val GL_RGB16F = 0x881B
private const val GL_HALF_FLOAT = 0x140B
private const val GL_TEXTURE_CUBE_MAP_SEAMLESS = 0x884F

/** A texture made with raw GL calls, so libGDX's texture binder can bind it like any other. */
class GpuTexture(target: Int, private val width: Int, private val height: Int) :
    GLTexture(target, Gdx.gl.glGenTexture()) {
    override fun getWidth() = width
    override fun getHeight() = height
    override fun getDepth() = 0
    override fun isManaged() = false
    override fun reload() = Unit
}

/**
 * What a sky lights a scene with: the specular cube ([levels] mips, level n prefiltered for GGX roughness
 * n / (levels - 1)), the irradiance cube, and the irradiance in the six axis directions as [ambient] (+X, -X, +Y, -Y, +Z,
 * -Z, three floats each). The holder of the lighting disposes both cubes once.
 */
class SkyLighting(
    val specular: GpuTexture,
    val irradiance: GpuTexture,
    val levels: Int,
    val ambient: FloatArray,
) : Disposable {
    override fun dispose() {
        specular.dispose()
        irradiance.dispose()
    }
}

/**
 * The lighting a frame draws with: the [current] environment, and while it is being replaced the [next] one with the
 * share [weight] (0 to 1) of the way to it. Both stay owned by whoever made them.
 */
class SkyLightingState(val current: SkyLighting, val next: SkyLighting? = null, val weight: Float = 0f) {
    /** The six axis irradiance colours of the light now: the current ones, mixed toward the next ones by [weight] during a fade. */
    fun blendedAmbient(): FloatArray {
        val to = next?.ambient ?: return current.ambient.copyOf()
        return FloatArray(18) { current.ambient[it] + (to[it] - current.ambient[it]) * weight }
    }
}

/**
 * The environment an HDR sky lights the scene with: the equirectangular image its background samples, and its [lighting].
 */
class HdrEnvironment(val equirect: GpuTexture, val lighting: SkyLighting) : Disposable {
    constructor(
        equirect: GpuTexture,
        specular: GpuTexture,
        irradiance: GpuTexture,
        levels: Int,
        ambient: FloatArray,
    ) : this(equirect, SkyLighting(specular, irradiance, levels, ambient))

    val specular: GpuTexture get() = lighting.specular
    val irradiance: GpuTexture get() = lighting.irradiance
    val levels: Int get() = lighting.levels
    val ambient: FloatArray get() = lighting.ambient

    /** The lighting alone, as a frame draws with it. */
    val state: SkyLightingState = SkyLightingState(lighting)

    override fun dispose() {
        equirect.dispose()
        lighting.dispose()
    }
}

/** Where an environment build starts from. */
sealed interface EnvironmentSource

/** A decoded equirectangular HDR image; the build uploads it and owns that upload. */
class EquirectSource(val image: HdrImage) : EnvironmentSource

/**
 * A cube of linear radiance, [size] pixels a face with [levels] mip levels already filled, that the build only reads: the
 * caller keeps owning [cube] and disposes it, whether the build finishes, fails or is disposed.
 */
class CubeSource(val cube: GpuTexture, val size: Int, val levels: Int) : EnvironmentSource

/**
 * Builds an environment from an [EnvironmentSource] in [HDR_BUILD_STEPS] steps, one per [step] call, on the GL thread inside
 * `GdxRuntime.withContext`. Each step renders into its own framebuffer and restores the framebuffer, viewport and
 * state it found. Throws when the GPU lacks OpenGL 3 or a renderable 16-bit float framebuffer. [finish] (an equirect source)
 * or [finishLighting] (either) hands the results over; [dispose] releases whatever the build still owns, never a borrowed
 * [CubeSource] cube.
 */
class HdrEnvironmentBuild(private val input: EnvironmentSource, private val shaders: ShaderStorage) : Disposable {
    constructor(image: HdrImage, shaders: ShaderStorage) : this(EquirectSource(image), shaders)

    private val targets = CubeTargets()
    private var done = 0
    private var equirect: GpuTexture? = null
    private var source: GpuTexture? = null
    private var sourceOwned = false
    private var sourceSize = SPECULAR_SIZE
    private var sourceLevels = SOURCE_LEVELS
    private var specular: GpuTexture? = null
    private var irradiance: GpuTexture? = null
    private var project: ShaderProgram? = null
    private var prefilter: ShaderProgram? = null
    private var convolve: ShaderProgram? = null
    private var mesh: Mesh? = null
    private val ambient = FloatArray(18)

    /** Does the next step; true when the environment is complete. */
    fun step(): Boolean {
        when (done) {
            0 -> prepare()
            1 -> projectToCube()
            in 2..6 -> prefilterLevel(done - 1)
            7 -> convolveIrradiance()
            8 -> readAmbient()
        }
        done++
        return done >= HDR_BUILD_STEPS
    }

    /** Hands over the finished HDR environment (an equirect source) and releases everything else. */
    fun finish(): HdrEnvironment {
        check(equirect != null) { "Only an equirect source makes an HDR environment" }
        val lighting = finishLighting()
        val result = HdrEnvironment(equirect!!, lighting)
        equirect = null
        dispose()
        return result
    }

    /** Hands over the finished lighting cubes and releases what the build owns besides them. */
    fun finishLighting(): SkyLighting {
        check(done >= HDR_BUILD_STEPS) { "HDR environment is not built yet" }
        val result = SkyLighting(specular!!, irradiance!!, SPECULAR_LEVELS, ambient.copyOf())
        specular = null
        irradiance = null
        if (equirect == null) dispose()
        return result
    }

    private fun prepare() {
        checkNotNull(Gdx.gl30) { "HDR skies need OpenGL 3" }
        Gdx.gl.glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
        when (input) {
            is EquirectSource -> uploadImage(input.image)
            is CubeSource -> {
                source = input.cube
                sourceOwned = false
                sourceSize = input.size
                sourceLevels = input.levels
            }
        }
    }

    private fun uploadImage(image: HdrImage) {
        val gl = Gdx.gl
        val texture = GpuTexture(GL20.GL_TEXTURE_2D, image.width, image.height).also { equirect = it }
        val data = ByteBuffer.allocateDirect(image.rgb.size * 2).order(ByteOrder.nativeOrder())
        data.asShortBuffer().put(image.rgb)
        texture.bind()
        gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 1)
        gl.glTexImage2D(
            GL20.GL_TEXTURE_2D,
            0,
            GL_RGB16F,
            image.width,
            image.height,
            0,
            GL20.GL_RGB,
            GL_HALF_FLOAT,
            data
        )
        gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 4)
        // no mipmaps: atan2 jumps at the image's left and right edge, and mip selection there would draw a seam
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR)
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR)
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_REPEAT)
        gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE)
        gl.glBindTexture(GL20.GL_TEXTURE_2D, 0)
    }

    private fun projectToCube() {
        specular = targets.cube(SPECULAR_SIZE, SPECULAR_LEVELS)
        irradiance = targets.cube(IRRADIANCE_SIZE, 1)
        when (input) {
            is EquirectSource -> {
                val src = targets.cube(SPECULAR_SIZE, SOURCE_LEVELS).also {
                    source = it
                    sourceOwned = true
                }
                val program = program("hdr_project").also { project = it }
                render(src, 0, SPECULAR_SIZE, program) {
                    equirect!!.bind(0)
                    program.setUniformi("u_equirect", 0)
                    program.setUniformf("u_texel", 2f / SPECULAR_SIZE)
                }
                targets.generateMipmaps(src)
                // level 0 of the specular cube is the projection itself (roughness 0)
                prefilterLevel(0)
            }

            is CubeSource -> {
                val program = program("hdr_cubecopy").also { project = it }
                render(specular!!, 0, SPECULAR_SIZE, program) {
                    source!!.bind(0)
                    program.setUniformi("u_source", 0)
                }
            }
        }
    }

    private fun prefilterLevel(level: Int) {
        val program = prefilter ?: program("hdr_prefilter").also { prefilter = it }
        render(specular!!, level, SPECULAR_SIZE shr level, program) {
            source!!.bind(0)
            program.setUniformi("u_source", 0)
            program.setUniformf("u_roughness", level / (SPECULAR_LEVELS - 1f))
            program.setUniformf("u_sourceSize", sourceSize.toFloat())
            program.setUniformf("u_maxLod", sourceLevels - 1f)
        }
    }

    private fun convolveIrradiance() {
        val program = program("hdr_irradiance").also { convolve = it }
        render(irradiance!!, 0, IRRADIANCE_SIZE, program) {
            source!!.bind(0)
            program.setUniformi("u_source", 0)
            program.setUniformf("u_sourceSize", sourceSize.toFloat())
            program.setUniformf("u_maxLod", sourceLevels - 1f)
        }
        releaseSource()
    }

    private fun releaseSource() {
        if (sourceOwned) source?.dispose()
        source = null
    }

    private fun readAmbient() {
        val pixel = BufferUtils.newFloatBuffer(4)
        targets.withFramebuffer {
            for (face in 0 until 6) {
                targets.attach(irradiance!!, face, 0)
                pixel.clear()
                Gdx.gl.glReadPixels(IRRADIANCE_SIZE / 2, IRRADIANCE_SIZE / 2, 1, 1, GL20.GL_RGBA, GL20.GL_FLOAT, pixel)
                for (c in 0 until 3) ambient[face * 3 + c] = pixel.get(c)
            }
        }
    }

    private fun program(fragment: String) = shaders.program("hdr_cube.vert", "hdr_common.glsl", "$fragment.frag")

    /** Draws [program] once per face into [level] of [target], [size] pixels square; [bind] sets the pass's inputs. */
    private fun render(target: GpuTexture, level: Int, size: Int, program: ShaderProgram, bind: () -> Unit) {
        val quad = mesh ?: createFullscreenTriangle().also { mesh = it }
        targets.withFramebuffer {
            Gdx.gl.glViewport(0, 0, size, size)
            program.bind()
            bind()
            for (face in 0 until 6) {
                targets.attach(target, face, level)
                program.setUniformi("u_face", face)
                quad.render(program, GL20.GL_TRIANGLES)
            }
        }
    }

    override fun dispose() {
        listOfNotNull(
            equirect,
            specular,
            irradiance,
            project,
            prefilter,
            convolve,
            mesh
        ).forEach(Disposable::dispose)
        releaseSource()
        equirect = null
        specular = null
        irradiance = null
        project = null
        prefilter = null
        convolve = null
        mesh = null
        targets.dispose()
    }
}
