/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCullingTest {
    /** A model whose rest-pose box is a 2 m cube about its origin; it has no GL resources. */
    private fun model(): Model = object : Model() {
        override fun calculateBoundingBox(out: BoundingBox): BoundingBox = out.set(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))
    }

    private val camera: PerspectiveCamera by lazy {
        com.badlogic.gdx.utils.GdxNativesLoader.load() // the camera's frustum update is native
        PerspectiveCamera(60f, 100f, 100f).apply {
            position.set(0f, 0f, 0f); lookAt(0f, 0f, -1f); near = 0.1f; far = 100f; update()
        }
    }

    @Test
    fun `an instance ahead of the camera is kept and one behind it is dropped`() {
        val culling = ModelCulling()
        val m = model()
        val ahead = ModelInstance(m).also { it.transform.setToTranslation(0f, 0f, -20f) }
        val behind = ModelInstance(m).also { it.transform.setToTranslation(0f, 0f, 20f) }
        assertTrue(culling.visible(ahead, camera.frustum))
        assertFalse(culling.visible(behind, camera.frustum))
    }

    @Test
    fun `an instance far to the side or beyond the far plane is dropped`() {
        val culling = ModelCulling()
        val m = model()
        val side = ModelInstance(m).also { it.transform.setToTranslation(Vector3(500f, 0f, -20f)) }
        val far = ModelInstance(m).also { it.transform.setToTranslation(0f, 0f, -500f) }
        assertFalse(culling.visible(side, camera.frustum))
        assertFalse(culling.visible(far, camera.frustum))
    }

    @Test
    fun `a scaled instance straddling the frustum edge is kept`() {
        val culling = ModelCulling()
        val big = ModelInstance(model()).also { it.transform.setToTranslation(25f, 0f, -20f).scale(40f, 40f, 40f) }
        assertTrue(culling.visible(big, camera.frustum))
    }
}
