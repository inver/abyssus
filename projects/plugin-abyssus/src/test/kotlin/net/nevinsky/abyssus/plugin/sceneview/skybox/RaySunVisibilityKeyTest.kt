/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.skybox

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.*
import org.junit.Test

class RaySunVisibilityKeyTest {
    private val sun = Vector3(0f, 1f, 0f)
    private val clouds = Any()
    private fun key(
        id: String = "sun", direction: Vector3 = sun, asset: Any? = clouds, drifting: Boolean = false, mapped: Boolean = true,
        scale: Float = .6f, x: Float = 0f, z: Float = 0f, time: Double = 0.0,
    ) = raySunVisibilityKey(id, direction, asset, drifting, mapped, scale, x, z, time)

    @Test
    fun anUnchangedShadeIsNotCopiedAgain() {
        assertEquals(key(), key())
        assertEquals("stationary clouds ignore time", key(), key(time = 9999.0))
        assertEquals("a map ignores sub-cell target moves", key(), key(x = 499f, z = -0f))
        assertEquals("smoothing within a step", key(scale = .6f), key(scale = .62f))
    }

    @Test
    fun driftingCloudsAreCopiedEveryRefreshPeriod() {
        assertEquals(key(drifting = true), key(drifting = true, time = 1.99))
        assertNotEquals(key(drifting = true), key(drifting = true, time = 2.0))
    }

    @Test
    fun everyInputThatChangesTheShadeChangesTheKey() {
        val base = key()
        assertNotEquals(base, key(id = "other"))
        assertNotEquals(base, key(direction = Vector3(1f, 0f, 0f)))
        assertNotEquals("a cloud reload", base, key(asset = Any()))
        assertNotEquals(base, key(mapped = false))
        assertNotEquals("a bigger change of the uniform factor", base, key(scale = .8f))
        assertNotEquals("leaving the 500 m cell", base, key(x = 500f))
        assertNotEquals(base, key(z = -1f))
    }

    @Test
    fun theUniformDimmingDoesNotDependOnWhereTheTargetIs() {
        assertEquals(key(mapped = false), key(mapped = false, x = 10_000f, z = -3000f))
    }
}
