/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.input.HandleInput
import net.nevinsky.abyssus.lib.physics.MAX_STEPS_PER_ADVANCE
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.SceneEngine
import org.slf4j.Logger

/** A frame never runs more than this much simulation (s), so a stall does not jump the plane. */
private const val MAX_FRAME = 0.1f

/**
 * One flight in the game: a physics world over its own load of the field scene, [plane] on its lines and the fixed-step
 * loop. Each step reads the handle from the input, updates the [flight] and steps the world. After the flight ends
 * the world goes on, so a crashed plane comes to rest. Lives on the game's main thread; [close] releases the world.
 */
class FlightSession(engine: SceneEngine, pilot: Entity, val plane: Entity, assets: PhysicsAssets, log: Logger, natives: JoltNatives) : AutoCloseable {
    private val world = PhysicsWorld(engine, assets, log, natives)
    private val ground = groundOf(engine, assets)
    val rig = LineRig(world, pilot, plane, ground)
    val flight = Flight(world, rig, ground).also { it.takeoff() }
    private var carried = 0f

    /** Runs the fixed steps [seconds] of frame time hold, with the handle from [input]. */
    fun advance(seconds: Float, input: HandleInput) {
        carried += seconds.coerceIn(0f, MAX_FRAME)
        var steps = 0
        while (carried >= PHYSICS_STEP && steps < MAX_STEPS_PER_ADVANCE) {
            input.update(PHYSICS_STEP)
            rig.tilt = input.tilt
            flight.update(PHYSICS_STEP)
            world.step()
            carried -= PHYSICS_STEP
            steps++
        }
        if (steps == MAX_STEPS_PER_ADVANCE) carried = 0f
    }

    /** The two lines, from the handle to the leadouts (world space), up line first. */
    fun lines(): List<Pair<Vector3, Vector3>> = rig.lines.map { line ->
        val a = Vector3()
        val b = Vector3()
        line.anchors(a, b)
        a to b
    }

    /** How the flight went, for GAME OVER; null while it goes on. */
    fun report(): FlightReport? {
        val end = flight.result ?: return null
        val keeper = flight.scoring.keeper
        return FlightReport(plane.getComponent(NameComponent::class.java)?.name.orEmpty(), end, keeper.score, keeper.laps, keeper.bestCombo, flight.time)
    }

    override fun close() = world.close()
}
