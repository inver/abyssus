/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** How a stamp meets the heights already there. */
enum class StampBlend { ADD, MAX, MIN }

/**
 * A heightmap image as a grid of [width] by [height] luminance values from 0 to 1, row after row from the top.
 * Decoding it from a project image is the plugin's job; the stamp only needs the numbers.
 */
class StampImage(val width: Int, val height: Int, private val luminance: FloatArray) {
    init {
        require(width >= 1 && height >= 1) { "an image has at least one pixel" }
        require(luminance.size == width * height) { "$width x $height needs ${width * height} values" }
    }

    /** The luminance at [s],[t] (0..1 across and down the image), bilinear, clamped at the edges. */
    fun sample(s: Float, t: Float): Float {
        val x = (s * width - 0.5f).coerceIn(0f, (width - 1).toFloat())
        val y = (t * height - 0.5f).coerceIn(0f, (height - 1).toFloat())
        val x0 = minOf(x.toInt(), maxOf(width - 2, 0))
        val y0 = minOf(y.toInt(), maxOf(height - 2, 0))
        val x1 = minOf(x0 + 1, width - 1)
        val y1 = minOf(y0 + 1, height - 1)
        val fx = x - x0
        val fy = y - y0
        return (at(x0, y0) * (1 - fx) + at(x1, y0) * fx) * (1 - fy) + (at(x0, y1) * (1 - fx) + at(x1, y1) * fx) * fy
    }

    private fun at(x: Int, y: Int): Float = luminance[y * width + x]
}

/**
 * Stamp: places [image] as a heightmap, [size] world units square and turned [rotationDegrees], centred on the click,
 * its luminance scaled to [heightScale] world units. The stamp's target at a vertex is the terrain height under the
 * click plus that value: [StampBlend.ADD] adds the value to the height, [StampBlend.MAX] raises the height to the target
 * where it is lower, [StampBlend.MIN] lowers it to the target where it is higher. Strength scales the change; there
 * is no falloff and a stroke stamps once. Vertices outside the image square are untouched.
 */
class StampTool(
    private val image: StampImage,
    val size: Float,
    val heightScale: Float,
    val rotationDegrees: Float,
    val blend: StampBlend,
) : SculptTool {
    init {
        require(size.isFinite() && size > 0f) { "stamp size must be positive" }
        require(heightScale.isFinite() && rotationDegrees.isFinite()) { "stamp height scale and rotation must be finite" }
    }

    private val cos = cos(Math.toRadians(rotationDegrees.toDouble())).toFloat()
    private val sin = sin(Math.toRadians(rotationDegrees.toDouble())).toFloat()

    override val smoothFalloff: Boolean get() = false
    override val singleStamp: Boolean get() = true

    /** A square of side [size] turned any way fits in a circle of half its diagonal. */
    override fun reach(radius: Float): Float = size * sqrt(2f) / 2f

    override fun changes(stamp: SculptStamp, out: FloatArray) {
        val grid = stamp.grid
        val ref = grid.heightAt(stamp.centreX, stamp.centreZ)
        val f = stamp.footprint
        for (i in 0 until f.count) {
            val wx = (f.gx[i] * grid.cell - stamp.centreX) / grid.cell * stamp.cellWorldX
            val wz = (f.gz[i] * grid.cell - stamp.centreZ) / grid.cell * stamp.cellWorldZ
            val s = (wx * cos + wz * sin) / size + 0.5f
            val t = (-wx * sin + wz * cos) / size + 0.5f
            if (s < 0f || s > 1f || t < 0f || t > 1f) continue
            val value = image.sample(s, t) * heightScale
            val h = grid.at(f.gx[i], f.gz[i])
            val target = ref + value
            out[i] = f.weight[i] * when (blend) {
                StampBlend.ADD -> value
                StampBlend.MAX -> maxOf(0f, target - h)
                StampBlend.MIN -> minOf(0f, target - h)
            }
        }
    }
}
