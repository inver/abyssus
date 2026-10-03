/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SpotConeTest {
    @Test fun coneEdgesAndSoftnessMatchAngularRadius() {
        fun ray(degrees: Double) = Vec3(sin(Math.toRadians(degrees)).toFloat(), 0f, -cos(Math.toRadians(degrees)).toFloat())
        val axis = Vec3(0f, 0f, -1f)
        val cone = SpotCone(60f, 0.5f)
        assertEquals(1f, cone.attenuation(axis, ray(0.0)), 0f)
        assertEquals(1f, cone.attenuation(axis, ray(14.0)), 0f)
        assertTrue(cone.attenuation(axis, ray(22.0)) in 0.01f..0.99f)
        assertEquals(0f, cone.attenuation(axis, ray(31.0)), 0f)
        assertEquals(0f, cone.attenuation(axis, ray(180.0)), 0f)
        assertEquals(1f, SpotCone(60f, 0f).attenuation(axis, ray(29.0)), 0f)
        assertEquals(0f, SpotCone(60f, 0f).attenuation(axis, ray(31.0)), 0f)
        assertEquals(1f, SpotCone(60f, 1f).attenuation(axis, ray(0.0)), 0f)
        assertTrue(SpotCone(60f, 1f).attenuation(axis, ray(15.0)) in 0.01f..0.99f)
    }

    @Test fun rotatedBeamAndRangeCutoff() {
        val cone = SpotCone(60f, 0.25f)
        val axis = SceneContent.forward(Quat(0.70710677f, 0f, 0f, 0.70710677f))
        assertEquals(1f, cone.attenuation(axis, Vec3(0f, 1f, 0f)), 1e-5f)
        assertEquals(0f, cone.attenuation(axis, Vec3(0f, -1f, 0f)), 0f)
        assertEquals(1f, cone.rangeAttenuation(5f, 10f), 0f)
        assertTrue(cone.rangeAttenuation(9f, 10f) in 0.01f..0.99f)
        assertEquals(0f, cone.rangeAttenuation(10f, 10f), 0f)
        assertEquals(0f, cone.rangeAttenuation(11f, 10f), 0f)
    }

    @Test fun invalidInputsAreRejectedOrUnlit() {
        for ((angle, softness) in listOf(0f to 0f, 180f to 0f, Float.NaN to 0f, 60f to -1f, 60f to 1.1f, 60f to Float.NaN)) {
            assertThrows(IllegalArgumentException::class.java) { SpotCone(angle, softness) }
        }
        val cone = SpotCone(60f, 0f)
        assertEquals(0f, cone.attenuation(Vec3(0f,0f,0f), Vec3(1f,0f,0f)), 0f)
        assertEquals(0f, cone.rangeAttenuation(Float.NaN, 10f), 0f)
        assertEquals(0f, cone.rangeAttenuation(1f, 0f), 0f)
    }
}
