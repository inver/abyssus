/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modellights

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import kotlin.math.floor

/** The most lights one use of Create Lights From Model adds. */
const val LAMP_LIGHT_MAX = 64

/** The intensity of a new lamp light. */
const val LAMP_INTENSITY = 1f

/** A lamp light reaches this many times the largest dimension of its lamp... */
const val LAMP_RANGE_FACTOR = 6f

/** ...but never less than this many world units. */
const val LAMP_MIN_RANGE = 3f

/** Above this many pieces the nearest-pair search first groups pieces into a grid, to stay fast. */
private const val PAIRWISE_LIMIT = 1024

/** The lights' color: the emissive color of the material, or white when it has none. */
fun lampColor(emissive: Color?): Color =
    emissive?.takeIf { it.r > 0f || it.g > 0f || it.b > 0f }?.let { Color(it.r, it.g, it.b, 1f) }
        ?: Color(1f, 1f, 1f, 1f)

/** The range of the light of [piece] (already in world units). */
fun lampRange(piece: LampPiece): Float = maxOf(LAMP_MIN_RANGE, LAMP_RANGE_FACTOR * piece.largest)

/** [pieces] after merging, and whether any were merged. */
class CappedPieces(val pieces: List<LampPiece>, val merged: Boolean)

/**
 * At most [max] pieces: while there are more, the two nearest centres (the lowest pair on a tie) become one piece with
 * their joint bounds. The result is in the order of the input's remaining pieces and is the same for the same input.
 */
fun capPieces(pieces: List<LampPiece>, max: Int): CappedPieces {
    require(max >= 1) { "max must be at least 1" }
    if (pieces.size <= max) {
        return CappedPieces(pieces, false)
    }
    val start =
        if (pieces.size > PAIRWISE_LIMIT) {
            gridReduced(pieces, max)
        } else {
            pieces
        }
    val list = start.toMutableList()
    while (list.size > max) {
        var bestA = 0
        var bestB = 1
        var best = Float.MAX_VALUE
        for (a in 0 until list.size) {
            for (b in a + 1 until list.size) {
                val d = list[a].center.dst2(list[b].center)
                if (d < best) {
                    best = d
                    bestA = a
                    bestB = b
                }
            }
        }
        list[bestA] = list[bestA].merged(list[bestB])
        list.removeAt(bestB)
    }
    return CappedPieces(list, true)
}

/** Groups pieces into the coarsest grid that still has at least [min] occupied cells; one merged piece per cell. */
private fun gridReduced(pieces: List<LampPiece>, min: Int): List<LampPiece> {
    val low = Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    val high = Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    for (p in pieces) {
        low.set(minOf(low.x, p.center.x), minOf(low.y, p.center.y), minOf(low.z, p.center.z))
        high.set(maxOf(high.x, p.center.x), maxOf(high.y, p.center.y), maxOf(high.z, p.center.z))
    }
    val extent = maxOf(high.x - low.x, high.y - low.y, high.z - low.z)
    if (extent <= 0f) {
        return pieces
    }
    fun cells(size: Float): Map<Triple<Long, Long, Long>, List<LampPiece>> =
        pieces.groupBy {
            Triple(
                floor((it.center.x - low.x) / size).toLong(),
                floor((it.center.y - low.y) / size).toLong(),
                floor((it.center.z - low.z) / size).toLong(),
            )
        }
    var small = extent / (pieces.size * 4f)
    var large = extent
    // the occupied cell count only falls as the cells grow: keep the largest size that has at least [min] cells
    var answer = cells(small)
    if (answer.size < min) {
        return pieces
    }
    repeat(24) {
        val middle = (small + large) / 2
        val grouped = cells(middle)
        if (grouped.size >= min) {
            small = middle
            answer = grouped
        } else {
            large = middle
        }
    }
    return answer.entries
        .sortedWith(compareBy({ it.key.first }, { it.key.second }, { it.key.third }))
        .map { (_, members) -> members.reduce(LampPiece::merged) }
}
