/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.*
import org.junit.Test

/** The cloud visibility contract: validation, identity, the shading payload layout and when it is re-uploaded. */
class RaySunVisibilityTest {
    private val rows = floatArrayOf(.1f, 0f, 0f, .5f, 0f, 0f, .1f, .5f)
    private fun map(light: String = "sun", revision: Long = 1, outside: Float = .6f, texels: FloatArray = FloatArray(4) { .25f * (it + 1) }) =
        RaySunVisibility(light, 2, texels, rows, .1f, outside, revision)

    private val sun = RayLight("sun", RayLightKind.DIRECTIONAL, RayColor(1f, 1f, 1f))
    private val lamp = RayLight("lamp", RayLightKind.POINT, RayColor(1f, 1f, 1f), range = 5f)
    private fun scene(visibility: RaySunVisibility?, lights: List<RayLight> = listOf(lamp, sun), textures: List<RayTexture> = emptyList()) =
        RaySceneSnapshot(emptyList(), emptyList(), emptyList(), textures, lights, sunVisibility = visibility)

    @Test fun invalidMapsAreRejected() {
        fun fails(block: () -> Unit) = assertTrue(runCatching(block).exceptionOrNull() is IllegalArgumentException)
        fails { RaySunVisibility("sun", 2, FloatArray(3), rows, .1f, 1f) }
        fails { RaySunVisibility("sun", 2, floatArrayOf(0f, 0f, 0f, 1.5f), rows, .1f, 1f) }
        fails { RaySunVisibility("sun", 2, floatArrayOf(0f, 0f, 0f, Float.NaN), rows, .1f, 1f) }
        fails { RaySunVisibility("sun", 0, FloatArray(0), rows, .1f, 1f) }
        fails { RaySunVisibility("sun", 2, FloatArray(4), FloatArray(7), .1f, 1f) }
        fails { RaySunVisibility("sun", 2, FloatArray(4), rows, 0f, 1f) }
        fails { RaySunVisibility("sun", 2, FloatArray(4), rows, .1f, 1.2f) }
    }

    @Test fun theMapIsAnImmutableCopyIdentifiedByItsRevision() {
        val texels = FloatArray(4) { .5f }
        val a = RaySunVisibility("sun", 2, texels, rows, .1f, .6f, 7)
        texels[0] = 0f
        assertEquals(.5f, a.visibility()[0], 0f)
        a.visibility()[1] = 0f
        assertEquals(.5f, a.visibility()[1], 0f)
        assertEquals(a, RaySunVisibility("sun", 2, FloatArray(4) { .5f }, rows, .1f, .6f, 7))
        assertNotEquals(a, RaySunVisibility("sun", 2, FloatArray(4) { .5f }, rows, .1f, .6f, 8))
        assertNotEquals(a, RaySunVisibility("sun", 2, FloatArray(4) { .5f }, rows, .1f, .7f, 7))
        assertNotEquals(a, RaySunVisibility("other", 2, FloatArray(4) { .5f }, rows, .1f, .6f, 7))
    }

    @Test fun theUniformMapAppliesOnlyItsOutsideFactor() {
        val uniform = RaySunVisibility.uniform("sun", .3f, 2)
        assertEquals(.3f, uniform.outside, 0f)
        val m = uniform.worldToMap()
        // any position maps far outside [0, 1], so only the outside factor remains
        val u = m[0] * 100f + m[1] * 50f + m[2] * -100f + m[3]
        assertTrue(u < -1f)
    }

    @Test fun theShadingPayloadPointsAtTheMapAndAppendsItsTexture() {
        val plain = RaySceneEncoding(scene(null)).encode()
        assertEquals(-1f, plain.floats[30], 0f)
        val encoded = RaySceneEncoding(scene(map())).encode()
        val block = encoded.floats[30].toInt()
        assertTrue(block >= 32)
        assertEquals("sun is light number 1", 1f, encoded.floats[block], 0f)
        assertEquals("the map is the last texture, after the scene's own", 0f, encoded.floats[block + 1], 0f)
        assertEquals(.1f, encoded.floats[block + 2], 0f)
        assertEquals(.6f, encoded.floats[block + 3], 0f)
        assertArrayEquals(rows, encoded.floats.copyOfRange(block + 4, block + 12), 0f)
        // the texture table entry: float texels (not bytes), 2 x 2, clamped, linear
        val table = encoded.floats[4].toInt()
        assertEquals(2f, encoded.floats[table + 1], 0f)
        assertEquals(2f, encoded.floats[table + 2], 0f)
        assertEquals(RayWrap.CLAMP_TO_EDGE.ordinal.toFloat(), encoded.floats[table + 3], 0f)
        assertEquals(RayFilter.LINEAR.ordinal.toFloat(), encoded.floats[table + 5], 0f)
        assertEquals(0f, encoded.floats[table + 6], 0f)
        val texels = encoded.floats[table].toInt()
        assertArrayEquals(floatArrayOf(.25f, .5f, .75f, 1f), FloatArray(4) { encoded.floats[texels + it * 4] }, 0f)
    }

    @Test fun theMapFollowsTheSceneTexturesAndIsIgnoredWithoutItsLight() {
        val own = RayTexture("t", 1, 1, floatArrayOf(1f, 1f, 1f, 1f))
        val encoded = RaySceneEncoding(scene(map(), textures = listOf(own))).encode()
        assertEquals("after the scene's one texture", 1f, encoded.floats[encoded.floats[30].toInt() + 1], 0f)
        val missing = RaySceneEncoding(scene(map(light = "absent"))).encode()
        assertEquals(-1f, missing.floats[30], 0f)
        assertEquals(RaySceneEncoding(scene(null)).encode().floats.size, missing.floats.size)
    }

    @Test fun theMapCountsAgainstTheTextureLimit() {
        val textures = List(RAY_MAX_TEXTURES) { RayTexture("t$it", 1, 1, floatArrayOf(1f, 1f, 1f, 1f)) }
        assertNull(scene(null, textures = textures).unsupportedReason())
        assertTrue(scene(map(), textures = textures).unsupportedReason()!!.contains("textures"))
    }

    @Test fun aChangedMapForcesTheShadingPayloadToBeUploadedAgain() {
        val first = scene(map())
        assertTrue("the same map keeps the upload", shadingUnchanged(first, scene(map())))
        assertFalse("a new revision", shadingUnchanged(first, scene(map(revision = 2))))
        assertFalse("a new outside factor", shadingUnchanged(first, scene(map(outside = .2f))))
        assertFalse("the map appearing", shadingUnchanged(scene(null), first))
        assertFalse("the map going away", shadingUnchanged(first, scene(null)))
    }
}
