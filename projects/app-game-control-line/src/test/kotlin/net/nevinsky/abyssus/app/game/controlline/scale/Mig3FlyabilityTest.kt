/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.scale

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.bundledProject
import net.nevinsky.abyssus.app.game.controlline.flight.FieldFlight
import net.nevinsky.abyssus.app.game.controlline.flight.CONTROL_TENSION
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bundled `Mig-3` replica: its model is in the plane frame, and it takes off and flies laps on its lines. */
class Mig3FlyabilityTest {
    private val points: List<Vector3> by lazy { checkNotNull(PhysicsAssets(bundledProject().toFile()).modelPoints("model_mig3")) }

    @Test
    fun theModelRestsOnTheGroundAndIsCentred() {
        assertEquals("lowest point", 0f, points.minOf { it.y }, 0.005f)
        assertEquals("centre x", 0f, (points.maxOf { it.x } + points.minOf { it.x }) / 2f, 0.02f)
        assertEquals("centre z", 0f, (points.maxOf { it.z } + points.minOf { it.z }) / 2f, 0.02f)
    }

    @Test
    fun theWingsRunAlongXAndTheNoseIsOnPlusZ() {
        val span = points.maxOf { it.x } - points.minOf { it.x }
        val length = points.maxOf { it.z } - points.minOf { it.z }
        assertTrue("span $span is not longer than length $length", span > length)
        assertEquals("span (m)", 1.03f, span, 0.01f)
        // the fin is the tallest, narrowest part, at the tail: the tallest points lie behind the middle
        val top = points.maxOf { it.y }
        val fin = points.filter { it.y > top * 0.97f }
        assertTrue("no fin points", fin.isNotEmpty())
        val tail = fin.map { it.z }.average()
        assertTrue("the tall tail is at z = $tail, not behind the middle", tail < -0.1)
    }

    @Test
    fun takesOffWithinSixSecondsAndStaysOnItsLines() = FieldFlight("Mig-3").use { f ->
        var airborneAt: Float? = null
        var t = 0f
        f.fly(6f) {
            t += 1f / 120f
            if (airborneAt == null && f.height > 1f) airborneAt = t
        }
        assertTrue("Mig-3 was not above 1 m within 6 s", airborneAt != null)
        val distance = f.position.cpy().sub(f.rig.handle).len()
        assertTrue("off its lines: $distance m from the handle", distance > f.settings.lineLength - 0.5f)
        assertTrue("tension ${f.flight.tension}", f.flight.tension >= CONTROL_TENSION)
    }

    @Test
    fun fliesLapsOnAHeldTrimAndScoresRealism() {
        val judge = ScaleJudge()
        FieldFlight("Mig-3", judge).use { f ->
            f.rig.tilt = 0f
            var highest = 0f
            var seconds = 0f
            while (judge.laps < 8 && !f.flight.finished && seconds < 120f) {
                f.fly(1f) { highest = maxOf(highest, f.flight.lastSample?.elevation ?: 0f) }
                seconds += 1f
            }
            assertNull("the flight ended: ${f.flight.result}", f.flight.result)
            assertEquals(8, judge.laps)
            assertTrue("climbed to $highest degrees", highest < 25f)
            assertTrue("lap times ${judge.lapTimes}", judge.marks.speed > 0)
            assertTrue("level mark ${judge.marks.level}", judge.marks.level > 0)
        }
    }
}
