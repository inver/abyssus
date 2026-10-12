/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import net.nevinsky.abyssus.app.game.controlline.flight.FieldFlight
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import net.nevinsky.abyssus.app.game.controlline.track.wrapDegrees
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The flyability spike: what `Stunter` does under scripted handle input. Prints its numbers. */
class PatternFlyabilityTest {
    private val lineLength = 18f

    /** One recorded step. */
    private class Step(val time: Float, val climb: Float, val speed: Float, val azimuth: Float, val elevation: Float, val height: Float)

    /** Flies [f] with a pilot following [target], records every step until [done] or [limit] seconds. */
    private fun run(f: FieldFlight, limit: Float, target: (TrackSample) -> Float?, done: () -> Boolean): List<Step> {
        val pilot = ScriptedPilot(f).also { it.target = target }
        val steps = ArrayList<Step>()
        pilot.until(limit) {
            val s = f.flight.lastSample!!
            steps += Step(s.time, s.climb, f.speed, s.azimuth, s.elevation, f.height)
            done()
        }
        return steps
    }

    /** The radius (m) of the turn between the steps where the unwrapped climb is at [from] and [to] degrees. */
    private fun cornerRadius(steps: List<Step>, from: Float, to: Float): Float? {
        var unwrapped = steps.first().climb
        var last = steps.first().climb
        var tFrom: Step? = null
        var tTo: Step? = null
        val speeds = ArrayList<Float>()
        for (s in steps) {
            unwrapped += wrapDegrees(s.climb - last)
            last = s.climb
            if (tFrom == null && (unwrapped - from) * (to - from) >= 0f) tFrom = s
            if (tFrom != null && tTo == null) {
                speeds += s.speed
                if ((unwrapped - to) * (to - from) >= 0f) tTo = s
            }
        }
        if (tFrom == null || tTo == null || tTo.time <= tFrom.time) return null
        val omega = Math.toRadians(abs(to - from).toDouble()).toFloat() / (tTo.time - tFrom.time)
        return speeds.average().toFloat() / omega
    }

    /** The radius of `Stunter`'s tightest corner (m): a full-tilt turn from 10 to 80 degrees of climb. */
    @Test
    fun fullTiltCornersAreAtMostThreeMetres() = FieldFlight("Stunter").use { f ->
        f.fly(12f)
        f.rig.tilt = 1f
        val steps = ArrayList<Step>()
        f.fly(1f) { val s = f.flight.lastSample!!; steps += Step(s.time, s.climb, f.speed, s.azimuth, s.elevation, f.height) }
        val radius = checkNotNull(cornerRadius(steps, 10f, 80f)) { "the plane did not turn 70 degrees in a second" }
        println("full tilt corner radius $radius m")
        assertTrue("corner radius $radius m", radius <= 3f)
    }

    /** Half tilt from level flight loops over the top, reaching the 45 degree parallel. */
    @Test
    fun aHalfTiltLoopReachesTheFortyFiveDegreeParallel() = FieldFlight("Stunter").use { f ->
        f.fly(12f)
        var top = 0f
        f.fly(3f, tilt = 0.6f) { top = max(top, f.flight.lastSample!!.elevation) }
        println("half tilt loop top $top degrees")
        assertTrue("loop top $top degrees", top >= 45f)
    }

    @Test
    fun spike() {
        // the plane levelled on the 45-degree parallel: climb to 45 degrees of elevation and turn level there
        FieldFlight("Stunter").use { f ->
            f.fly(12f)
            println("level: speed ${f.speed} m/s height ${f.height} m")
            // an inside loop of angular radius 22.5 degrees: the climb angle grows with the distance flown
            val rho = Math.toRadians(22.5).toFloat() * lineLength
            var flown = 0f
            var maxElevation = 0f
            var minHeight = 99f
            val steps = run(f, 10f, { s ->
                flown += f.speed / 120f
                maxElevation = max(maxElevation, s.elevation)
                minHeight = min(minHeight, f.height)
                Math.toDegrees((flown / rho).toDouble()).toFloat()
            }) { flown >= 2 * Math.PI.toFloat() * rho }
            println("loop: top elevation $maxElevation, ended after ${steps.size / 120f} s")
        }
        for ((name, corners) in listOf("square" to 90f, "triangle" to 120f)) {
            FieldFlight("Stunter").use { f ->
                f.fly(12f)
                var phase = 0
                var startAz = 0f
                var legStart = 0f
                val lead = 14f
                val steps = run(f, 12f, { s ->
                    val inside45 = 45f
                    when (phase) {
                        0 -> { legStart = s.azimuth; phase = 1 }
                    }
                    when (phase) {
                        1 -> if (s.elevation >= inside45 - lead) { phase = 2; startAz = s.azimuth }
                        2 -> if (abs(wrapDegrees(s.azimuth - startAz)) >= 45f - 8f) phase = 3
                        3 -> if (s.elevation <= lead * 0.5f) phase = 4
                    }
                    when (phase) {
                        1 -> if (corners == 90f) 90f else 60f
                        2 -> 0f
                        3 -> if (corners == 90f) -90f else -60f
                        else -> 0f
                    }
                }) { phase == 4 }
                println("$name: phases done=${phase == 4} after ${steps.size / 120f} s")
                val up = cornerRadius(steps, 10f, 80f)
                val top = cornerRadius(steps, 80f, 10f)
                println("$name: radius of the bottom-to-vertical corner $up m, vertical-to-top $top m")
                println("$name: climb range ${steps.minOf { it.climb }}..${steps.maxOf { it.climb }}, climbs " + steps.filterIndexed { i, _ -> i % 12 == 0 }.joinToString(" ") { "%.0f/%.0f".format(it.climb, it.elevation) })
                println("$name: top elevation ${steps.maxOf { it.elevation }}, bottom height ${steps.minOf { it.height }} m")
            }
        }
    }
}
