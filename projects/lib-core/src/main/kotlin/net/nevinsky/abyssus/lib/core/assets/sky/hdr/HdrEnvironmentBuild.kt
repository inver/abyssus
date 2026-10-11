/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.hdr

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
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
private const val GL_FRAMEBUFFER_BINDING = 0x8CA6

/** A texture made with raw GL calls, so libGDX's texture binder can bind it like any other. */
class GpuTexture(target: Int, private val width: Int, private val height: Int) :
    GLTexture(target, Gdx.gl.glGenTexture()) {
    override fun getWidth() = width
    override fun getHeight() = height
    override fun getDepth() = 0
    override fun isManaged() = false
    override fun reload() = Unit
}

/** Linear lighting only; owns its specular and irradiance cubes, never a background or source texture. */
class HdrEnvironment(
    val specular: GpuTexture,
    val irradiance: GpuTexture,
    val levels: Int,
    val ambient: FloatArray,
) : Disposable {
    private var disposed = false

    override fun dispose() {
        if (disposed) return
        disposed = true
        specular.dispose()
        irradiance.dispose()
    }
}

/** HDR background and lighting have separate ownership; the sky owns both after a successful transfer. */
class HdrSkyEnvironment(val background: GpuTexture, val lighting: HdrEnvironment) : Disposable {
    private var disposed = false

    override fun dispose() {
        if (disposed) return
        disposed = true
        background.dispose()
        lighting.dispose()
    }
}

sealed interface EnvironmentSource

class EquirectSource(val image: HdrImage) : EnvironmentSource

/** A completed linear cube and its allocated mip count. Borrowed until the build completes or is disposed. */
class CubeSource(val texture: GpuTexture, val levels: Int) : EnvironmentSource {
    init {
        require(texture.glTarget == GL20.GL_TEXTURE_CUBE_MAP && texture.width == texture.height)
        require(texture.width > 0 && levels in 1..(32 - Integer.numberOfLeadingZeros(texture.width)))
    }
}

/**
 * Builds linear lighting from an equirectangular image or a borrowed cube in [HDR_BUILD_STEPS] steps, one per [step]
 * call, on the GL thread inside
 * `GdxRuntime.withContext`. Each step renders into its own framebuffer and restores the framebuffer, viewport and
 * state it found. Throws when the GPU lacks OpenGL 3 or a renderable 16-bit float framebuffer.
 */
class HdrEnvironmentBuild(private val input: EnvironmentSource, private val shaders: ShaderStorage) : Disposable {
    constructor(image: HdrImage, shaders: ShaderStorage) : this(EquirectSource(image), shaders)

    private val sourceSize = (input as? CubeSource)?.texture?.width ?: SPECULAR_SIZE
    private val sourceLevels = (input as? CubeSource)?.levels ?: SOURCE_LEVELS
    private var ownsSource = false
    private var disposed = false

    private var done = 0
    private var equirect: GpuTexture? = null
    private var source: GpuTexture? = null
    private var specular: GpuTexture? = null
    private var irradiance: GpuTexture? = null
    private var fbo = 0
    private var project: ShaderProgram? = null
    private var prefilter: ShaderProgram? = null
    private var convolve: ShaderProgram? = null
    private var mesh: Mesh? = null
    private val ambient = FloatArray(18)

    /** Does the next step; true when the environment is complete. */
    fun step(): Boolean {
        check(!disposed) { "Environment build was released" }
        if (done >= HDR_BUILD_STEPS) return true
        try {
            when (done) {
                0 -> prepareSource()
                1 -> projectToCube()
                in 2..6 -> prefilterLevel(done - 1)
                7 -> convolveIrradiance()
                8 -> readAmbient()
            }
        } catch (failure: Throwable) {
            dispose()
            throw failure
        }
        done++
        return done >= HDR_BUILD_STEPS
    }

    /** Hands over the finished textures and releases everything else. */
    fun finish(): HdrEnvironment {
        check(!disposed && done >= HDR_BUILD_STEPS) { "HDR environment is not built yet or was released" }
        val result = HdrEnvironment(specular!!, irradiance!!, SPECULAR_LEVELS, ambient.copyOf())
        specular = null
        irradiance = null
        dispose()
        return result
    }

    /** Transfers an equirectangular background separately, preserving the HDR draw/exposure path. */
    fun finishHdr(): HdrSkyEnvironment {
        check(!disposed && done >= HDR_BUILD_STEPS) { "HDR environment is not built yet or was released" }
        val background = checkNotNull(equirect) { "A cube-source build has no HDR background" }
        equirect = null
        return HdrSkyEnvironment(background, finish())
    }

    private fun prepareSource() {
        checkNotNull(Gdx.gl30) { "HDR skies need OpenGL 3" }
        when (val src = input) {
            is EquirectSource -> uploadImage(src.image)
            is CubeSource -> source = src.texture
        }
    }

    private fun uploadImage(image: HdrImage) {
        val gl = Gdx.gl
        gl.glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
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
        parameters(GL20.GL_TEXTURE_2D, GL20.GL_LINEAR, GL20.GL_LINEAR, GL20.GL_REPEAT, GL20.GL_CLAMP_TO_EDGE)
        gl.glBindTexture(GL20.GL_TEXTURE_2D, 0)
    }

