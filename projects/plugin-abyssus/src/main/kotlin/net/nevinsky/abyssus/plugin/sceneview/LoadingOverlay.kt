/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.util.GeometryUtils.Companion.createFullscreenTriangle
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.utils.Disposable
import kotlin.math.PI

/**
 * Grays out the whole view and draws a round progress indicator in its center, in GL: a Swing component cannot be
 * laid over the heavyweight GL canvas. Call only with the GL context current, after everything else is drawn.
 */
class LoadingOverlay(shaders: ShaderStorage) : Disposable {
    private val program = shaders.program("overlay")

    private val triangle = createFullscreenTriangle()

    private var seconds = 0f

    fun draw(width: Int, height: Int, deltaSeconds: Float) {
        // a long frame (an upload) must not make the ring jump ahead
        seconds += deltaSeconds.coerceIn(0f, MAX_STEP_SECONDS)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        program.bind()
        program.setUniformf("u_resolution", width.toFloat(), height.toFloat())
        program.setUniformf("u_angle", angleAt(seconds))
        triangle.render(program, GL20.GL_TRIANGLES)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    override fun dispose() {
        triangle.dispose()
        program.dispose()
    }

    companion object {
        private const val TURNS_PER_SECOND = 0.8f
        private const val MAX_STEP_SECONDS = 0.05f

        /** The head of the spinning arc, in radians, [seconds] after the overlay appeared. */
        fun angleAt(seconds: Float): Float = ((seconds * TURNS_PER_SECOND) % 1f) * (2f * PI.toFloat())
    }
}
