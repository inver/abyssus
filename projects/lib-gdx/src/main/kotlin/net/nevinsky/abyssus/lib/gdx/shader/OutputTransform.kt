/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The display transform of a host that lights in linear space: multiply by [exposure], tone map (Khronos PBR Neutral,
 * which leaves surface colours alone up to about 0.76 and rolls the highlights off to 1), then encode as sRGB. A host that
 * never turns it on gets the colours its shaders wrote before it existed. `output_transform.glsl` is the shader side
 * and the functions below are its reference: tests compare the GPU with them.
 */
data class OutputTransform(val exposure: Float = 1f) {
    init {
        require(exposure.isFinite() && exposure > 0f) { "exposure must be a positive number: $exposure" }
    }
}

/** The sRGB transfer function's decode: display value [c] (0..1) to linear light. */
fun srgbToLinear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

/** The sRGB transfer function's encode: linear light [c] (0..1) to a display value. */
fun linearToSrgb(c: Float): Float = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f

private const val START_COMPRESSION = 0.8f - 0.04f
private const val DESATURATION = 0.15f

/**
 * Tone maps the linear colour [rgb] in place (Khronos PBR Neutral): the darkest channel loses a small offset, and a colour
 * whose brightest channel passes [START_COMPRESSION] is compressed towards 1 and drained towards white.
 */
fun toneMap(rgb: FloatArray) {
    val x = min(rgb[0], min(rgb[1], rgb[2]))
    val offset = if (x < 0.08f) x - 6.25f * x * x else 0.04f
    for (i in 0..2) rgb[i] -= offset
    val peak = max(rgb[0], max(rgb[1], rgb[2]))
    if (peak < START_COMPRESSION) return
    val d = 1f - START_COMPRESSION
    val newPeak = 1f - d * d / (peak + d - START_COMPRESSION)
    for (i in 0..2) rgb[i] *= newPeak / peak
    val g = 1f - 1f / (DESATURATION * (peak - newPeak) + 1f)
    for (i in 0..2) rgb[i] = rgb[i] + (newPeak - rgb[i]) * g
}

/** The display colour (each channel 0..1) of linear light [rgb] under [transform]; [rgb] is not changed. */
fun OutputTransform.apply(rgb: FloatArray): FloatArray {
    val out = FloatArray(3) { max(rgb[it], 0f) * exposure }
    toneMap(out)
    for (i in 0..2) out[i] = linearToSrgb(out[i].coerceIn(0f, 1f))
    return out
}
