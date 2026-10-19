/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.track.wrapDegrees
import java.util.Locale
import net.nevinsky.abyssus.lib.core.util.azimuthOf
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where the debrief looks from: the pilot's place, or from outside the circle. */
enum class DebriefView { PILOT, OUTSIDE }

/** What a drawn track is: the ideal one, the one flown, or a faint guide. */
enum class TrackKind { IDEAL, FLOWN, GUIDE }

/** A track to draw: [points] are world offsets from the pilot's ground point. */
class TrackLine(val kind: TrackKind, val points: List<Vector3>)

/** One line of the debrief's score sheet: [tenths] is the mark in tenths, [reason] is why it is 0, [decided] is false for a manoeuvre never judged. */
data class DebriefRow(val figure: Figure, val tenths: Int, val reason: ZeroReason?, val decided: Boolean = true)

/** The plane's place at a moment of the replay: an offset from the pilot's ground point and its attitude. */
class PlanePose(val position: Vector3, val rotation: Quaternion)

/** The points a figure takes up, for the cameras: world offsets from the pilot's ground point, flown on lines of [lineLength] metres. */
class DebriefFrame(val points: List<Vector3>, val lineLength: Float = 18f) {
    /** The middle of the points' bounding box. */
    val centre: Vector3 = run {
        val low = Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        val high = Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (p in points) {
            low.set(min(low.x, p.x), min(low.y, p.y), min(low.z, p.z))
            high.set(max(high.x, p.x), max(high.y, p.y), max(high.z, p.z))
        }
        if (points.isEmpty()) Vector3() else low.add(high).scl(0.5f)
    }

    /** The azimuth of [centre] round the pilot, in degrees as the track measures it (anticlockwise seen from above). */
    val azimuth: Float = if (centre.x == 0f && centre.z == 0f) 0f else azimuthOf(centre.x, centre.z)
}

/** The speeds the replay runs at. */
val DEBRIEF_SPEEDS = listOf(0.5f, 1f, 2f)

/** The guides run this many degrees of azimuth beyond the figure. */
private const val GUIDE_MARGIN = 10f

/** A guide arc has a point every this many degrees. */
private const val GUIDE_STEP = 2f

/** The message of a debrief with no replay. */
const val NO_REPLAY_MESSAGE = "Replay not available"

/**
 * The debrief of an F2B flight (design decision 3), without GL: its score sheet [rows] with one manoeuvre selected, the
 * [recording] it replays (null for a sheet-only debrief), the playback clock and the [view]. What to draw comes from
 * [segments], [planePose], [frame] and [deductionLines].
 */
class DebriefModel(val title: String, val rows: List<DebriefRow>, val recording: FlightRecording?) {
    /** Why there is no replay, or null when there is. */
    val notice: String? get() = if (recording == null) NO_REPLAY_MESSAGE else null

    /** The selected row, from 0; the first manoeuvre at the start. */
    var selected = 0
        private set

    /** Grows with every change of [selected], for the screen to rebuild itself. */
    var revision = 0
        private set

    var view = DebriefView.PILOT
        private set
    var playing = true
        private set
    var speed = 1f
        private set

    /** Seconds into the selected manoeuvre that the replay has reached. */
    var clock = 0f
        private set

    private val judgement: RecordedJudgement? get() = recording?.judgement(selected)

    /** How long the selected manoeuvre lasted (s); 0 when it has no span. */
    val duration: Float get() = judgement?.let { max(0f, it.end - it.start) } ?: 0f

    val row: DebriefRow get() = rows[selected]

    fun select(by: Int) {
        val next = (selected + by).coerceIn(0, rows.size - 1)
        if (next == selected) return
        selected = next
        clock = 0f
        revision++
    }

    fun toggleView() {
        view = if (view == DebriefView.PILOT) DebriefView.OUTSIDE else DebriefView.PILOT
    }

    fun togglePause() {
        playing = !playing
    }

    fun restart() {
        clock = 0f
        playing = true
    }

    /** Sets the replay speed to the [index]th of [DEBRIEF_SPEEDS]. */
    fun setSpeed(index: Int) {
        speed = DEBRIEF_SPEEDS[index.coerceIn(DEBRIEF_SPEEDS.indices)]
    }

    /** Moves the replay on by [seconds] of real time; at the end of the manoeuvre it holds. */
    fun advance(seconds: Float) {
        if (playing) clock = min(duration, clock + seconds * speed)
    }

    // what to draw

    /** The ideal and flown tracks of the selected manoeuvre and the guides over them; empty when there is nothing to show. */
    fun segments(): List<TrackLine> {
        val recording = recording ?: return emptyList()
        val j = judgement ?: return emptyList()
        val ideal = vectors(j.ideal)
        val flown = flown(recording, j)
        if (ideal.isEmpty() && flown.isEmpty()) return emptyList()
        val lines = ArrayList<TrackLine>()
        if (ideal.isNotEmpty()) lines += TrackLine(TrackKind.IDEAL, ideal)
        if (flown.isNotEmpty()) lines += TrackLine(TrackKind.FLOWN, flown)
        lines += guides(recording.lineLength, ideal.ifEmpty { flown })
        return lines
    }

