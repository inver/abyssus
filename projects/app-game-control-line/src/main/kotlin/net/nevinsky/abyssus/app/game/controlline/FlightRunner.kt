/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import net.nevinsky.abyssus.app.game.controlline.ai.F2bBot
import net.nevinsky.abyssus.app.game.controlline.ai.f2bBot
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.flight.FlightControlSource
import net.nevinsky.abyssus.app.game.controlline.flight.FlightSession
import net.nevinsky.abyssus.app.game.controlline.flight.HumanControl
import net.nevinsky.abyssus.app.game.controlline.flow.Control
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Mode
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.flow.reportOf
import net.nevinsky.abyssus.app.game.controlline.input.HandleInput
import net.nevinsky.abyssus.app.game.controlline.pattern.GuidePlanner
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternJudge
import net.nevinsky.abyssus.app.game.controlline.render.FieldScene
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.RigidBodyComponent
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import org.slf4j.Logger

/**
 * The flight half of the game, without GL (design decision 4 of `add-f2b-ai-demonstration`): it starts a
 * [FlightSession] on its own load of the field ([newScene]) when the [flow] flies a plane, with the player's [input] or a
 * fresh Watch AI bot holding the handle, advances it while flying, reports its end to the flow, and closes it when the
 * flow leaves the flight's screens. A paused flight is not advanced, so neither the plane nor the bot moves. During an AI
 * demonstration the player's keys and mouse do not reach the handle. Main thread only.
 */
class FlightRunner(
    private val flow: GameFlow,
    private val newScene: () -> FieldScene,
    private val assets: () -> PhysicsAssets,
    private val log: Logger,
    private val natives: JoltNatives,
) : AutoCloseable {
    /** The player's handle, from the keys and the mouse. */
    val input = HandleInput()

    var session: FlightSession? = null
        private set
    var judge: PatternJudge? = null
        private set

    /** The Noob guide of the flight, or null at the other levels. */
    var guide: GuidePlanner? = null
        private set

    /** The flight's own load of the field. */
    var scene: FieldScene? = null
        private set

    /** The bot holding the handle in an AI demonstration, or null. */
    var bot: F2bBot? = null
        private set

    /** The flight being flown, or null. */
    var flying: Screen.Flying? = null
        private set

    /** Starts a flight when the flow flies a plane (again), and ends it when the flow leaves the flight's screens. */
    fun follow() {
        when (val screen = flow.screen) {
            is Screen.Flying -> if (screen != flying) start(screen)
            is Screen.Paused, is Screen.NameEntry, is Screen.GameOver, is Screen.Debrief -> Unit
            is Screen.Scores -> if (screen.from !is Screen.GameOver) end()
            else -> end()
        }
    }

    /** Runs [seconds] of frame time: the flight while flying (then its end goes to the flow), the plane coming to rest after it. */
    fun advance(seconds: Float) {
        val s = session ?: return
        when (val screen = flow.screen) {
            is Screen.Flying -> {
                s.advance(seconds)
                judge?.let { guide?.update(it) }
                flying?.let { f -> reportOf(s, f.mode, judge, f.plane.name)?.let(flow::flightEnded) }
            }
            // the plane comes to rest; the flight itself is over
            is Screen.GameOver, is Screen.NameEntry -> s.advance(seconds)
            else -> Unit
        }
    }

    /** Whether the flight in progress is flown by the bot. */
    val demonstration: Boolean get() = flying?.control == Control.AI

    /** A key went down or up while flying; true when it is one of the handle's (which the bot ignores in a demonstration). */
    fun key(name: String, pressed: Boolean): Boolean {
        if (flow.screen !is Screen.Flying) return false
        if (demonstration) return HandleInput.isHandleKey(name)
        return input.key(name, pressed)
    }

    /** The mouse moved to [y] of a window [height] high while flying; it sets the handle only in the player's own flight. */
    fun mouseMoved(y: Float, height: Float): Boolean {
        if (flow.screen !is Screen.Flying) return false
        if (!demonstration) input.mouseMoved(y, height)
        return true
    }

    private fun start(screen: Screen.Flying) {
        end()
        val loaded = newScene()
        val plane = loaded.entity(screen.plane.entityId) ?: return
        val pilot = loaded.pilot ?: return
        // an F2B pattern is judged over a hemisphere of the plane's line length
        val pattern = screen.mode as? Mode.Pattern
        val judge = pattern?.let { PatternJudge(screen.plane.lineLength) }
        this.judge = judge
        guide = pattern?.takeIf { it.level.guided }?.let { GuidePlanner(screen.plane.lineLength) }
        input.reset()
        val control: FlightControlSource = when (screen.control) {
            Control.HUMAN -> HumanControl(input)
            Control.AI -> {
                // the bot flies the plane's own settings, and only reads the judge
                val settings = plane.getComponent(PlaneComponent::class.java)
                val mass = plane.getComponent(RigidBodyComponent::class.java)?.mass ?: screen.plane.mass
                f2bBot(settings, mass, checkNotNull(judge) { "Watch AI flies an F2B pattern" }::guideInput).also { bot = it }
            }
        }
        session = FlightSession(loaded.engine, pilot, plane, assets(), log, natives, judge, control)
        scene = loaded
        flying = screen
    }

    private fun end() {
        session?.close()
        session = null
        judge = null
        guide = null
        bot = null
        scene = null
        flying = null
    }

    override fun close() = end()
}
