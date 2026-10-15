/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Why a surface is not drawn this frame. */
enum class WaterSkip {
    DISABLED,

    /** The camera is at or below the surface: no underwater rendering, the normal scene continues. */
    BELOW_VIEW,

    /** Nothing of it is in view. */
    NOT_VISIBLE,
}

/** The surfaces of one mean level, which share a planar reflection. */
class WaterLevelGroup(val level: Double, val surfaces: List<WaterSettings>) {
    /** The level in thousandths of a unit: what makes two levels "the same" and what a group is known by between frames. */
    val key: Long = levelKey(level)
}

/** The level as a whole number of thousandths (so `2.0004` and `2.0` share a reflection). */
fun levelKey(level: Double): Long = Math.round(level * 1000.0)

/**
 * What one frame does for the water in view: which levels get a planar reflection of the scene ([reflected], at most the
 * budget, ordered by level), which fall back to the sky or a tint ([fallback]), the surfaces to draw far to near
 * ([drawOrder]), why any were skipped, and the sizes of the offscreen targets. Pure data.
 */
class WaterPassPlan(
    val reflected: List<WaterLevelGroup>,
    val fallback: List<WaterLevelGroup>,
    val drawOrder: List<WaterSettings>,
    val skipped: Map<String, WaterSkip>,
    val reflectionWidth: Int,
    val reflectionHeight: Int,
    val depthWidth: Int,
    val depthHeight: Int,
) {
    /** True when no surface will be drawn, so the frame needs no water targets or passes at all. */
    val isEmpty: Boolean get() = drawOrder.isEmpty()

    /** The group of [surface] among the reflected ones, or null (it uses the fallback). */
    fun reflectionOf(surface: WaterSettings): WaterLevelGroup? = reflected.firstOrNull { it.key == levelKey(surface.level) }
}

/**
 * Plans the water of a frame. Budgets are fixed: at most [maxReflections] reflection views at half the viewport and at
 * most [maxReflectionSize] on a side, and depth targets at most [maxDepthSize] on a side. The levels that get a
 * reflection are the largest on screen, with ties by level, and one that had a reflection last frame keeps it unless
 * another is [hysteresis] times larger, so two similar levels do not swap every frame. Stateless: the previous frame's
 * choice comes in as [WaterPassPlanner.plan]'s `previous`.
 */
class WaterPassPlanner(
    private val maxReflections: Int = DEFAULT_MAX_REFLECTIONS,
    private val maxReflectionSize: Int = DEFAULT_MAX_REFLECTION_SIZE,
    private val maxDepthSize: Int = DEFAULT_MAX_DEPTH_SIZE,
    private val hysteresis: Double = DEFAULT_HYSTERESIS,
) {
    /**
     * [surfaces] are the valid surfaces of the scene; [eyeY] is the camera height; [viewWidth] by [viewHeight] the
     * viewport in pixels; [screenArea] says how much of the screen a surface covers (0 or less: out of view);
     * [previous] the keys of the levels reflected last frame.
     */
    fun plan(
        surfaces: List<WaterSettings>,
        eyeY: Double,
        viewWidth: Int,
        viewHeight: Int,
        screenArea: (WaterSettings) -> Double,
        previous: Collection<Long> = emptyList(),
        eyeX: Double = 0.0,
        eyeZ: Double = 0.0,
    ): WaterPassPlan {
        val skipped = linkedMapOf<String, WaterSkip>()
        val visible = ArrayList<Pair<WaterSettings, Double>>()
        for (s in surfaces) {
            when {
                !s.enabled -> skipped[s.id] = WaterSkip.DISABLED
                eyeY <= s.level -> skipped[s.id] = WaterSkip.BELOW_VIEW
                else -> {
                    val area = screenArea(s)
                    if (area.isFinite() && area > 0.0) visible += s to area else skipped[s.id] = WaterSkip.NOT_VISIBLE
                }
            }
        }
        val groups = visible.groupBy { levelKey(it.first.level) }.map { (key, members) ->
            val ordered = members.sortedBy { it.first.id }.map { it.first }
            ScoredGroup(WaterLevelGroup(ordered.first().level, ordered), members.sumOf { it.second }, key)
        }
        val remembered = previous.toSet()
        val ranked = groups.sortedWith(
            compareByDescending<ScoredGroup> { if (it.key in remembered) it.area * hysteresis else it.area }.thenBy { it.key },
        )
        val chosen = ranked.take(maxReflections).sortedBy { it.key }
        val rest = ranked.drop(maxReflections).sortedBy { it.key }
        val drawOrder = visible.map { it.first }.sortedWith(
            compareByDescending<WaterSettings> { (it.x - eyeX) * (it.x - eyeX) + (it.z - eyeZ) * (it.z - eyeZ) }
                .thenBy { it.level }.thenBy { it.id },
        )
        val reflection = targetSize(viewWidth, viewHeight, 0.5, maxReflectionSize)
        val depth = targetSize(viewWidth, viewHeight, 1.0, maxDepthSize)
        return WaterPassPlan(
            chosen.map { it.group }, rest.map { it.group }, drawOrder, skipped,
            reflection.first, reflection.second, depth.first, depth.second,
        )
    }

    private class ScoredGroup(val group: WaterLevelGroup, val area: Double, val key: Long)

    /** [scale] of the viewport, capped to [cap] on its longer side with the aspect kept; at least one pixel each way. */
    private fun targetSize(width: Int, height: Int, scale: Double, cap: Int): Pair<Int, Int> {
        val w = max(width, 1) * scale
        val h = max(height, 1) * scale
        val shrink = min(1.0, cap / max(w, h))
        return max(1, (w * shrink).roundToInt()) to max(1, (h * shrink).roundToInt())
    }
}

const val DEFAULT_MAX_REFLECTIONS = 2
const val DEFAULT_MAX_REFLECTION_SIZE = 1024
const val DEFAULT_MAX_DEPTH_SIZE = 2048
const val DEFAULT_HYSTERESIS = 1.25
