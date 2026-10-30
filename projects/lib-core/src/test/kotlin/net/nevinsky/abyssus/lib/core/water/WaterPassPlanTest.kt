/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterPassPlanTest {
    private val presets = WaterPresets()
    private fun surface(id: String, level: Double, x: Double = 0.0, enabled: Boolean = true) =
        presets.defaults(WaterPreset.LAKE, id, id, x, level, 0.0).copy(enabled = enabled)

    private val planner = WaterPassPlanner()
    private fun plan(
        surfaces: List<WaterSettings>,
        eyeY: Double = 50.0,
        area: (WaterSettings) -> Double = { 1.0 },
        previous: Collection<Long> = emptyList(),
        view: Pair<Int, Int> = 800 to 600,
    ) = planner.plan(surfaces, eyeY, view.first, view.second, area, previous)

    @Test fun noWaterNeedsNothing() {
        val plan = plan(emptyList())
        assertTrue(plan.isEmpty)
        assertTrue(plan.reflected.isEmpty() && plan.fallback.isEmpty())
    }

    @Test fun aSingleLevelGetsAReflection() {
        val plan = plan(listOf(surface("a", 2.0)))
        assertEquals(listOf("a"), plan.reflected.single().surfaces.map { it.id })
        assertTrue(plan.fallback.isEmpty())
        assertEquals(listOf("a"), plan.drawOrder.map { it.id })
    }

    @Test fun coplanarSurfacesShareOneGroup() {
        val plan = plan(listOf(surface("b", 2.0), surface("a", 2.0004), surface("c", 3.0)))
        assertEquals(2, plan.reflected.size)
        assertEquals(listOf("a", "b"), plan.reflected.first { it.key == levelKey(2.0) }.surfaces.map { it.id })
        assertEquals(plan.reflected[0], plan.reflectionOf(surface("a", 2.0)))
        assertNull(plan.reflectionOf(surface("z", 9.0)))
    }

    @Test fun disabledAndBelowViewAndOffScreenSurfacesAreSkippedWithAReason() {
        val plan = plan(
            listOf(surface("off", 2.0, enabled = false), surface("under", 80.0), surface("away", 1.0), surface("ok", 3.0)),
            area = { if (it.id == "away") 0.0 else 1.0 },
        )
        assertEquals(WaterSkip.DISABLED, plan.skipped["off"])
        assertEquals(WaterSkip.BELOW_VIEW, plan.skipped["under"])
        assertEquals(WaterSkip.NOT_VISIBLE, plan.skipped["away"])
        assertEquals(listOf("ok"), plan.drawOrder.map { it.id })
    }

    @Test fun aCameraAtTheLevelIsBelowView() {
        assertEquals(WaterSkip.BELOW_VIEW, plan(listOf(surface("a", 50.0)), eyeY = 50.0).skipped["a"])
    }

    @Test fun onlyTheBudgetOfLevelsReflectsAndTheLargestWin() {
        val surfaces = listOf(surface("l1", 1.0), surface("l2", 2.0), surface("l3", 3.0), surface("l4", 4.0))
        val sizes = mapOf("l1" to 5.0, "l2" to 50.0, "l3" to 20.0, "l4" to 1.0)
        val plan = plan(surfaces, area = { sizes.getValue(it.id) })
        assertEquals(listOf(levelKey(2.0), levelKey(3.0)), plan.reflected.map { it.key })
        assertEquals(listOf(levelKey(1.0), levelKey(4.0)), plan.fallback.map { it.key })
        assertEquals("every visible surface is still drawn", 4, plan.drawOrder.size)
    }

    @Test fun tiesBreakByLevelSoTheChoiceIsStable() {
        val surfaces = listOf(surface("c", 3.0), surface("a", 1.0), surface("b", 2.0))
        val plan = plan(surfaces)
        assertEquals(listOf(levelKey(1.0), levelKey(2.0)), plan.reflected.map { it.key })
        assertEquals(plan.reflected.map { it.key }, plan(surfaces.reversed()).reflected.map { it.key })
    }

    @Test fun aReflectedLevelKeepsItsSlotAgainstASlightlyLargerNewcomer() {
        val surfaces = listOf(surface("a", 1.0), surface("b", 2.0), surface("c", 3.0))
        val sizes = mapOf("a" to 10.0, "b" to 10.0, "c" to 11.0)
        val first = plan(surfaces, area = { sizes.getValue(it.id) }, previous = listOf(levelKey(1.0), levelKey(2.0)))
        assertEquals(listOf(levelKey(1.0), levelKey(2.0)), first.reflected.map { it.key })
        // clearly larger: it takes over
        val bigger = mapOf("a" to 10.0, "b" to 10.0, "c" to 40.0)
        val second = plan(surfaces, area = { bigger.getValue(it.id) }, previous = listOf(levelKey(1.0), levelKey(2.0)))
        assertTrue(levelKey(3.0) in second.reflected.map { it.key })
    }

    @Test fun surfacesAreDrawnFarToNearThenByLevelThenId() {
        val surfaces = listOf(surface("near", 1.0, x = 5.0), surface("far", 1.0, x = 100.0), surface("tieB", 1.0, x = 50.0), surface("tieA", 1.0, x = 50.0))
        assertEquals(listOf("far", "tieA", "tieB", "near"), plan(surfaces).drawOrder.map { it.id })
    }

    @Test fun targetSizesFollowTheViewportWithinTheCaps() {
        val plan = plan(listOf(surface("a", 1.0)), view = 800 to 600)
        assertEquals(400 to 300, plan.reflectionWidth to plan.reflectionHeight)
        assertEquals(800 to 600, plan.depthWidth to plan.depthHeight)
        val huge = plan(listOf(surface("a", 1.0)), view = 7680 to 4320)
        assertEquals(1024 to 576, huge.reflectionWidth to huge.reflectionHeight)
        assertEquals(2048 to 1152, huge.depthWidth to huge.depthHeight)
        val tiny = plan(listOf(surface("a", 1.0)), view = 1 to 1)
        assertTrue(tiny.reflectionWidth >= 1 && tiny.depthHeight >= 1)
        val empty = plan(listOf(surface("a", 1.0)), view = 0 to 0)
        assertTrue(empty.reflectionWidth >= 1 && empty.depthWidth >= 1)
    }
}

class WaterScreenAreaTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private val area = WaterScreenArea()
    private val presets = WaterPresets()
    private fun lake(x: Double = 0.0, z: Double = 0.0, size: Double = 40.0, level: Double = 0.0) =
        presets.defaults(WaterPreset.LAKE, "l", "l", x, level, z).copy(width = size, length = size)

    private fun camera() = com.badlogic.gdx.graphics.PerspectiveCamera(60f, 800f, 600f).also {
        it.position.set(0f, 30f, 40f); it.lookAt(0f, 0f, 0f); it.near = 0.1f; it.far = 500f; it.update()
    }

    @Test fun aSurfaceInFrontOfTheCameraHasAPositiveAreaInsideTheViewport() {
        val pixels = area.pixels(camera().combined, lake(), 800, 600)
        assertTrue(pixels > 1000.0)
        assertTrue(pixels <= 800.0 * 600.0)
    }

    @Test fun aBiggerSurfaceCoversMore() {
        val c = camera().combined
        assertTrue(area.pixels(c, lake(size = 80.0), 800, 600) > area.pixels(c, lake(size = 20.0), 800, 600))
    }

    @Test fun aSurfaceBehindTheCameraOrFarOffToTheSideCoversNothing() {
        val c = camera().combined
        assertEquals(0.0, area.pixels(c, lake(z = 500.0), 800, 600), 0.0)
        assertEquals(0.0, area.pixels(c, lake(x = 5000.0), 800, 600), 0.0)
    }

    @Test fun aSurfaceFillingTheViewCoversTheWholeViewport() {
        val looking = com.badlogic.gdx.graphics.PerspectiveCamera(60f, 800f, 600f).also {
            it.position.set(0f, 5f, 0f); it.lookAt(0f, 0f, -1f); it.up.set(0f, 0f, -1f); it.near = 0.1f; it.far = 500f; it.update()
        }
        val full = area.pixels(looking.combined, lake(size = 100000.0), 800, 600)
        assertEquals(800.0 * 600.0, full, 800.0 * 600.0 * 0.01)
    }

    @Test fun partlyVisibleSurfacesAreClippedToTheView() {
        val c = camera().combined
        val whole = area.pixels(c, lake(size = 40.0), 800, 600)
        val edge = area.pixels(c, lake(x = 40.0, size = 60.0), 800, 600)
        assertTrue(edge > 0.0)
        assertTrue(whole > 0.0)
    }

    @Test fun degenerateViewportsHaveNoArea() {
        assertEquals(0.0, area.pixels(camera().combined, lake(), 0, 600), 0.0)
    }
}
