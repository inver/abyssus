/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.water

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.content.Rgba
import net.nevinsky.abyssus.lib.core.water.WaterPassPlan
import net.nevinsky.abyssus.plugin.sceneview.shadows.ShadowGlState
import org.slf4j.Logger

/** How many planar reflection targets can exist; the planner never asks for more (see `DEFAULT_MAX_REFLECTIONS`). */
internal const val MAX_REFLECTION_TARGETS = 4

/** What [WaterResources.ensure] was asked for; a failed allocation is not retried until this changes. */
private data class Wanted(val viewW: Int, val viewH: Int, val depthW: Int, val depthH: Int, val reflW: Int, val reflH: Int, val reflections: Int)

/**
 * The offscreen targets of one view's water passes, kept between frames and made again only when the sizes change: a
 * copy of the opaque scene's colour ([sceneColor]), the packed linear depth of everything opaque ([sceneDepth]) and of
 * terrain alone ([terrainDepth]), and one planar reflection image per reflected level. Every call needs the GL context
 * current; nothing here outlives it ([abandon] after a lost context, [dispose] otherwise). A target that cannot be
 * allocated leaves [failed] set with a reason logged once, and the water falls back to plain tinted water until the
 * sizes change.
 */
internal class WaterResources(private val log: Logger) : Disposable {
    private val owner = Gdx.app
    var sceneColor: Texture? = null
        private set
    var sceneDepth: FrameBuffer? = null
        private set
    var terrainDepth: FrameBuffer? = null
        private set
    private val reflectionTargets = arrayOfNulls<FrameBuffer>(MAX_REFLECTION_TARGETS)
    private var current: Wanted? = null
    private var failedFor: Wanted? = null

    /** True when the last [ensure] could not allocate what it was asked for. */
    val failed: Boolean get() = failedFor != null && failedFor == lastAsked
    private var lastAsked: Wanted? = null

    /** The reflection image number [slot] (0 up), or null when it does not exist. */
    fun reflection(slot: Int): FrameBuffer? = reflectionTargets.getOrNull(slot)

    /** How many targets are alive, for tests. */
    val targetCount: Int get() = listOfNotNull(sceneColor, sceneDepth, terrainDepth).size + reflectionTargets.count { it != null }

    /**
     * Makes the targets [plan] needs for a frame of [viewWidth] by [viewHeight]: allocates what is missing or the wrong
     * size, releases surplus reflection targets, and does nothing when everything is right. False when it could not.
     */
    fun ensure(plan: WaterPassPlan, viewWidth: Int, viewHeight: Int): Boolean {
        val wanted = Wanted(
            viewWidth, viewHeight, plan.depthWidth, plan.depthHeight, plan.reflectionWidth, plan.reflectionHeight,
            plan.reflected.size.coerceAtMost(MAX_REFLECTION_TARGETS),
        )
        lastAsked = wanted
        if (failedFor == wanted) return false
        if (current == wanted && present(wanted)) return true
        val state = ShadowGlState()
        return try {
            val max = maxTextureSize()
            if (listOf(viewWidth, viewHeight, wanted.depthW, wanted.depthH, wanted.reflW, wanted.reflH).any { it !in 1..max }) {
                release()
                fail(wanted, "a target is larger than the supported texture size $max")
                false
            } else runCatchingKeepingCancellation {
                // only what is missing or the wrong size is made again, so a change in how many levels reflect keeps the rest
                if (sceneColor?.let { it.width != viewWidth || it.height != viewHeight } == true) { sceneColor?.dispose(); sceneColor = null }
                if (sceneColor == null) {
                    sceneColor = Texture(viewWidth, viewHeight, Pixmap.Format.RGBA8888).also { it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
                }
                sceneDepth = packedTargetOf(sceneDepth, wanted.depthW, wanted.depthH)
                terrainDepth = packedTargetOf(terrainDepth, wanted.depthW, wanted.depthH)
                for (i in 0 until MAX_REFLECTION_TARGETS) {
                    val existing = reflectionTargets[i]
                    if (i >= wanted.reflections) {
                        existing?.dispose(); reflectionTargets[i] = null
                        continue
                    }
                    if (existing != null && existing.width == wanted.reflW && existing.height == wanted.reflH) continue
                    existing?.dispose()
                    reflectionTargets[i] = FrameBuffer(Pixmap.Format.RGBA8888, wanted.reflW, wanted.reflH, true).also {
                        it.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                        it.colorBufferTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
                    }
                }
                current = wanted
                failedFor = null
                true
            }.getOrElse { e ->
                release()
                fail(wanted, e.message ?: e.javaClass.simpleName)
                false
            }
        } finally {
            state.restore()
        }
    }

    private fun packedTargetOf(existing: FrameBuffer?, width: Int, height: Int): FrameBuffer {
        if (existing != null && existing.width == width && existing.height == height) return existing
        existing?.dispose()
        return packedTarget(width, height)
    }

    private fun present(wanted: Wanted): Boolean =
        sceneColor != null && sceneDepth != null && terrainDepth != null &&
            (0 until wanted.reflections).all { reflectionTargets[it] != null } &&
            (wanted.reflections until MAX_REFLECTION_TARGETS).all { reflectionTargets[it] == null }

    private fun packedTarget(width: Int, height: Int): FrameBuffer = FrameBuffer(Pixmap.Format.RGBA8888, width, height, true).also {
        // packed depth is numbers, not colours: no filtering between texels
        it.colorBufferTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        it.colorBufferTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
    }

    private fun fail(wanted: Wanted, reason: String) {
        failedFor = wanted
        current = null
        log.warn("Water targets could not be allocated ($reason); water is drawn without reflections, refraction and foam until the view changes size")
    }

    private fun maxTextureSize(): Int {
        val size = BufferUtils.newIntBuffer(1)
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, size)
        return size.get(0)
    }

