/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

/**
 * The 15 scored manoeuvres of the F2B schedule, in order, with their difficulty factor [k] (FAI Sporting Code Volume F2,
 * 2027 edition, rule 4.2.12). [repeats] is how many repeat figures the manoeuvre has (loops, squares, eights); 1 for the
 * single figures.
 */
enum class Figure(val label: String, val k: Int, val repeats: Int = 1) {
    TAKEOFF("Take-off", 2),
    REVERSE_WINGOVER("Reverse wingover", 8),
    INSIDE_LOOPS("Three inside loops", 6, 3),
    INVERTED_LAPS("Two laps of inverted flight", 2, 2),
    OUTSIDE_LOOPS("Three outside loops", 6, 3),
    INSIDE_SQUARES("Two inside square loops", 12, 2),
    OUTSIDE_SQUARES("Two outside square loops", 12, 2),
    INSIDE_TRIANGLES("Two inside triangular loops", 14, 2),
    HORIZONTAL_EIGHTS("Two horizontal eights", 7, 2),
    SQUARE_EIGHTS("Two square horizontal eights", 18, 2),
    VERTICAL_EIGHTS("Two vertical eights", 10, 2),
    HOURGLASS("Hourglass", 10),
    OVERHEAD_EIGHTS("Two overhead eights", 10, 2),
    CLOVER("Four-leaf clover", 8, 4),
    LANDING("Landing", 5),
}

/** The schedule in order. */
val SCHEDULE: List<Figure> = Figure.entries

/** The sum of the K-factors: 130. */
val TOTAL_K: Int = SCHEDULE.sumOf { it.k }

/** The best possible score: every mark 10. */
val MAX_SCORE: Int = TOTAL_K * 10

/** The flight time (s) within which the schedule is flown. */
const val FLIGHT_TIME = 420f

/** The pause between two manoeuvres, in laps. */
const val PAUSE_LAPS = 1.5f
