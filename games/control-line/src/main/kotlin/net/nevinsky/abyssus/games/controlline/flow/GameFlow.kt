/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.flow

import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.games.controlline.components.PlaneClass
import net.nevinsky.abyssus.games.controlline.components.PlaneComponent
import net.nevinsky.abyssus.games.controlline.flight.FlightEnd
import net.nevinsky.abyssus.games.controlline.score.ScoreEntry
import net.nevinsky.abyssus.games.controlline.score.ScoreTable
import net.nevinsky.abyssus.physics.RigidBodyComponent
import net.nevinsky.abyssus.runtime.ecs.component.NameComponent
import java.time.Instant

/** The longest pilot name the score table keeps. */
const val NAME_LENGTH = 12

/** What the main menu says when the field scene has no plane. */
const val NO_PLANE_MESSAGE = "No plane was found in the field scene"

/** A plane of the field scene as plane select shows it; [entityId] is its scene id. */
data class PlaneChoice(
    val entityId: Int,
    val name: String,
    val planeClass: PlaneClass,
    val lineLength: Float,
    val mass: Float,
    val fuelTime: Float,
)

/** Every entity of a scene with a [PlaneComponent], in the order of their names. */
fun planeChoices(entities: Map<Int, Entity>): List<PlaneChoice> = entities.mapNotNull { (id, entity) ->
    val plane = entity.getComponent(PlaneComponent::class.java) ?: return@mapNotNull null
    PlaneChoice(
        id, entity.getComponent(NameComponent::class.java)?.name ?: "Plane $id", plane.planeClass, plane.lineLength,
        entity.getComponent(RigidBodyComponent::class.java)?.mass ?: 0f, plane.fuelTime,
    )
}.sortedWith(compareBy<PlaneChoice> { it.name }.thenBy { it.entityId })

/** How a flight went, as GAME OVER shows it. */
data class FlightReport(val plane: String, val end: FlightEnd, val score: Int, val laps: Int, val bestCombo: Int, val flightTime: Float)

/** The screens of the game and what each one holds. */
sealed interface Screen {
    data object MainMenu : Screen

    /** Choosing a plane; [index] into the flow's planes. */
    data class PlaneSelect(val index: Int) : Screen

    /** Flying [plane]; [attempt] counts from 1 and grows with each retry, so a retry is a new flight. */
    data class Flying(val plane: PlaneChoice, val attempt: Int = 1) : Screen

    data class Paused(val flying: Flying) : Screen

    /** The flight entered the score table: asking for a name, starting from [name]. */
    data class NameEntry(val flying: Flying, val report: FlightReport, val name: String) : Screen

    /** The flight is over; [rank] is where it was saved in the score table (from 1), null when it was not. */
    data class GameOver(val flying: Flying, val report: FlightReport, val rank: Int?) : Screen

    /** The score table, opened from [from], with the row at [highlight] (from 1) marked. */
    data class Scores(val from: Screen, val highlight: Int?) : Screen
}

/**
 * The game's screen state machine (design decision 8), with the transitions of `control-line-game-flow`. Screens only
 * show [screen] and send events here; nothing here touches GL or input devices.
 */
class GameFlow(val planes: List<PlaneChoice>, val table: ScoreTable, private val now: () -> Instant = Instant::now) {
    var screen: Screen = Screen.MainMenu
        private set

    /** The name entered last, offered at the next name entry. */
    var lastName = ""
        private set

    /** Whether the game should close (Quit). */
    var quit = false
        private set

    val startEnabled: Boolean get() = planes.isNotEmpty()

    /** The main menu's message: why Start is disabled, or null. */
    val message: String? get() = if (startEnabled) null else NO_PLANE_MESSAGE

    // main menu

    fun start() {
        if (screen == Screen.MainMenu && startEnabled) screen = Screen.PlaneSelect(0)
    }

    fun scores() {
        val from = screen
        if (from == Screen.MainMenu) screen = Screen.Scores(from, null)
        if (from is Screen.GameOver) screen = Screen.Scores(from, from.rank)
    }

    fun quitGame() {
        if (screen == Screen.MainMenu) quit = true
    }

    // plane select

    fun previousPlane() = turn(-1)

    fun nextPlane() = turn(1)

    private fun turn(by: Int) {
        val s = screen as? Screen.PlaneSelect ?: return
        screen = Screen.PlaneSelect(Math.floorMod(s.index + by, planes.size))
    }

    /** Enter on plane select: fly the shown plane. */
    fun choosePlane() {
        val s = screen as? Screen.PlaneSelect ?: return
        screen = Screen.Flying(planes[s.index])
    }

    // flying

    fun pause() {
        val s = screen as? Screen.Flying ?: return
        screen = Screen.Paused(s)
    }

    fun resume() {
        val s = screen as? Screen.Paused ?: return
        screen = s.flying
    }

    /** The flight ended: GAME OVER, after the name entry when the score enters the table. */
    fun flightEnded(report: FlightReport) {
        val flying = screen as? Screen.Flying ?: return
        screen = if (table.qualifies(report.score)) Screen.NameEntry(flying, report, lastName)
        else Screen.GameOver(flying, report, null)
    }

    /** The name was entered: the flight is saved under it (at most [NAME_LENGTH] characters, "Pilot" when blank). */
    fun nameEntered(name: String) {
        val s = screen as? Screen.NameEntry ?: return
        val clean = name.trim().take(NAME_LENGTH).trim().ifEmpty { "Pilot" }
        lastName = clean
        val r = s.report
        val rank = table.add(ScoreEntry(clean, r.plane, r.score, r.laps, r.bestCombo, r.flightTime, now().toString()))
        screen = Screen.GameOver(s.flying, r, rank)
    }

    /** Retry on GAME OVER: the same plane again, from takeoff. */
    fun retry() {
        val s = screen as? Screen.GameOver ?: return
        screen = Screen.Flying(s.flying.plane, s.flying.attempt + 1)
    }

    /** Main menu, from GAME OVER or the pause menu. */
    fun mainMenu() {
        if (screen is Screen.GameOver || screen is Screen.Paused) screen = Screen.MainMenu
    }

    /** Escape or Back: one screen back, or pause while flying. */
    fun back() {
        when (val s = screen) {
            is Screen.PlaneSelect -> screen = Screen.MainMenu
            is Screen.Flying -> pause()
            is Screen.Paused -> resume()
            is Screen.Scores -> screen = s.from
            else -> Unit
        }
    }
}