    /**
     * Renders into [target] (clearing it to [clear] and to the far depth) with [block], and restores the caller's
     * framebuffer, viewport and GL state afterwards. False when [block] or the target failed; nothing is thrown.
     */
    fun render(target: FrameBuffer, clear: Rgba, block: () -> Unit): Boolean {
        val gl = Gdx.gl
        val state = ShadowGlState()
        return try {
            runCatchingKeepingCancellation {
                target.bind()
                gl.glViewport(0, 0, target.width, target.height)
                gl.glDisable(GL20.GL_SCISSOR_TEST)
                gl.glEnable(GL20.GL_DEPTH_TEST); gl.glDepthMask(true); gl.glDepthFunc(GL20.GL_LEQUAL)
                gl.glColorMask(true, true, true, true)
                gl.glDisable(GL20.GL_BLEND)
                // packed depth is numerical data: no dithering and no colour-space conversion
                gl.glDisable(GL20.GL_DITHER)
                gl.glDisable(0x8DB9) // GL_FRAMEBUFFER_SRGB
                gl.glClearColor(clear.r, clear.g, clear.b, clear.a)
                gl.glClearDepthf(1f)
                gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                block()
                true
            }.getOrElse { e ->
                log.warn("A water pass failed (${e.message ?: e.javaClass.simpleName}); the water is drawn without it", e)
                false
            }
        } finally {
            gl.glFrontFace(GL20.GL_CCW)
            state.restore()
        }
    }

    /** Copies the colour of the current framebuffer, [width] by [height], into [sceneColor]. False when there is no such texture. */
    fun captureScene(width: Int, height: Int): Boolean {
        val texture = sceneColor ?: return false
        if (texture.width != width || texture.height != height) return false
        val active = BufferUtils.newIntBuffer(1).also { Gdx.gl.glGetIntegerv(GL20.GL_ACTIVE_TEXTURE, it) }.get(0)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        texture.bind()
        Gdx.gl.glCopyTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
        Gdx.gl.glActiveTexture(active)
        return Gdx.gl.glGetError() == GL20.GL_NO_ERROR
    }

    private fun release() {
        sceneColor?.dispose(); sceneColor = null
        sceneDepth?.dispose(); sceneDepth = null
        terrainDepth?.dispose(); terrainDepth = null
        for (i in reflectionTargets.indices) { reflectionTargets[i]?.dispose(); reflectionTargets[i] = null }
        current = null
    }

    /** The context is gone: forget every target without GL calls. */
    fun abandon() {
        GLFrameBuffer.clearAllFrameBuffers(owner)
        Texture.clearAllTextures(owner)
        sceneColor = null; sceneDepth = null; terrainDepth = null
        reflectionTargets.fill(null)
        current = null
        failedFor = null
        lastAsked = null
    }

    override fun dispose() {
        release()
        failedFor = null
        lastAsked = null
    }
}
