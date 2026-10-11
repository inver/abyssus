/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

/** Why a manoeuvre scored 0. */
enum class ZeroReason(val label: String) {
    OMITTED("omitted"),
    NOT_COMPLETED("not completed"),
    WRONG_REPEATS("wrong number of repeats"),
    TOO_SOON("too soon"),
    AFTER_TIME("after 7:00"),
    CRASHED("crashed"),
}

/**
 * One line of the score sheet: [figure] marked [tenths] tenths of a mark (10 to 100; 0 when it scored nothing, then with a
 * [reason]) at [time] seconds. A marked one keeps how it was [graded].
 */
class SheetEntry(val figure: Figure, val tenths: Int, val reason: ZeroReason?, val graded: Graded?, val time: Float) {
    /** The mark, 1 to 10 in steps of 0.1, or 0. */
    val mark: Float get() = tenths / 10f

    /** The points, mark times K, in tenths of a point. */
    val points: Int get() = tenths * figure.k
}

/** [tenths] tenths of a point as text with two decimals, `74.60` for 746 (the sum is exact, so nothing is rounded). */
fun formatPoints(tenths: Int): String = "${tenths / 10}.${tenths % 10}0"

/** The score sheet of an F2B flight: one entry per manoeuvre of the schedule once it is decided (design decision 5). */
class ScoreSheet {
    private val rows = arrayOfNulls<SheetEntry>(SCHEDULE.size)

    /** The entries in schedule order; null for a manoeuvre not decided yet. */
    val entries: List<SheetEntry?> get() = rows.toList()

    operator fun get(figure: Figure): SheetEntry? = rows[figure.ordinal]

    operator fun set(figure: Figure, entry: SheetEntry) {
        rows[figure.ordinal] = entry
    }

    /** The flight score in tenths of a point: the sum of mark × K. */
    val tenths: Int get() = rows.sumOf { it?.points ?: 0 }

    /** The flight score, with two decimals. */
    val score: String get() = formatPoints(tenths)

    /** The marks, one per manoeuvre (0 when it scored nothing or is undecided). */
    val marks: List<Float> get() = rows.map { it?.mark ?: 0f }
}
