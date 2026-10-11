/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.flight.FlightJudge
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import kotlin.math.abs
import kotlin.math.max

/** Where the plane flies between manoeuvres, for the Mid assist level's warning. */
enum class HeightWarning { BELOW, ABOVE }

/** Level flight between manoeuvres lower than this above the ground (m) is too low... */
const val PAUSE_HEIGHT_MIN = 1f

/** ...and higher than this too high. */
const val PAUSE_HEIGHT_MAX = 3f

/** Level flight below this latitude (degrees) for half a lap is a pause: the plane has settled, wherever it is. */
const val PAUSE_LATITUDE = 70f

/** A wingover's inverted half lap is a pause of this many degrees or more only when it goes on past the lap's half. */
private const val WINGOVER_PAUSE = 300f

/** How far above the ground at the start the plane has to be to have left it (m). */
private const val LIFTOFF = 0.1f

/** The azimuth, in degrees, from the release at which the take-off ends: 3 laps. */
private const val TAKEOFF_AZIMUTH = 1080f

/**
 * The F2B judge (design decision 3): a sequencer that finds each manoeuvre in the flown path, in schedule order, and marks
 * it. It reads the samples of a flight from the start of its ground roll, and fills in a [ScoreSheet].
 *
 * A burst of flying between two pauses (level flight for half a lap or more) is one manoeuvre, found by [Matchers] and
 * marked by [Grader]. A manoeuvre already passed is not scored again; one later than the expected scores the ones skipped
 * 0 as omitted. A whole manoeuvre scores its mark; otherwise it scores 0 with its reason.
 */
class PatternJudge(private val radius: Float, private val grader: Grader = Grader(radius)) : FlightJudge {
    private val recorder = RecordingBuilder(radius)

    private val path = PathTokens(radius)
    private val matchers = Matchers(radius)

    /** The score sheet so far. */
    val sheet = ScoreSheet()

    /** The manoeuvre expected next; null once all are decided. */
    var expected: Figure? = Figure.TAKEOFF
        private set

    /** The flight time (s) of the last sample. */
    var clock = 0f
        private set

    /** The entry decided last, and how many have been decided (for the toast). */
    var lastScored: SheetEntry? = null
        private set
    var decided = 0
        private set

    /** Laps flown since the last manoeuvre ended, or null before the first. */
    var pauseLaps: Float? = null
        private set

    /** Whether the plane flies level between manoeuvres outside 1 to 3 m above the ground. */
    var heightWarning: HeightWarning? = null
        private set

    /** Whether the expected manoeuvre has started: the take-off has left the ground, or flying other than level has begun since the last manoeuvre ended. */
    var started = false
        private set

    /** What the Noob guide reads: where the plane is and where the schedule has got to. */
    fun guideInput(): GuideInput {
        val last = path.points.lastOrNull()
        return GuideInput(expected, pauseLaps, started, last?.u?.let { Vector3(it) }, last?.engineRunning ?: true)
    }

    private var lastEnd = -1 // point index
    private var lastEndAzimuth = 0f
    private var lastEndTime = 0f
    private var liftoff = -1
    private var finished = false

    /** The recording of the flight so far, for [plane] flown at [level]. */
    fun recording(plane: String, level: AssistLevel): FlightRecording = recorder.build(plane, level, sheet, grader.weights)

    override fun sample(sample: TrackSample) {
        if (finished) return
        recorder.add(sample)
        clock = sample.time
        path.feed(sample)
        val points = path.points
        if (points.isEmpty()) return
        val last = points.last()
        pauseLaps = if (lastEnd >= 0) (last.azimuth - lastEndAzimuth) / 360f else null
        val open = path.peekOpen()
        heightWarning = if (expected != null && lastEnd >= 0 && open is Level && open.latitude < BASE_LATITUDE) {
            when {
                last.height < PAUSE_HEIGHT_MIN -> HeightWarning.BELOW
                last.height > PAUSE_HEIGHT_MAX -> HeightWarning.ABOVE
                else -> null
            }
        } else null
        when (expected) {
            null, Figure.LANDING -> Unit
            Figure.TAKEOFF -> takeoff()
            else -> sequence(open)
        }
        started = startedNow(path.peekOpen())
    }

