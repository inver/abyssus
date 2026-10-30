/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import java.awt.image.BufferedImage

/** What a water GL check reads of the last frame: the canvas image and the offscreen targets, as bytes. */
internal class WaterShot(
    val image: BufferedImage,
    val renderer: SceneRenderer,
    val reflection: Map<Int, TargetImage>,
    val sceneDepth: TargetImage?,
    val terrainDepth: TargetImage?,
    /** The world centre of each drawn model's bounds in the last frame, by entity id. */
    val modelCentres: Map<String, com.badlogic.gdx.math.Vector3> = emptyMap(),
)

/** The RGBA bytes of an offscreen target, row 0 at the bottom (the way GL reads it). */
internal class TargetImage(val width: Int, val height: Int, val bytes: ByteArray) {
    fun rgb(x: Int, yFromTop: Int): Triple<Int, Int, Int> {
        val i = ((height - 1 - yFromTop) * width + x) * 4
        return Triple(bytes[i].toInt() and 255, bytes[i + 1].toInt() and 255, bytes[i + 2].toInt() and 255)
    }

    /** The packed depth at ([x], [yFromTop]) as the water shader decodes it. */
    fun depth(x: Int, yFromTop: Int): Float {
        val i = ((height - 1 - yFromTop) * width + x) * 4
        fun c(k: Int) = (bytes[i + k].toInt() and 255) / 255f
        return c(0) / 16581375f + c(1) / 65025f + c(2) / 255f + c(3)
    }
}

internal object WaterGlRig {
    /** Reads a [FrameBuffer] back without disturbing the caller's framebuffer binding. */
    fun read(target: FrameBuffer): TargetImage {
        val gl = Gdx.gl
        val before = BufferUtils.newIntBuffer(1).also { gl.glGetIntegerv(0x8CA6, it) }.get(0)
        target.bind()
        val buffer = BufferUtils.newByteBuffer(target.width * target.height * 4)
        gl.glReadPixels(0, 0, target.width, target.height, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buffer)
        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, before)
        return TargetImage(target.width, target.height, ByteArray(target.width * target.height * 4) { buffer.get(it) })
    }

    /**
     * Renders [params] for [frames] frames from [orbit] (sleeping while assets load) and returns the last frame with the
     * water targets as they were then. [each] runs after every frame inside the GL context.
     */
    fun shoot(
        params: SceneRenderParams,
        orbit: OrbitCamera,
        frames: Int = 70,
        size: Pair<Int, Int> = 640 to 400,
        configure: (SceneRenderer) -> Unit = {},
        log: org.slf4j.Logger = printingLog,
        sceneShaders: net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage =
            net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage().withResources("/shader/scene", SceneRenderer::class.java),
        each: (SceneRenderer, Int) -> Unit = { _, _ -> },
    ): WaterShot {
        var reflection = emptyMap<Int, TargetImage>()
        var sceneDepth: TargetImage? = null
        var terrainDepth: TargetImage? = null
        var captured: SceneRenderer? = null
        var centres = emptyMap<String, com.badlogic.gdx.math.Vector3>()
        var configured = false
        val result = GlHarness.render(params, frames, orbit, framebufferSize = size, log = log, sceneShaders = sceneShaders, afterFrame = { renderer, index ->
            if (!configured) { renderer.waterTime = 2.5; configure(renderer); configured = true }
            each(renderer, index)
            if (index < frames - 1) Thread.sleep(15)
            if (index == frames - 1) {
                captured = renderer
                centres = renderer.drawnModels.associate { entity ->
                    entity.placement.entityId to com.badlogic.gdx.math.Vector3().also {
                        com.badlogic.gdx.math.collision.BoundingBox(entity.localBounds).getCenter(it)
                        it.mul(entity.instance.transform)
                    }
                }
                val water = renderer.waterStatus
                if (water != null) {
                    val t = water.targets
                    reflection = (0 until 4).mapNotNull { slot -> t.reflection(slot)?.let { slot to read(it) } }.toMap()
                    sceneDepth = t.sceneDepth?.let(::read)
                    terrainDepth = t.terrainDepth?.let(::read)
                }
            }
        })
        result.error?.let { throw it }
        return WaterShot(result.image, captured!!, reflection, sceneDepth, terrainDepth, centres)
    }

    fun luminance(rgb: Triple<Int, Int, Int>) = (0.299 * rgb.first + 0.587 * rgb.second + 0.114 * rgb.third) / 255.0

    fun rgb(image: BufferedImage, x: Int, y: Int): Triple<Int, Int, Int> {
        val p = image.getRGB(x, y)
        return Triple((p shr 16) and 255, (p shr 8) and 255, p and 255)
    }
}
