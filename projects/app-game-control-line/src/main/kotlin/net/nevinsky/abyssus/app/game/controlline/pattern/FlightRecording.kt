/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import com.fasterxml.jackson.annotation.JsonIgnore
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import kotlin.math.max
import kotlin.math.roundToInt

/** The version of the recording files this game reads and writes. */
const val RECORDING_VERSION = 1

/** A recording keeps one sample in this many physics steps: 30 Hz at 120 Hz. */
const val RECORDING_STRIDE = 4

/** The ideal track is kept as one point per this many metres. */
const val IDEAL_SPACING = 0.25f

/** The numbers of one [FlightRecording] sample: the time, the offset (x, y, z) and the rotation (x, y, z, w). */
const val SAMPLE_STRIDE = 8

/** One deduction as it was judged: the [measured] error cost [tenths] tenths of a mark, outside the [tolerance]. */
data class RecordedDeduction(val criterion: Criterion, val measured: Float, val tolerance: Float, val tenths: Int)

/**
 * One manoeuvre as it was judged, from [start] to [end] seconds into the flight. [tenths] is its mark (0 with a [reason]),
 * [ideal] its ideal track as world offsets from the pilot's ground point (x, y, z in turn; empty when it was not whole).
 */
class RecordedJudgement(
    val index: Int, val start: Float, val end: Float, val tenths: Int, val reason: ZeroReason?,
    val deductions: List<RecordedDeduction>, val ideal: FloatArray,
) {
    @get:JsonIgnore
    val figure: Figure get() = SCHEDULE[index]
}

/**
 * A flight as the debrief replays it (design decision 1): the plane's path at 30 samples a second, [SAMPLE_STRIDE]
 * numbers each (see there), and a [judgement][RecordedJudgement] per manoeuvre that was decided. Numbers have three
 * decimals, so a saved recording reads back exactly as it was kept.
 */
class FlightRecording(
    val plane: String, val lineLength: Float, val level: AssistLevel, val samples: FloatArray, val judgements: List<RecordedJudgement>,
    val version: Int = RECORDING_VERSION,
) {
    /** How many samples there are. */
    @get:JsonIgnore
    val count: Int get() = samples.size / SAMPLE_STRIDE

    /** The judgement of the manoeuvre at [index] of the schedule, or null when it was not decided. */
    fun judgement(index: Int): RecordedJudgement? = judgements.firstOrNull { it.index == index }

    fun time(i: Int): Float = samples[i * SAMPLE_STRIDE]

    /** The offset of sample [i] into [out]. */
    fun offset(i: Int, out: Vector3): Vector3 = out.set(samples[i * SAMPLE_STRIDE + 1], samples[i * SAMPLE_STRIDE + 2], samples[i * SAMPLE_STRIDE + 3])

    /** The rotation of sample [i] into [out]. */
    fun rotation(i: Int, out: Quaternion): Quaternion =
        out.set(samples[i * SAMPLE_STRIDE + 4], samples[i * SAMPLE_STRIDE + 5], samples[i * SAMPLE_STRIDE + 6], samples[i * SAMPLE_STRIDE + 7])
}

private fun round3(v: Float): Float = (v * 1000f).roundToInt() / 1000f

/**
 * Builds a [FlightRecording] as a flight goes: [add] takes every step's sample and keeps each [RECORDING_STRIDE]th, and
 * [build] adds the judgements of a finished [sheet].
 */
class RecordingBuilder(private val radius: Float) {
    private val geometry = HemisphereGeometry(radius)
    private var samples = FloatArray(SAMPLE_STRIDE * 1024)
    private var count = 0
    private var steps = 0

    fun add(sample: TrackSample) {
        val keep = steps % RECORDING_STRIDE == 0
        steps++
        if (!keep) return
        if ((count + 1) * SAMPLE_STRIDE > samples.size) samples = samples.copyOf(samples.size * 2)
        val at = count * SAMPLE_STRIDE
        val q = sample.rotation
        for ((k, v) in floatArrayOf(sample.time, sample.offset.x, sample.offset.y, sample.offset.z, q.x, q.y, q.z, q.w).withIndex()) {
            samples[at + k] = round3(v)
        }
        count++
    }

    fun build(plane: String, level: AssistLevel, sheet: ScoreSheet, weights: MarkWeights = MarkWeights()): FlightRecording {
        val judgements = sheet.entries.mapIndexedNotNull { index, entry -> entry?.let { judgement(index, it, weights) } }
        return FlightRecording(plane, radius, level, samples.copyOf(count * SAMPLE_STRIDE), judgements)
    }

    private fun judgement(index: Int, entry: SheetEntry, weights: MarkWeights): RecordedJudgement {
        val graded = entry.graded
        val deductions = graded?.deductions.orEmpty().map {
            RecordedDeduction(it.criterion, round3(it.measured), round3(weights.of(it.criterion).tolerance), (it.amount * 10f).roundToInt())
        }
        return RecordedJudgement(index, round3(entry.start), round3(max(entry.start, entry.time)), entry.tenths, entry.reason, deductions, thinned(graded?.ideal.orEmpty()))
    }

    /** The ideal track's directions as world offsets from the pilot's ground point, one point per [IDEAL_SPACING] metres. */
    private fun thinned(directions: List<Vector3>): FloatArray {
        val out = ArrayList<Float>()
        var last: Vector3? = null
        for ((i, u) in directions.withIndex()) {
            val p = geometry.position(u)
            if (last == null || i == directions.size - 1 || p.dst(last) >= IDEAL_SPACING) {
                out += round3(p.x)
                out += round3(p.y)
                out += round3(p.z)
                last = p
            }
        }
        return out.toFloatArray()
    }
}
