/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Test

/** The reference of the box-projected reflection lookup (`envDirection` in `pbr.fragment.glsl`). */
class BoxProjectionTest {
    private val min = Vector3(-10f, 0f, -20f)
    private val max = Vector3(10f, 8f, 20f)

    private fun assertVector(want: Vector3, got: Vector3) {
        assertEquals(want.x, got.x, 1e-4f)
        assertEquals(want.y, got.y, 1e-4f)
        assertEquals(want.z, got.z, 1e-4f)
    }

    @Test
    fun aRayStraightUpHitsTheRoofAboveThePoint() {
        // a floor point 4 m beside the probe: its reflection straight up meets the roof above it, not above the probe
        val probe = Vector3(0f, 2f, 0f)
        val got = boxProjected(Vector3(4f, 0f, 0f), Vector3(0f, 1f, 0f), min, max, probe)
        assertVector(Vector3(4f, 6f, 0f), got)
    }

    @Test
    fun aRayTowardAWallHitsThatWall() {
        val probe = Vector3(0f, 2f, 0f)
        // toward +x at 45 degrees up from (5, 0, 0): the wall at x = 10 is met at height 5 (below the roof at 8)
        val got = boxProjected(Vector3(5f, 0f, 0f), Vector3(1f, 1f, 0f), min, max, probe)
        assertVector(Vector3(10f, 3f, 0f), got)
    }

    @Test
    fun fromTheProbeTheDirectionIsTheRayItself() {
        val probe = Vector3(1f, 3f, -2f)
        val ray = Vector3(0.3f, 0.5f, -0.8f)
        val got = boxProjected(probe, ray, min, max, probe).nor()
        assertVector(Vector3(ray).nor(), got)
    }

    @Test
    fun aRayAlongAnAxisDoesNotDivideByZero() {
        val got = boxProjected(Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f), min, max, Vector3(0f, 1f, 0f))
        assertVector(Vector3(0f, 0f, 20f), got)
    }
}