    private fun startedNow(open: Token?): Boolean = when (val figure = expected) {
        null, Figure.LANDING -> false
        Figure.TAKEOFF -> liftoff >= 0
        else -> burst().isNotEmpty() || (open != null && open.from > lastEnd && when (open) {
            // level flight is the pause, except the inverted laps themselves
            is Level -> open.latitude >= PAUSE_LATITUDE || (!open.upright && figure == Figure.INVERTED_LAPS)
            else -> true
        })
    }

    // take-off: from the start of the ground roll to 3 laps after it

    private fun takeoff() {
        val points = path.points
        if (liftoff < 0) {
            val index = points.indexOfFirst { it.height > points[0].height + LIFTOFF }
            if (index < 0) return
            liftoff = index
        }
        val end = points.indexOfFirst { it.azimuth - points[0].azimuth >= TAKEOFF_AZIMUTH }
        if (end < 0) return
        val start = points[0].time
        if (points[end].time > FLIGHT_TIME) zero(Figure.TAKEOFF, ZeroReason.AFTER_TIME, points[end].time, start)
        else decide(Figure.TAKEOFF, grader.gradeTakeoff(points, liftoff, end), points[end].time, start)
        ended(end)
        expected = Figure.REVERSE_WINGOVER
    }

    // everything between: a burst of flying, then a pause

    private fun burst(): List<Token> = path.tokens.filter { it.from > lastEnd && !isPause(it) }

    private fun isPause(token: Token) = token is Level && token.latitude < PAUSE_LATITUDE && abs(token.azimuthSpan) >= PAUSE_AZIMUTH

    private fun sequence(open: Token?) {
        if (open !is Level || open.latitude >= PAUSE_LATITUDE || abs(open.azimuthSpan) < PAUSE_AZIMUTH) return
        val figure = expected ?: return
        // inverted laps: the first two laps of an inverted pause long enough for them
        if (!open.upright && abs(open.azimuthSpan) >= 720f && figure.ordinal <= Figure.INVERTED_LAPS.ordinal && burst().isEmpty()) {
            invertedLaps(open)
            return
        }
        val tokens = burst()
        if (tokens.isEmpty()) return
        val recognition = matchers.recognise(tokens, path.points)
        if (recognition == null) {
            // not one of the schedule's figures: nothing to score, but it is over
            lastEnd = max(lastEnd, tokens.last().to)
            return
        }
        // a wingover goes on through half a lap inverted: not yet a pause
        if (recognition.figure == Figure.REVERSE_WINGOVER && recognition.repeats == 1 && !open.upright && abs(open.azimuthSpan) < WINGOVER_PAUSE) return
        score(recognition, tokens)
        lastEnd = max(lastEnd, tokens.last().to)
    }

    private fun invertedLaps(open: Level) {
        val points = path.points
        val from = open.from
        val end = (from until points.size).first { points[it].azimuth - points[from].azimuth >= 720f }
        val recognition = Recognition(Figure.INVERTED_LAPS, Verdict.COMPLETE, from, end, 2, geometry.azimuth(points[from].u))
        score(recognition, listOf(open))
    }

    private val geometry = HemisphereGeometry(radius)

    /** Scores [recognition], found among [tokens]; the manoeuvres it skips score 0. */
    private fun score(recognition: Recognition, tokens: List<Token>) {
        val index = recognition.figure.ordinal
        val next = expected?.ordinal ?: return
        if (index < next) return // already passed
        for (skipped in next until index) zero(SCHEDULE[skipped], ZeroReason.OMITTED, clock)
        val points = path.points
        val figure = recognition.figure
        val time = points[recognition.to].time
        val start = points[recognition.from].time
        val soon = recognition.from.let { lastEnd >= 0 && (points[it].azimuth - lastEndAzimuth) / 360f < PAUSE_LAPS }
        when {
            recognition.verdict == Verdict.WRONG_REPEATS -> zero(figure, ZeroReason.WRONG_REPEATS, time, start)
            recognition.verdict == Verdict.NOT_COMPLETED -> zero(figure, ZeroReason.NOT_COMPLETED, time, start)
            soon -> zero(figure, ZeroReason.TOO_SOON, time, start)
            time > FLIGHT_TIME -> zero(figure, ZeroReason.AFTER_TIME, time, start)
            else -> decide(figure, grader.grade(recognition, points, tokens), time, start)
        }
        ended(recognition.to)
        expected = SCHEDULE.getOrNull(index + 1)
        // the clover is the last judged figure; the landing follows
    }

