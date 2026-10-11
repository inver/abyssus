/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.skybox

import net.nevinsky.abyssus.lib.core.assets.sky.procedural.CLOUD_DRIFT_REFRESH_SECONDS
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import kotlin.math.floor
import kotlin.math.roundToInt

/** Metres of orbit target movement after which the ray tracing copy of the cloud shadows is taken again. */
const val RAY_SUN_VISIBILITY_CELL = 500f

/** The uniform sun factor is compared in steps of this size, so smoothing never restarts the ray image every frame. */
private const val SCALE_STEPS = 20

/**
 * What a published ray copy of the clouds' shade on the sun was made for. A new copy is needed when any part changes:
 * the sun entity or direction, the cloud asset (by identity, so a reload is a change), whether the cloud shadow map or
 * the uniform dimming shades the sun, the uniform factor (in 5% steps), the orbit target's 500 m cell (the map is
 * centered on it) and, for drifting clouds, the [timeBucket] of the view's sky clock. Stationary clouds are copied once,
 * which bounds how often ray accumulation restarts.
 */
data class RaySunVisibilityKey(
    val sunId: String,
    val sun: Vec3,
    val clouds: Any?,
    val mapped: Boolean,
    val scaleStep: Int,
    val cellX: Long,
    val cellZ: Long,
    val timeBucket: Long,
)

/** The key for the sun [sunId] toward [sun] at [timeSeconds] of the sky clock, orbit target ([targetX], [targetZ]). */
fun raySunVisibilityKey(
    sunId: String, sun: Vec3, clouds: Any?, drifting: Boolean, mapped: Boolean, scale: Float,
    targetX: Float, targetZ: Float, timeSeconds: Double,
) = RaySunVisibilityKey(
    sunId, sun, clouds, mapped, (scale * SCALE_STEPS).roundToInt(),
    if (mapped) floor(targetX / RAY_SUN_VISIBILITY_CELL).toLong() else 0L,
    if (mapped) floor(targetZ / RAY_SUN_VISIBILITY_CELL).toLong() else 0L,
    if (drifting) floor(timeSeconds / CLOUD_DRIFT_REFRESH_SECONDS).toLong() else 0L,
)
