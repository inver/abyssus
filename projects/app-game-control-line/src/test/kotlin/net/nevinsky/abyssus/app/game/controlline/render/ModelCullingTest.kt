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
import org.junit.Assert.assertEquals
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

    /** A model whose rest-pose box is a cube of [half] metres either side of its origin; [half] may change, as a rebuild does. */
    private class Cube(var half: Float) : Model() {
        override fun calculateBoundingBox(out: BoundingBox): BoundingBox = out.set(Vector3(-half, -half, -half), Vector3(half, half, half))
    }

    @Test
    fun `a model rebuilt with larger bounds is measured again`() {
        val culling = ModelCulling()
        val small = Cube(1f)
        // 10 m to the side of the view at 20 m: a 1 m cube is outside, a 10 m one reaches in
        val at = Vector3(30f, 0f, -20f)
        culling.track("models/hangar", 1, small)
        assertFalse(culling.visible(ModelInstance(small).also { it.transform.setToTranslation(at) }, camera.frustum))

        val rebuilt = Cube(10f)
        culling.track("models/hangar", 2, rebuilt)
        assertEquals("the old model's sphere is dropped", 0, culling.cached)
        assertTrue(culling.visible(ModelInstance(rebuilt).also { it.transform.setToTranslation(at) }, camera.frustum))
        assertEquals(1, culling.cached)
    }

    @Test
    fun `a new build of the same model object is measured again`() {
        val culling = ModelCulling()
        val cube = Cube(1f)
        val instance = ModelInstance(cube).also { it.transform.setToTranslation(30f, 0f, -20f) }
        culling.track("models/hangar", 1, cube)
        assertFalse(culling.visible(instance, camera.frustum))
        cube.half = 10f
        culling.track("models/hangar", 1, cube)
        assertFalse("the same build keeps its sphere", culling.visible(instance, camera.frustum))
        culling.track("models/hangar", 2, cube)
        assertTrue("the next build's bounds are used", culling.visible(instance, camera.frustum))
    }

    @Test
    fun `clearing drops every sphere`() {
        val culling = ModelCulling()
        val cube = Cube(1f)
        culling.track("a", 1, cube)
        culling.visible(ModelInstance(cube), camera.frustum)
        assertEquals(1, culling.cached)
        culling.clear()
        assertEquals(0, culling.cached)
    }
}