    private fun ended(pointIndex: Int) {
        val point = path.points[pointIndex]
        lastEnd = pointIndex
        lastEndAzimuth = point.azimuth
        lastEndTime = point.time
        pauseLaps = 0f
    }

    private fun decide(figure: Figure, graded: Graded, time: Float, start: Float = time) =
        record(SheetEntry(figure, graded.tenths, null, graded, time, start))

    private fun zero(figure: Figure, reason: ZeroReason, time: Float, start: Float = time) =
        record(SheetEntry(figure, 0, reason, null, time, start))

    private fun record(entry: SheetEntry) {
        sheet[entry.figure] = entry
        lastScored = entry
        decided++
    }

    // the end of the flight

    override fun ended(end: FlightEnd) {
        if (finished) return
        finished = true
        path.flush()
        val remaining = expected
        when (end) {
            FlightEnd.CRASHED, FlightEnd.LINES_SLACK -> {
                // a manoeuvre that was whole before the crash keeps its mark
                if (remaining != null && remaining != Figure.TAKEOFF && remaining != Figure.LANDING) finishBurst(allowIncomplete = false)
                crashed()
            }
            FlightEnd.LANDED -> landed()
        }
    }

    private fun finishBurst(allowIncomplete: Boolean) {
        val tokens = burst()
        if (tokens.isEmpty()) return
        val recognition = matchers.recognise(tokens, path.points) ?: return
        if (!allowIncomplete && recognition.verdict != Verdict.COMPLETE) return
        score(recognition, tokens)
    }

    private fun crashed() {
        val from = expected?.ordinal ?: return
        for (i in from until SCHEDULE.size) zero(SCHEDULE[i], ZeroReason.CRASHED, clock)
        expected = null
    }

    private fun landed() {
        val points = path.points
        if (expected == Figure.TAKEOFF) {
            zero(Figure.TAKEOFF, ZeroReason.NOT_COMPLETED, clock)
            expected = Figure.REVERSE_WINGOVER
        }
        val remaining = expected
        if (remaining != null && remaining != Figure.LANDING) {
            finishBurst(allowIncomplete = true)
            val rest = expected ?: Figure.LANDING
            for (i in rest.ordinal until Figure.LANDING.ordinal) zero(SCHEDULE[i], ZeroReason.OMITTED, clock)
        }
        if (points.size < 3) {
            zero(Figure.LANDING, ZeroReason.NOT_COMPLETED, clock)
            expected = null
            return
        }
        val touchdown = points.size - 1
        val stopped = points.indexOfLast { it.engineRunning }.let { max(it + 1, 0) }
        // the glide begins at the last point on the base after the engine stopped; with none, where the engine stopped
        var from = touchdown - 1
        while (from > stopped && points[from].height < BASE_HEIGHT - GLIDE_START_TOLERANCE) from--
        from = max(from, stopped).coerceAtMost(touchdown - 1)
        val time = points[touchdown].time
        if (time > FLIGHT_TIME) {
            zero(Figure.LANDING, ZeroReason.AFTER_TIME, time, points[from].time)
        } else {
            val back = (touchdown downTo 0).firstOrNull { points[touchdown].time - points[it].time >= SINK_WINDOW } ?: 0
            val sink = max(0f, (points[back].height - points[touchdown].height) / max(points[touchdown].time - points[back].time, 1e-3f))
            decide(Figure.LANDING, grader.gradeLanding(points, from, touchdown, sink), time, points[from].time)
        }
        expected = null
    }

    private companion object {
        const val GLIDE_START_TOLERANCE = 0.3f
        const val SINK_WINDOW = 0.25f
    }
}
