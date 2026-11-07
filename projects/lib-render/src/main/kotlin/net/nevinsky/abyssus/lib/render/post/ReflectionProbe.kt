/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render.post

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.CubeSource
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.GpuTexture
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrEnvironment
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrEnvironmentBuild
import org.lwjgl.opengl.GL32C.*

/** The size of a probe's cube faces by default (texels): enough for the blurred light of a room. */
const val PROBE_FACE_SIZE = 128

/** The view of each cube face, in GL's face order (+X, -X, +Y, -Y, +Z, -Z): (direction, up). */
private val FACES =
    listOf(
        Vector3(1f, 0f, 0f) to Vector3(0f, -1f, 0f),
        Vector3(-1f, 0f, 0f) to Vector3(0f, -1f, 0f),
        Vector3(0f, 1f, 0f) to Vector3(0f, 0f, 1f),
        Vector3(0f, -1f, 0f) to Vector3(0f, 0f, -1f),
        Vector3(0f, 0f, 1f) to Vector3(0f, -1f, 0f),
        Vector3(0f, 0f, -1f) to Vector3(0f, -1f, 0f),
    )

/**
 * A reflection probe (OpenSpec `control-line-pbr-rendering`, "Interiors reflect themselves"): the host's scene seen
 * from one point into the six faces of a linear RGBA16F cube, then prefiltered into an [HdrEnvironment] (irradiance and
 * specular) by `lib-core`'s [HdrEnvironmentBuild], which the host lights its models with like a sky's.
 *
 * The work is spread over frames: [start] at a position, then one [step] a frame draws [FACES_PER_STEP] faces (the host
 * draws them in [step]'s callback, in linear light, into the bound face) or a few build steps, until a step returns the
 * environment, which the caller then owns. [failure] tells why a probe gave up; nothing of it is left then.
 *
 * It owns its cube, framebuffer and build until they are handed over or [dispose]d, and restores the framebuffer,
 * viewport and clear colour it changes. GL thread only, OpenGL 3.2.
 */
class ReflectionProbe(private val shaders: ShaderStorage, private val size: Int = PROBE_FACE_SIZE) : Disposable {
    private val position = Vector3()
    private var cube: GpuTexture? = null
    private var framebuffer = 0
    private var depth = 0
    private var face = -1
    private var build: HdrEnvironmentBuild? = null

    /** Why the last probe gave up, or null. */
    var failure: String? = null
        private set

    /** Whether a probe is under way: [start]ed and not yet finished or failed. */
    val busy: Boolean
        get() = face >= 0

    /** The camera a face is drawn with: a 90 degree square view from the probe. */
    val camera = PerspectiveCamera(90f, size.toFloat(), size.toFloat()).apply {
        near = 0.05f
        far = 500f
    }

    /** Starts a probe at [at], dropping one under way. */
    fun start(at: Vector3) {
        release()
        failure = null
        position.set(at)
        face = 0
    }

    /**
     * Does the next part of the probe; [drawFace] draws the scene from the given camera into the bound face (cleared to
     * transparent black, depth cleared, viewport set). Returns the finished environment, which the caller owns, or null
     * while the probe is still under way or when it failed ([failure]).
     */
    fun step(drawFace: (Camera) -> Unit): HdrEnvironment? {
        if (face < 0) {
            return null
        }
        return try {
            if (face < FACES.size) {
                repeat(FACES_PER_STEP) {
                    if (face < FACES.size) {
                        capture(face++, drawFace)
                    }
                }
                if (face == FACES.size) {
                    val texture = checkNotNull(cube)
                    keepingCubeBinding {
                        glBindTexture(GL_TEXTURE_CUBE_MAP, texture.textureObjectHandle)
                        glGenerateMipmap(GL_TEXTURE_CUBE_MAP)
                    }
                    build = HdrEnvironmentBuild(CubeSource(texture, levels()), shaders)
                }
                null
            } else {
                val building = checkNotNull(build)
                var done = false
                repeat(BUILD_STEPS_PER_STEP) {
                    if (!done) {
                        done = building.step()
                    }
                }
                if (done) {
                    val environment = building.finish()
                    build = null
                    release()
                    environment
                } else {
                    null
                }
            }
        } catch (failed: Exception) {
            failure = failed.message ?: failed.toString()
            release()
            null
        }
    }

    /** Runs [block] and binds back the cube map the active texture unit had (libGDX's binder trusts its own record). */
    private inline fun keepingCubeBinding(block: () -> Unit) {
        val old = glGetInteger(GL_TEXTURE_BINDING_CUBE_MAP)
        try {
            block()
        } finally {
            glBindTexture(GL_TEXTURE_CUBE_MAP, old)
        }
    }

    private fun levels(): Int = 32 - Integer.numberOfLeadingZeros(size)

    private fun capture(index: Int, drawFace: (Camera) -> Unit) {
        if (cube == null) {
            keepingCubeBinding { allocate() }
        }
        val oldFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
        val viewport = IntArray(4).also { glGetIntegerv(GL_VIEWPORT, it) }
        val clear = FloatArray(4).also { glGetFloatv(GL_COLOR_CLEAR_VALUE, it) }
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
            glFramebufferTexture2D(
                GL_FRAMEBUFFER,
                GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_CUBE_MAP_POSITIVE_X + index,
                checkNotNull(cube).textureObjectHandle,
                0,
            )
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
                "the probe's face framebuffer is incomplete"
            }
            glViewport(0, 0, size, size)
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            val (direction, up) = FACES[index]
            camera.position.set(position)
            camera.direction.set(direction)
            camera.up.set(up)
            camera.update()
            drawFace(camera)
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, oldFramebuffer)
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            glClearColor(clear[0], clear[1], clear[2], clear[3])
        }
    }

    private fun allocate() {
        val texture = GpuTexture(GL20.GL_TEXTURE_CUBE_MAP, size, size)
        cube = texture
        glBindTexture(GL_TEXTURE_CUBE_MAP, texture.textureObjectHandle)
        for (i in 0 until FACES.size) {
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL_RGBA16F, size, size, 0, GL_RGBA, GL_HALF_FLOAT, 0L)
        }
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)
        val oldRenderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING)
        depth = glGenRenderbuffers()
        glBindRenderbuffer(GL_RENDERBUFFER, depth)
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, size, size)
        glBindRenderbuffer(GL_RENDERBUFFER, oldRenderbuffer)
        val oldFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
        framebuffer = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth)
        glBindFramebuffer(GL_FRAMEBUFFER, oldFramebuffer)
    }

    private fun release() {
        build?.dispose()
        build = null
        cube?.dispose()
        cube = null
        glDeleteFramebuffers(framebuffer)
        glDeleteRenderbuffers(depth)
        framebuffer = 0
        depth = 0
        face = -1
    }

    override fun dispose() = release()
}

/** Faces drawn per [ReflectionProbe.step]: the probe takes three frames to see the room. */
private const val FACES_PER_STEP = 2

/** Prefilter steps per [ReflectionProbe.step] once the faces are drawn. */
private const val BUILD_STEPS_PER_STEP = 3
