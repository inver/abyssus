/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

/**
 * A rectangle of mask texels: [minX]..[maxX] along x and [minZ]..[maxZ] along z, both inclusive; empty when either
 * span is inverted. A brush stroke reports the texels it changed, and the draft turns them into dirty chunks.
 */
data class MaskRect(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int) {

    /** True when the rectangle holds no texel. */
    val isEmpty: Boolean
        get() = maxX < minX || maxZ < minZ

    /** How many texels the rectangle spans along x; 0 when empty. */
    val width: Int
        get() = if (isEmpty) 0 else maxX - minX + 1

    /** How many texels the rectangle spans along z; 0 when empty. */
    val height: Int
        get() = if (isEmpty) 0 else maxZ - minZ + 1

    /** The smallest rectangle holding both rectangles; an empty one leaves the other as it is. */
    fun union(other: MaskRect): MaskRect = when {
        isEmpty -> other
        other.isEmpty -> this
        else -> MaskRect(
            minOf(minX, other.minX),
            minOf(minZ, other.minZ),
            maxOf(maxX, other.maxX),
            maxOf(maxZ, other.maxZ),
        )
    }
}

/** The rectangle that holds no texel: what a stroke reports when no mask value changed. */
fun emptyMaskRect(): MaskRect = MaskRect(0, 0, -1, -1)
