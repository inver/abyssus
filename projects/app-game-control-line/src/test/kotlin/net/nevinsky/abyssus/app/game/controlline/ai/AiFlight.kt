/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.flight.AIRBORNE_HEIGHT
import net.nevinsky.abyssus.app.game.controlline.flight.CONTROL_TENSION
import net.nevinsky.abyssus.app.game.controlline.flight.FlightControlSource
import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import net.nevinsky.abyssus.app.game.controlline.flight.FlightSession
import net.nevinsky.abyssus.app.game.controlline.flight.fieldSession
import net.nevinsky.abyssus.app.game.controlline.pattern.Figure
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternJudge
import net.nevinsky.abyssus.app.game.controlline.pattern.SCHEDULE
import net.nevinsky.abyssus.app.game.controlline.pattern.SheetEntry
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A Watch AI flight of the bundled field's [planeName] through the production session and bot, judged by the normal
 * judge: what the tests of the bot read. [change] edits the plane first; [schedule] is what the bot flies.
 */
class AiFlight(
    planeName: String = "Stunter", schedule: List<Figure> = SCHEDULE, change: PlaneComponent.() -> Unit = {},
) : AutoCloseable {
    lateinit var judge: PatternJudge
        private set
    lateinit var bot: F2bBot
        private set

    /** The phases the bot went through, with the manoeuvre and the flight time each began at. */
    val phases = ArrayList<Triple<BotPhase, Figure?, Float>>()

    /** The lowest line tension (N) the bot saw in the air after the take-off's climb. */
    var minTension = Float.MAX_VALUE
        private set

    /** The longest time (s) the lines stayed below [CONTROL_TENSION] in the air after the take-off's climb. */
    var longestSlack = 0f
        private set
    private var slackSince: Float? = null

    /** The flight time at which the bot first saw the engine stopped, or null. */
    var engineStopped: Float? = null
        private set

    /** The last observation the bot saw. */
    var last: FlightObservation? = null
        private set

    val session: FlightSession = fieldSession(planeName, change) { plane, mass ->
        judge = PatternJudge(plane.lineLength)
        bot = f2bBot(plane, mass, judge::guideInput, schedule)
        val inner = bot
        judge to FlightControlSource { o ->
            val tilt = inner.tilt(o)
            val planner = inner.planner
            if (phases.lastOrNull()?.let { it.first != planner.phase || it.second != planner.figure } != false) {
                phases += Triple(planner.phase, planner.figure, o.time)
            }
            if (o.time > 20f && o.height > AIRBORNE_HEIGHT) {
                minTension = min(minTension, o.tension)
                if (o.tension < CONTROL_TENSION) {
                    val since = slackSince ?: o.time.also { slackSince = it }
                    longestSlack = maxOf(longestSlack, o.time - since + PHYSICS_STEP)
                } else slackSince = null
            }
            if (!o.engineRunning && engineStopped == null) engineStopped = o.time
            last = o
            tilt
        }
    }

    val result: FlightEnd? get() = session.flight.result
    val time: Float get() = session.flight.time

    /** The sample at the end of the flight (the touchdown, for a landing). */
    val lastSample: TrackSample? get() = session.flight.lastSample

    /** How far from the pilot's ground point the plane was at its last sample, horizontally (m): its centre of gravity. */
    val horizontal: Float get() = lastSample?.offset?.let { sqrt(it.x * it.x + it.z * it.z) } ?: Float.NaN

    /**
     * How far from the pilot's ground point the lines meet the plane (its leadouts) now, horizontally (m): the farther of
     * the two. After a landing, where it touched down inside the flying circle.
     */
    val lineEnds: Float get() {
        val ground = session.rig.handle.sub(0f, session.rig.pilotSettings.handleHeight, 0f)
        return session.rig.lines.maxOf { line ->
            val a = Vector3()
            val b = Vector3()
            line.anchors(a, b)
            b.sub(ground)
            sqrt(b.x * b.x + b.z * b.z)
        }
    }

    /** Advances in [frames] until the flight ends or [limit] seconds of flight have passed. */
    fun fly(limit: Float = 430f, frames: Iterator<Float> = generateSequence { 1f / 60f }.iterator(), until: () -> Boolean = { false }) {
        while (session.flight.result == null && session.flight.time < limit && !until()) session.advance(frames.next())
    }

    fun entry(figure: Figure): SheetEntry? = judge.sheet[figure]

    /** The sheet, the end and the bot's phases, for a failure message. */
    fun describe(): String = buildString {
        appendLine("end $result at %.2f s, impact %.2f m/s, centre of gravity %.2f m and line ends %.2f m from the pilot".format(time, session.flight.impactSpeed, horizontal, lineEnds))
        lastSample?.let { appendLine("last sample: height %.2f m, elevation %.1f°, climb %.1f°, inverted %s, engine %s".format(it.height, it.elevation, it.climb, it.inverted, it.engineRunning)) }
        appendLine("bot phase ${bot.planner.phase} ${bot.planner.figure}, diagnostics ${bot.planner.diagnostics}")
        appendLine("lowest tension in the air %.1f N, longest below control tension %.2f s, engine stopped at %s s".format(minTension, longestSlack, engineStopped))
        for (f in SCHEDULE) {
            val e = judge.sheet[f]
            appendLine("  ${f.label}: " + when {
                e == null -> "undecided"
                e.reason != null -> "0 (${e.reason.label}) at %.1f s".format(e.time)
                else -> "%.1f at %.1f s".format(e.tenths / 10f, e.time)
            })
        }
    }

    override fun close() = session.close()
}
