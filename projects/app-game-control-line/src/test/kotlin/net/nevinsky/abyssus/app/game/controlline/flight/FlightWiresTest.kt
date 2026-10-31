/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

class FlightWiresTest {
    @Test
    fun aFlyingTrainersWiresAreNearlyStraight() = fieldSession("Trainer").use { s ->
        repeat(600) {
            s.advance(0.01f)
            s.wires(0.01f)
        }
        assertTrue("tension ${s.flight.tension}", s.flight.tension >= CONTROL_TENSION)
        val ends = s.lines()
        s.wires().forEachIndexed { i, wire ->
            val (from, to) = ends[i]
            assertEquals(0f, wire.first().dst(from), 1e-4f)
            assertEquals(0f, wire.last().dst(to), 1e-4f)
            val bow = wire[WIRE_SEGMENTS / 2].dst(Vector3(from).lerp(to, 0.5f))
            assertTrue("line $i bows $bow m", bow < 0.2f)
        }
    }

    @Test
    fun aGlidingTrainersSlackWiresHang() = fieldSession("Trainer") { thrust = 0f }.use { s ->
        repeat(60) {
            s.advance(0.01f)
            s.wires(0.01f)
        }
        val ends = s.lines()
        s.wires().forEachIndexed { i, wire ->
            val (from, to) = ends[i]
            val chordMid = Vector3(from).lerp(to, 0.5f)
            assertTrue("line $i mid ${wire[WIRE_SEGMENTS / 2].y} chord ${chordMid.y}", wire[WIRE_SEGMENTS / 2].y < chordMid.y)
        }
    }

    @Test
    fun whileTheLinesPullNeitherWireHangsLikeThread() = fieldSession("Trainer").use { s ->
        var worst = 0f
        var where = ""
        repeat(2000) {
            s.advance(0.01f)
            val wires = s.wires(0.01f)
            if (s.rig.tension >= CONTROL_TENSION) {
                val ends = s.lines()
                wires.forEachIndexed { i, wire ->
                    val (from, to) = ends[i]
                    val d = wire[WIRE_SEGMENTS / 2].dst(Vector3(from).lerp(to, 0.5f))
                    if (d > worst) { worst = d; where = "t=${s.flight.time} line $i T=${s.flight.tension} v=${s.rig.plane.let { _ -> s.flight.forces.airspeed }}" }
                }
            }
        }
        assertTrue("a wire strayed $worst m from its chord at $where", worst < 0.3f)
    }
}