    private fun projectToCube() {
        val src = if (input is EquirectSource) {
            cube(SPECULAR_SIZE, SOURCE_LEVELS).also { source = it; ownsSource = true }
        } else checkNotNull(source)
        specular = cube(SPECULAR_SIZE, SPECULAR_LEVELS)
        irradiance = cube(IRRADIANCE_SIZE, 1)
        fbo = Gdx.gl.glGenFramebuffer()
        if (input is EquirectSource) {
            val program = program("hdr_project").also { project = it }
            render(src, 0, SPECULAR_SIZE, program) {
                equirect!!.bind(0)
                program.setUniformi("u_equirect", 0)
                program.setUniformf("u_texel", 2f / SPECULAR_SIZE)
            }
            src.bind()
            Gdx.gl.glGenerateMipmap(GL20.GL_TEXTURE_CUBE_MAP)
            Gdx.gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0)
        }
        // level 0 of the specular cube is the projection itself (roughness 0)
        prefilterLevel(0)
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

    private fun readAmbient() {
        val pixel = BufferUtils.newFloatBuffer(4)
        withFramebuffer {
            for (face in 0 until 6) {
                attach(irradiance!!, face, 0)
                pixel.clear()
                Gdx.gl.glReadPixels(IRRADIANCE_SIZE / 2, IRRADIANCE_SIZE / 2, 1, 1, GL20.GL_RGBA, GL20.GL_FLOAT, pixel)
                for (c in 0 until 3) ambient[face * 3 + c] = pixel.get(c)
            }
        }
    }

    /** A cube of RGB16F faces [size] wide with [levels] mip levels allocated, clamped and linearly filtered. */
    private fun cube(size: Int, levels: Int): GpuTexture {
        val gl = Gdx.gl
        val texture = GpuTexture(GL20.GL_TEXTURE_CUBE_MAP, size, size)
        texture.bind()
        for (level in 0 until levels) for (face in 0 until 6) {
            val s = maxOf(size shr level, 1)
            gl.glTexImage2D(
                GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face,
                level,
                GL_RGB16F,
                s,
                s,
                0,
                GL20.GL_RGB,
                GL_HALF_FLOAT,
                null
            )
        }
        gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL30.GL_TEXTURE_MAX_LEVEL, levels - 1)
        val min = if (levels > 1) GL20.GL_LINEAR_MIPMAP_LINEAR else GL20.GL_LINEAR
        parameters(GL20.GL_TEXTURE_CUBE_MAP, min, GL20.GL_LINEAR, GL20.GL_CLAMP_TO_EDGE, GL20.GL_CLAMP_TO_EDGE)
        gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0)
        return texture
    }

    private fun parameters(target: Int, min: Int, mag: Int, wrapS: Int, wrapT: Int) {
        val gl = Gdx.gl
        gl.glTexParameteri(target, GL20.GL_TEXTURE_MIN_FILTER, min)
        gl.glTexParameteri(target, GL20.GL_TEXTURE_MAG_FILTER, mag)
        gl.glTexParameteri(target, GL20.GL_TEXTURE_WRAP_S, wrapS)
        gl.glTexParameteri(target, GL20.GL_TEXTURE_WRAP_T, wrapT)
    }

    private fun program(fragment: String) = shaders.program("hdr_cube.vert", "hdr_common.glsl", "$fragment.frag")

    /** Draws [program] once per face into [level] of [target], [size] pixels square; [bind] sets the pass's inputs. */
    private fun render(target: GpuTexture, level: Int, size: Int, program: ShaderProgram, bind: () -> Unit) {
        val quad = mesh ?: createFullscreenTriangle().also { mesh = it }
        withFramebuffer {
            Gdx.gl.glViewport(0, 0, size, size)
            program.bind()
            bind()
            for (face in 0 until 6) {
                attach(target, face, level)
                program.setUniformi("u_face", face)
                quad.render(program, GL20.GL_TRIANGLES)
            }
        }
    }

    private fun attach(target: GpuTexture, face: Int, level: Int) {
        val gl = Gdx.gl
        gl.glFramebufferTexture2D(
            GL20.GL_FRAMEBUFFER,
            GL20.GL_COLOR_ATTACHMENT0,
            GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face,
            target.textureObjectHandle,
            level
        )
        val status = gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER)
        check(status == GL20.GL_FRAMEBUFFER_COMPLETE) {
            "16-bit float framebuffer is incomplete (status 0x${
                status.toString(
                    16
                )
            })"
        }
    }

    /** Runs [block] with [fbo] bound and blending, depth, culling and scissor off; restores what it found. */
    private fun withFramebuffer(block: () -> Unit) {
        val gl = Gdx.gl
        val ints = BufferUtils.newIntBuffer(16)
        gl.glGetIntegerv(GL_FRAMEBUFFER_BINDING, ints)
        val previous = ints.get(0)
        ints.clear()
        gl.glGetIntegerv(GL20.GL_VIEWPORT, ints)
        val viewport = IntArray(4) { ints.get(it) }
        val caps = listOf(GL20.GL_BLEND, GL20.GL_DEPTH_TEST, GL20.GL_CULL_FACE, GL20.GL_SCISSOR_TEST)
        val enabled = caps.map(gl::glIsEnabled)
        caps.forEach(gl::glDisable)
        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, fbo)
        try {
            block()
        } finally {
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previous)
            gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            caps.zip(enabled).forEach { (cap, on) -> if (on) gl.glEnable(cap) }
            gl.glActiveTexture(GL20.GL_TEXTURE0)
            gl.glBindTexture(GL20.GL_TEXTURE_2D, 0)
            gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0)
        }
    }

    private fun releaseSource() {
        if (ownsSource) source?.dispose()
        source = null
        ownsSource = false
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        releaseSource()
        listOfNotNull(
            equirect,
            specular,
            irradiance,
            project,
            prefilter,
            convolve,
            mesh
        ).forEach(Disposable::dispose)
        equirect = null
        source = null
        specular = null
        irradiance = null
        project = null
        prefilter = null
        convolve = null
        mesh = null
        if (fbo != 0) Gdx.gl.glDeleteFramebuffer(fbo)
        fbo = 0
    }
}
