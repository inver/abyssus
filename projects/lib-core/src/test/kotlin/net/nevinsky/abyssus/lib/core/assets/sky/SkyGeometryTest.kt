/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assets.sky

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.assets.sky.SkyFrame
import net.nevinsky.abyssus.lib.core.assets.sky.SkyRenderer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class SkyGeometryTest {
    private val sky = object : SkyRenderer {
        override fun draw(camera: Camera, frame: SkyFrame) = Unit
        override fun dispose() = Unit
    }

    @Before
    fun natives() = GdxNativesLoader.load() // Camera.update uses a native Matrix4 routine, no GL needed

    private fun camera() = PerspectiveCamera(60f, 800f, 600f).apply {
        position.set(10f, 20f, 30f)
        lookAt(0f, 0f, 0f)
        near = 0.1f
        far = 500f
        update()
    }

    @Test
    fun rotationOnlyViewProjDropsTheCameraPosition() {
        val camera = camera()
        val moved = camera().apply { position.set(-5f, 7f, 100f); lookAt(-5f - 10f, 7f - 20f, 100f - 30f); update() }
        val out = Matrix4()
        assertSame(out, sky.rotationOnlyViewProj(camera, out))
        // same orientation, different position: the matrices agree
        assertArrayEquals(out.values, sky.rotationOnlyViewProj(moved, Matrix4()).values, 1e-4f)
    }

    @Test
    fun rotationOnlyViewProjEqualsProjectionTimesViewWithoutTranslation() {
        val camera = camera()
        val view = Matrix4(camera.view).setTranslation(Vector3.Zero)
        val expected = Matrix4(camera.projection).mul(view)
        assertArrayEquals(expected.values, sky.rotationOnlyViewProj(camera, Matrix4()).values, 1e-5f)
    }
}
