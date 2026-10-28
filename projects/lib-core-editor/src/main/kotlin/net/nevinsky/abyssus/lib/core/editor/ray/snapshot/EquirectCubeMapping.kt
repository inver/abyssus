/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray.snapshot

import com.badlogic.gdx.math.Vector3
import java.util.stream.IntStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * How one cube face is laid out: [direction] is the axis it faces (+X, -X, +Y, -Y, +Z, -Z in that order), [right] and
 * [vertical] the directions of increasing x and of increasing row in its image, rows counted from the bottom.
 */
class CubeFaceAxes(val direction: Vector3, val right: Vector3, val vertical: Vector3)

/** One decoded cube face: 8-bit RGBA, [height] rows counted from the bottom. */
class CubeFaceImage(val width: Int, val height: Int, val rgba: ByteArray) {
    init {
        require(rgba.size.toLong() == width.toLong() * height * 4) { "A cube face needs width * height * 4 bytes" }
    }
}

/**
 * The resampling of six cube faces into an equirectangular image of [width] by [height] (centre column faces -Z, top row
 * is +Y), bilinear within each face. Which face and where on it each output pixel reads depends only on the output
 * size and the face [axes], so it is worked out once and reused for every [resample]. The raster cube map and a baked
 * sky share this, so both face the same way. Output rows run from the top.
 */
class EquirectCubeMapping(val width: Int, val height: Int, axes: List<CubeFaceAxes>) {
    init {
        require(axes.size == 6) { "A cube has six faces" }
    }

    private val faceOfPixel = ByteArray(width * height)
    private val fractionX = FloatArray(width * height)
    private val fractionY = FloatArray(width * height)

    init {
        IntStream.range(0, height).parallel().forEach { y ->
            val phi = (y + .5f) / height * PI.toFloat()
            for (x in 0 until width) {
                val theta = ((x + .5f) / width - .5f) * 2f * PI.toFloat()
                val dx = sin(phi) * sin(theta)
                val dy = cos(phi)
                val dz = -sin(phi) * cos(theta)
                val index = when {
                    abs(dx) >= abs(dy) && abs(dx) >= abs(dz) -> if (dx > 0) 0 else 1
                    abs(dy) >= abs(dz) -> if (dy > 0) 2 else 3
                    else -> if (dz > 0) 4 else 5
                }
                val face = axes[index]
                val forward = dx * face.direction.x + dy * face.direction.y + dz * face.direction.z
                val u = (dx * face.right.x + dy * face.right.y + dz * face.right.z) / forward
                val v = (dx * face.vertical.x + dy * face.vertical.y + dz * face.vertical.z) / forward
                val pixel = y * width + x
                faceOfPixel[pixel] = index.toByte()
                fractionX[pixel] = (u + 1f) / 2f
                fractionY[pixel] = (v + 1f) / 2f
            }
        }
    }

    /** The equirectangular 8-bit RGBA image (alpha 255) of [faces], six images in +X, -X, +Y, -Y, +Z, -Z order. */
    fun resample(faces: List<CubeFaceImage>): ByteArray {
        require(faces.size == 6) { "A cube has six faces" }
        val out = ByteArray(width * height * 4)
        IntStream.range(0, height).parallel().forEach { y ->
            for (x in 0 until width) {
                val pixel = y * width + x
                val face = faces[faceOfPixel[pixel].toInt()]
                // texel centres sit at +.5, so a position between two centres blends them
                val fx = (fractionX[pixel] * face.width - .5f).coerceIn(0f, face.width - 1f)
                val fy = (fractionY[pixel] * face.height - .5f).coerceIn(0f, face.height - 1f)
                val x0 = fx.toInt()
                val y0 = fy.toInt()
                val x1 = minOf(x0 + 1, face.width - 1)
                val y1 = minOf(y0 + 1, face.height - 1)
                val wx = fx - x0
                val wy = fy - y0
                val a = (y0 * face.width + x0) * 4
                val b = (y0 * face.width + x1) * 4
                val c = (y1 * face.width + x0) * 4
                val d = (y1 * face.width + x1) * 4
                val i = pixel * 4
                for (channel in 0 until 3) {
                    val top = (face.rgba[a + channel].toInt() and 255) * (1f - wx) + (face.rgba[b + channel].toInt() and 255) * wx
                    val bottom = (face.rgba[c + channel].toInt() and 255) * (1f - wx) + (face.rgba[d + channel].toInt() and 255) * wx
                    out[i + channel] = (top * (1f - wy) + bottom * wy + .5f).toInt().toByte()
                }
                out[i + 3] = 255.toByte()
            }
        }
        return out
    }
}