    /** The plane's place and attitude at the replay's clock, or null without a recording. */
    fun planePose(): PlanePose? {
        val recording = recording ?: return null
        if (recording.count == 0) return null
        val start = judgement?.start ?: return null
        return poseAt(recording, start + clock)
    }

    /** The selected manoeuvre's figure, for the cameras. */
    fun frame(): DebriefFrame {
        val shown = segments().filter { it.kind != TrackKind.GUIDE }.flatMap { it.points }
        val length = recording?.lineLength ?: DEFAULT_LINE_LENGTH
        if (shown.isNotEmpty()) return DebriefFrame(shown, length)
        val pose = recording?.let { r -> judgement?.let { poseAt(r, it.start) } }
        return DebriefFrame(listOf(pose?.position ?: Vector3(length, BASE_HEIGHT, 0f)), length)
    }

    /** What the selected mark was taken off for, largest first; the reason for a 0; nothing for a sheet-only debrief. */
    fun deductionLines(): List<String> {
        val j = judgement ?: return emptyList()
        j.reason?.let { return listOf("Scored 0: ${it.label}") }
        if (j.deductions.isEmpty()) return listOf("No deductions")
        return j.deductions.sortedByDescending { it.tenths }.map {
            String.format(
                Locale.ROOT, "%s %.2f %s off (±%.2f): −%d.%d", it.criterion.label, it.measured, it.criterion.unit, it.tolerance,
                it.tenths / 10, it.tenths % 10,
            )
        }
    }

    // helpers

    private fun vectors(flat: FloatArray): List<Vector3> = List(flat.size / 3) { Vector3(flat[it * 3], flat[it * 3 + 1], flat[it * 3 + 2]) }

    private fun flown(recording: FlightRecording, j: RecordedJudgement): List<Vector3> {
        if (j.end <= j.start) return emptyList()
        val out = ArrayList<Vector3>()
        for (i in 0 until recording.count) {
            val t = recording.time(i)
            if (t in j.start..j.end) out += recording.offset(i, Vector3())
        }
        return out
    }

    private fun poseAt(recording: FlightRecording, time: Float): PlanePose {
        val last = recording.count - 1
        var high = 0
        while (high < last && recording.time(high) < time) high++
        val low = max(0, high - 1)
        val span = recording.time(high) - recording.time(low)
        val t = if (span > 1e-6f) ((time - recording.time(low)) / span).coerceIn(0f, 1f) else 0f
        val from = recording.offset(low, Vector3())
        val position = from.lerp(recording.offset(high, Vector3()), t)
        val rotation = recording.rotation(low, Quaternion()).nor().slerp(recording.rotation(high, Quaternion()).nor(), t)
        return PlanePose(position, rotation)
    }

    /** The base and the 45° parallel over the azimuth span of [track] plus the margin. */
    private fun guides(radius: Float, track: List<Vector3>): List<TrackLine> {
        val geometry = HemisphereGeometry(radius)
        val azimuths = track.filter { it.x != 0f || it.z != 0f }.map { azimuthOf(it.x, it.z) }
        if (azimuths.isEmpty()) return emptyList()
        // spread round the first, so a figure over the seam at 180° is still one span
        val reference = azimuths[azimuths.size / 2]
        val offsets = azimuths.map { wrapDegrees(it - reference) }
        var low = offsets.min() - GUIDE_MARGIN
        var high = offsets.max() + GUIDE_MARGIN
        if (high - low > 360f) {
            low = -180f
            high = 180f
        }
        val steps = max(1, ((high - low) / GUIDE_STEP).roundToInt())
        return listOf(0f, PARALLEL_45).map { latitude ->
            TrackLine(TrackKind.GUIDE, List(steps + 1) { geometry.position(geometry.direction(latitude, reference + low + (high - low) * it / steps)) })
        }
    }

    private companion object {
        const val DEFAULT_LINE_LENGTH = 18f
    }
}

/** The debrief of a flight with its [recording]: the sheet comes from its judgements. */
fun debriefOf(recording: FlightRecording): DebriefModel {
    val rows = SCHEDULE.mapIndexed { index, figure ->
        recording.judgement(index)?.let { DebriefRow(figure, it.tenths, it.reason) } ?: DebriefRow(figure, 0, null, decided = false)
    }
    return DebriefModel("${recording.plane} - ${recording.level.label}", rows, recording)
}

/** The debrief of a saved flight whose recording is missing or unreadable: its 15 marks, no replay. */
fun sheetOnlyDebrief(entry: PatternScoreEntry, level: AssistLevel): DebriefModel {
    val rows = SCHEDULE.mapIndexed { index, figure -> DebriefRow(figure, ((entry.marks.getOrNull(index) ?: 0.0) * 10).roundToInt(), null) }
    return DebriefModel("${entry.plane} - ${level.label} - ${entry.name}", rows, null)
}

/** The debrief of a flight just flown with no recording (a report built without one): the score sheet only. */
fun sheetOnlyDebrief(sheet: ScoreSheet, plane: String, level: AssistLevel): DebriefModel {
    val rows = SCHEDULE.map { figure -> sheet[figure]?.let { DebriefRow(figure, it.tenths, it.reason) } ?: DebriefRow(figure, 0, null, decided = false) }
    return DebriefModel("$plane - ${level.label}", rows, null)
}
