/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.io

/** The rule of a score board: the [size] best entries by [score], best first and earlier ones first among equals. */
class RankedBoard<E>(private val size: Int, private val score: (E) -> Double) {
    fun ranked(list: List<E>): List<E> = list.sortedByDescending(score).take(size)

    /** Whether an entry scoring [value] would enter [board]. */
    fun qualifies(board: List<E>, value: Double): Boolean = board.size < size || value > score(board.last())
}
