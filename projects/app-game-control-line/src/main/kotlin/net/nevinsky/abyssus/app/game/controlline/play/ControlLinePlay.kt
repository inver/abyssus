/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.play

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.GameComponents
import net.nevinsky.abyssus.app.game.controlline.components.PilotComponent
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.flight.CONTROL_TENSION
import net.nevinsky.abyssus.app.game.controlline.flight.Flight
import net.nevinsky.abyssus.app.game.controlline.flight.Ground
import net.nevinsky.abyssus.app.game.controlline.flight.LineRig
import net.nevinsky.abyssus.app.game.controlline.input.HandleInput
import net.nevinsky.abyssus.lib.physics.ColliderComponent
import net.nevinsky.abyssus.lib.physics.ColliderShape
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.lib.physics.play.DebugLine
import net.nevinsky.abyssus.lib.physics.play.PlayInput
import net.nevinsky.abyssus.lib.physics.play.PlayModule
import net.nevinsky.abyssus.lib.runtime.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.runtime.ecs.scene.SceneEngine
import net.nevinsky.abyssus.lib.runtime.schema.ComponentRegistry
import java.util.Locale

/** After a crash or a landing, Play starts the next flight this many seconds later. */
const val RESTART_DELAY = 1f

/** Taut lines are drawn white, slack ones grey (RGBA8888). */
private const val TAUT = -0x1
private const val SLACK = 0x808080ff.toInt()

/**
 * Play in Abyssus for the control-line field (design decision 10): flies the plane selected in the tree, or the first
 * plane by name, on its lines with the game's flight code, and the W / S (or Up / Down) keys tilt the handle. A flight
 * that ends restarts from takeoff after [RESTART_DELAY]. No screens: the score, laps and tension go to telemetry.
 *
 * Play gives a module no project folder, so the terrain's heights are not read: the ground is taken as flat at the
 * field's height 0, which is where the field's flying circle is.
 */
class ControlLinePlay : PlayModule {
    private val input = HandleInput()
    private var flight: Flight? = null
    private var flights = 0
    private var lastEnd: String = "-"

    override fun components(): ComponentRegistry = GameComponents()

    override fun systems(world: PhysicsWorld, engine: SceneEngine, selection: Entity?): List<EntitySystem> {
        val entities = engine.ids.ids.sorted().mapNotNull { engine.ids[it] }
        val plane = selection?.takeIf { it.getComponent(PlaneComponent::class.java) != null }
            ?: entities.filter { it.getComponent(PlaneComponent::class.java) != null }.minByOrNull { name(it) }
            ?: return emptyList()
        val pilot = entities.firstOrNull { it.getComponent(PilotComponent::class.java) != null } ?: return emptyList()
        val field = entities.firstOrNull { it.getComponent(ColliderComponent::class.java)?.shape == ColliderShape.HEIGHT_FIELD }
        val ground = Ground(field, null)
        val next = Flight(world, LineRig(world, pilot, plane, ground), ground)
        flight = next
        flights = 0
        lastEnd = "-"
        input.reset()
        return listOf(PlaySystem(next))
    }

    override fun input(event: PlayInput) {
        when (event.kind) {
            PlayInput.Kind.KEY_DOWN -> input.key(event.key, true)
            PlayInput.Kind.KEY_UP -> input.key(event.key, false)
            // the play protocol does not say how high the view is, so the mouse cannot set the tilt here
            else -> Unit
        }
    }

    override fun telemetry(world: PhysicsWorld): Map<String, String> {
        val f = flight ?: return mapOf("Control line" to "no plane with a PlaneComponent, or no pilot")
        val keeper = f.scoring.keeper
        return linkedMapOf(
            "Plane" to name(f.rig.plane),
            "Flight" to flights.toString(),
            "Score" to keeper.score.toString(),
            "Laps" to keeper.laps.toString(),
            "Combo" to "x${keeper.multiplier}",
            "Tension" to String.format(Locale.ROOT, "%.1f N", f.tension),
            "Elevator" to String.format(Locale.ROOT, "%.1f°", f.rig.elevator * MathUtils.radiansToDegrees),
            "Fuel" to String.format(Locale.ROOT, "%.0f s", f.fuelLeft),
            "Last flight" to lastEnd,
        )
    }

    override fun lines(world: PhysicsWorld): List<DebugLine> {
        val f = flight ?: return emptyList()
        val color = if (f.tension >= CONTROL_TENSION) TAUT else SLACK
        val a = Vector3()
        val b = Vector3()
        return f.rig.lines.map { line ->
            line.anchors(a, b)
            DebugLine(a.x, a.y, a.z, b.x, b.y, b.z, color)
        }
    }

    private fun name(entity: Entity) = entity.getComponent(NameComponent::class.java)?.name.orEmpty()

    /** Updated by the play host before each advance: input, then the flight; a finished flight restarts. */
    private inner class PlaySystem(private val flight: Flight) : EntitySystem() {
        private var waited = 0f

        override fun update(deltaTime: Float) {
            if (flights == 0) start()
            if (flight.finished) {
                waited += deltaTime
                if (waited >= RESTART_DELAY) start()
                return
            }
            input.update(deltaTime)
            flight.rig.tilt = input.tilt
            flight.update(deltaTime)
            flight.result?.let { end ->
                lastEnd = "${end.label}, ${flight.scoring.keeper.score} points"
                waited = 0f
            }
        }

        private fun start() {
            flight.takeoff()
            flights++
            waited = 0f
        }
    }
}
