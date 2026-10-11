/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.skybox

import net.nevinsky.abyssus.lib.core.assets.sky.procedural.CLOUD_DRIFT_REFRESH_SECONDS
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import java.io.File
import kotlin.math.floor

/**
 * What a baked ray-tracing sky was made for. A new bake is needed when any part changes: the sky, project or sun, the
 * cloud asset ([clouds] is that asset by identity, so a reload is a change; null while clouds are hidden or absent), or,
 * for clouds that drift, the [timeBucket] of the view's sky clock. Stationary clouds, and skies without clouds, never
 * change it with time, so a bake is not repeated for nothing.
 */
data class RaySkyBakeKey(
    val name: String,
    val projectDir: File?,
    val sun: Vec3,
    val clouds: Any?,
    val timeBucket: Long,
)

/**
 * The bake key for the sky [name] at [timeSeconds] of the view's sky clock. [clouds] is the cloud asset to bake (null:
 * bake the bare atmosphere) and [drifting] says whether any of its bands has wind; a drifting sky is baked again every
 * [CLOUD_DRIFT_REFRESH_SECONDS] of cloud time, which bounds the rate at which ray accumulation restarts.
 */
fun raySkyBakeKey(name: String, projectDir: File?, sun: Vec3, clouds: Any?, drifting: Boolean, timeSeconds: Double) =
    RaySkyBakeKey(
        name, projectDir, sun, clouds,
        if (clouds != null && drifting) floor(timeSeconds / CLOUD_DRIFT_REFRESH_SECONDS).toLong() else 0L,
    )

/**
 * The six axis colors (see `HdrEnvironment.ambient`) a sky light fading from [from] to [to] shows at [weight] (0 to 1);
 * [from] itself when [to] is null. The ray path uses it so its diffuse sky light fades with the raster path.
 */
fun blendAxisColors(from: FloatArray, to: FloatArray?, weight: Float): FloatArray {
    if (to == null) return from
    val w = weight.coerceIn(0f, 1f)
    return FloatArray(from.size) { from[it] + (to[it] - from[it]) * w }
}
