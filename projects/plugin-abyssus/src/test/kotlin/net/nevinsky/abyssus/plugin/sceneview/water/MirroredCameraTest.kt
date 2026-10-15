/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.water

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.water.WaterReflectionCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirroredCameraTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private fun main() = PerspectiveCamera(60f, 800f, 600f).also {
        it.position.set(5f, 12f, 25f); it.lookAt(0f, 0f, 0f); it.near = 0.1f; it.far = 300f; it.update()
    }

    @Test fun theViewIsTheMainViewThroughTheMirrorAndThePoseIsMirrored() {
        val source = main()
        val mirrored = MirroredCamera(source, 2f)
        val expected = Matrix4(source.combined).mul(WaterReflectionCamera().matrix(2f))
        for (i in 0 until 16) assertEquals(expected.`val`[i], mirrored.combined.`val`[i], 1e-4f)
        assertEquals(source.position.x, mirrored.position.x, 1e-5f)
        assertEquals(2 * 2f - source.position.y, mirrored.position.y, 1e-5f)
        assertEquals(-source.direction.y, mirrored.direction.y, 1e-5f)
        assertEquals(source.near, mirrored.near, 0f)
        assertEquals(source.far, mirrored.far, 0f)
    }

    @Test fun itFollowsTheMainCameraAndTheLevelWhenUpdated() {
        val source = main()
        val mirrored = MirroredCamera(source, 0f)
        source.position.set(-8f, 20f, 10f)
        source.lookAt(3f, 0f, 3f)
        source.update()
        mirrored.level = 1.5f
        mirrored.update()
        val expected = Matrix4(source.combined).mul(WaterReflectionCamera().matrix(1.5f))
        for (i in 0 until 16) assertEquals(expected.`val`[i], mirrored.combined.`val`[i], 1e-4f)
        assertEquals(2 * 1.5f - 20f, mirrored.position.y, 1e-5f)
    }

    @Test fun theFrustumIsTheMirroredViewsSoCullingMatchesWhatIsDrawn() {
        val mirrored = MirroredCamera(main(), 0f)
        // the main camera looks down at the origin; through the mirror it looks up at its reflection below the plane
        assertTrue("a point above the plane that the mirror shows is inside the frustum", mirrored.frustum.pointInFrustum(Vector3(0f, 1f, 0f)))
        assertFalse("a point behind the mirrored camera is not", mirrored.frustum.pointInFrustum(Vector3(5f, 30f, 60f)))
    }
}
