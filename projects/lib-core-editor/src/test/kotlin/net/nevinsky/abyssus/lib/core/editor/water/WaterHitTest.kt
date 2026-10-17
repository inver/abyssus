/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.water

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.pick.WaterOutline
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.lib.core.water.WaterPresets
import net.nevinsky.abyssus.lib.core.water.WaterSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WaterHitTest {
    private val hit = WaterHit()

    /** A 40 x 20 surface centred at (10, 2, -5). */
    private val lake = WaterPresets().defaults(WaterPreset.LAKE, "lake", "Lake", 10.0, 2.0, -5.0).copy(width = 40.0, length = 20.0)

    private fun down(x: Float, y: Float, z: Float, dx: Float = 0f, dy: Float = -1f, dz: Float = 0f) =
        Ray(Vector3(x, y, z), Vector3(dx, dy, dz).nor())

    @Test fun aRayFromAboveMeetsTheMeanLevelAtItsDistance() {
        assertEquals(8f, hit.distance(down(10f, 10f, -5f), lake)!!, 1e-4f)
        // a slanted ray travels farther to the same plane
        val slanted = down(0f, 10f, -5f, dx = 1f, dy = -1f)
        assertEquals(8f * kotlin.math.sqrt(2f), hit.distance(slanted, lake)!!, 1e-3f)
    }

    @Test fun theRectangleEdgesAreInsideAndJustPastThemIsOutside() {
        val inside = listOf(-10f to -5f, 30f to -5f, 10f to -15f, 10f to 5f, -10f to -15f, 30f to 5f)
        for ((x, z) in inside) assertEquals("$x,$z", 8f, hit.distance(down(x, 10f, z), lake)!!, 1e-4f)
        val outside = listOf(-10.01f to -5f, 30.01f to -5f, 10f to -15.01f, 10f to 5.01f)
        for ((x, z) in outside) assertNull("$x,$z", hit.distance(down(x, 10f, z), lake))
    }

    @Test fun disabledSurfacesAreNeverMet() {
        assertNull(hit.distance(down(10f, 10f, -5f), lake.copy(enabled = false)))
    }

    @Test fun onlyTheTopFaceIsPickable() {
        // from below, looking up
        assertNull(hit.distance(down(10f, -3f, -5f, dy = 1f), lake))
        // starting below the level, looking down
        assertNull(hit.distance(down(10f, 1f, -5f), lake))
        // starting exactly at the level
        assertNull(hit.distance(down(10f, 2f, -5f), lake))
    }

    @Test fun parallelAndGrazingRaysMissWithoutDividingByZero() {
        assertNull(hit.distance(down(10f, 10f, -5f, dx = 1f, dy = 0f), lake))
        assertNull(hit.distance(Ray(Vector3(10f, 10f, -5f), Vector3(1f, -1e-7f, 0f)), lake))
        val flat = hit.distance(down(-50f, 2.5f, -5f, dx = 1f, dy = -0.01f), lake)
        assertEquals("a grazing ray still meets a distant surface", 50f, flat!!, 0.1f)
    }

    @Test fun theMaximumDistanceLimitsTheSearch() {
        assertNull(hit.distance(down(10f, 10f, -5f), lake, maxDistance = 7.9f))
        assertEquals(8f, hit.distance(down(10f, 10f, -5f), lake, maxDistance = 8f)!!, 1e-4f)
    }

    @Test fun nearestIsTheHigherSurfaceUnderTheSameRay() {
        val low = lake.copy(id = "low", level = 0.0)
        val high = lake.copy(id = "high", level = 5.0)
        val pick = hit.nearest(down(10f, 10f, -5f), listOf(low, high))!!
        assertEquals("high", pick.id)
        assertEquals(5f, pick.distance, 1e-4f)
    }

    @Test fun equalSurfacesResolveToTheFirstListed() {
        val a = lake.copy(id = "a")
        val b = lake.copy(id = "b")
        assertEquals("a", hit.nearest(down(10f, 10f, -5f), listOf(a, b))!!.id)
        assertEquals("b", hit.nearest(down(10f, 10f, -5f), listOf(b, a))!!.id)
    }

    @Test fun nearestSkipsSurfacesTheRayMisses() {
        val far = lake.copy(id = "far", x = 500.0)
        assertEquals("lake", hit.nearest(down(10f, 10f, -5f), listOf(far, lake))!!.id)
        assertNull(hit.nearest(down(10f, 10f, -5f), listOf(far)))
        assertNull(hit.nearest(down(10f, 10f, -5f), emptyList()))
    }

    @Test fun aPreviewMovesOnlyTheNamedValidSurface() {
        val other = lake.copy(id = "other")
        val invalid = InvalidWater("bad", "Bad", listOf("x"))
        val water = listOf(ValidWater(lake), ValidWater(other), invalid)
        val shown = WaterPreview().apply(water, mapOf("lake" to Vec3(1f, 2.5f, 3f), "bad" to Vec3(9f, 9f, 9f)))
        val moved = (shown[0] as ValidWater).settings
        assertEquals(Triple(1.0, 2.5, 3.0), Triple(moved.x, moved.level, moved.z))
        assertEquals(lake.copy(x = 1.0, level = 2.5, z = 3.0), moved)
        assertEquals(other, (shown[1] as ValidWater).settings)
        assertSame(invalid, shown[2])
        assertSame(water, WaterPreview().apply(water, emptyMap()))
    }

    @Test fun aSeaSizedSurfaceKeepsPrecisionAtItsEdges() {
        val sea: WaterSettings = WaterPresets().defaults(WaterPreset.SEA, "sea", "Sea")
        assertEquals(30f, hit.distance(down(999f, 30f, -999f), sea)!!, 1e-3f)
        assertNull(hit.distance(down(1000.5f, 30f, 0f), sea))
    }
}

class WaterOutlineTest {
    private class Lines : net.nevinsky.abyssus.lib.core.editor.pick.LineSink {
        val segments = mutableListOf<Pair<Vec3, Vec3>>()
        override fun line(from: Vec3, to: Vec3, color: net.nevinsky.abyssus.lib.core.editor.content.Rgba) { segments += from to to }
    }

    @Test fun theOutlineIsTheExtentRectangleAtTheMeanLevel() {
        val lake = WaterPresets().defaults(WaterPreset.LAKE, "a", "Lake", 10.0, 2.0, -5.0).copy(width = 40.0, length = 20.0)
        val out = Lines().also { WaterOutline().draw(it, lake) }
        assertEquals(4, out.segments.size)
        val points = out.segments.flatMap { listOf(it.first, it.second) }
        assertEquals(setOf(Vec3(-10f, 2f, -15f), Vec3(30f, 2f, -15f), Vec3(30f, 2f, 5f), Vec3(-10f, 2f, 5f)), points.toSet())
        // the segments form a closed loop
        for (i in out.segments.indices) assertEquals(out.segments[i].second, out.segments[(i + 1) % 4].first)
    }
}
