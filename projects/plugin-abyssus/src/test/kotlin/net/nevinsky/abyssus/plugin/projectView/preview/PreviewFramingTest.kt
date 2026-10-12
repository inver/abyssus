/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView.preview

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewFramingTest {
    private val framing = PreviewFraming()

    init {
        GdxNativesLoader.load() // libGDX's matrix projection is native
    }

    /** Every corner of the model and the post projects inside the view from the default orbit angles. */
    private fun assertFramed(size: Vector3, aspect: Float) {
        val frame = framing.frame(size, aspect)
        val orbit = OrbitCamera(frame.target, frame.distance, 30f, 20f)
        val camera = PerspectiveCamera(PreviewFraming.FOV_DEGREES, 400f * aspect, 400f)
        val eye = orbit.position()
        camera.position.set(eye.x, eye.y, eye.z)
        camera.near = frame.near
        camera.far = frame.far
        camera.up.set(Vector3.Y)
        camera.lookAt(frame.target.x, frame.target.y, frame.target.z)
        camera.update()
        val box = framing.bounds(size)
        for (i in 0 until 8) {
            val corner = Vector3(
                if (i and 1 == 0) box.min.x else box.max.x,
                if (i and 2 == 0) box.min.y else box.max.y,
                if (i and 4 == 0) box.min.z else box.max.z,
            )
            val p = Vector3(corner).prj(camera.combined)
            assertTrue("$size corner $corner at $p", p.x in -1f..1f && p.y in -1f..1f && p.z in -1f..1f)
        }
    }

    @Test
    fun aOneMetreCrateIsFramed() {
        assertFramed(Vector3(1f, 1f, 1f), 4f / 3f)
        assertFramed(Vector3(1f, 1f, 1f), 0.6f)
    }

    @Test
    fun aHundredMetreCrateIsFramed() {
        assertFramed(Vector3(100f, 100f, 100f), 4f / 3f)
        assertFramed(Vector3(100f, 2f, 30f), 0.6f)
    }

    @Test
    fun aTinyModelStillShowsThePost() {
        assertFramed(Vector3(0.01f, 0.01f, 0.01f), 1f)
    }
}
