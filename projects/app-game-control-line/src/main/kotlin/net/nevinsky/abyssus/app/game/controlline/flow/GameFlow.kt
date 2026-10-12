/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flow

import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.app.game.controlline.components.PlaneClass
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.pattern.AssistLevel
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefModel
import net.nevinsky.abyssus.app.game.controlline.pattern.debriefOf
import net.nevinsky.abyssus.app.game.controlline.pattern.sheetOnlyDebrief
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreEntry
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.score.ScoreEntry
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
import net.nevinsky.abyssus.lib.physics.RigidBodyComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import java.time.Instant

/** The longest pilot name the score table keeps. */
const val NAME_LENGTH = 12

/** What the main menu says when the field scene has no plane. */
const val NO_PLANE_MESSAGE = "No plane was found in the field scene"

/** What the main menu says when the field scene has planes but none of class `STUNT`. */
const val NO_STUNT_MESSAGE = "The F2B pattern needs a stunt plane"

/** The assist levels the game offers. */
val OFFERED_LEVELS = listOf(AssistLevel.PRO, AssistLevel.MID, AssistLevel.NOOB)

/** The tabs of the score table screen: Free flight, then one per offered assist level. */
enum class ScoreTab(val label: String, val level: AssistLevel?) {
    FREE("Free flight", null),
    PRO("Pro", AssistLevel.PRO),
    MID("Mid", AssistLevel.MID),
    NOOB("Noob", AssistLevel.NOOB),
}

/** The score table tab of the flights of [mode]. */
fun tabOf(mode: Mode): ScoreTab = when (mode) {
    Mode.Free -> ScoreTab.FREE
    is Mode.Pattern -> ScoreTab.entries.first { it.level == mode.level }
}

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

/** The screens of the game and what each one holds. */
sealed interface Screen {
    data object MainMenu : Screen

    /** Choosing the assist level of an F2B pattern. */
    data object AssistSelect : Screen

    /** Choosing a plane for a flight of [mode]; [index] into [GameFlow.choices] of the mode. */
    data class PlaneSelect(val index: Int, val mode: Mode = Mode.Free) : Screen

    /** Flying [plane] in [mode]; [attempt] counts from 1 and grows with each retry, so a retry is a new flight. */
    data class Flying(val plane: PlaneChoice, val attempt: Int = 1, val mode: Mode = Mode.Free) : Screen

    data class Paused(val flying: Flying) : Screen

    /** The flight entered the score table: asking for a name, starting from [name]. */
    data class NameEntry(val flying: Flying, val report: FlightReport, val name: String) : Screen

    /** The flight is over; [rank] is where it was saved in the score table (from 1), null when it was not. */
    data class GameOver(val flying: Flying, val report: FlightReport, val rank: Int?) : Screen

    /**
     * The score table on [tab], opened from [from], with the row at [highlight] (from 1) marked on the tab of the flight
     * that was just saved.
     */
    data class Scores(val from: Screen, val highlight: Int?, val tab: ScoreTab = ScoreTab.FREE, val selected: Int = 0) : Screen {
        /** The tab the [highlight] belongs to: that of the flight shown on GAME OVER. */
        val highlightTab: ScoreTab? get() = (from as? GameOver)?.let { tabOf(it.flying.mode) }
    }

    /** The debrief of an F2B flight, opened from [from] (GAME OVER or the score table); [model] holds what it shows. */
    data class Debrief(val from: Screen, val model: DebriefModel) : Screen
}

/**
 * The game's screen state machine (design decision 8), with the transitions of `control-line-game-flow`. Screens only
 * show [screen] and send events here; nothing here touches GL or input devices.
 */
class GameFlow(
    val planes: List<PlaneChoice>, val table: ScoreTable, val patternTable: PatternScoreTable,
    private val now: () -> Instant = Instant::now,
) {
    var screen: Screen = Screen.MainMenu
        private set

    /** The name entered last, offered at the next name entry. */
    var lastName = ""
        private set

    /** Whether the game should close (Quit). */
    var quit = false
        private set

    /** Whether Free flight can be chosen: there is a plane. */
    val freeEnabled: Boolean get() = planes.isNotEmpty()

    /** Whether the F2B pattern can be chosen: there is a plane of class `STUNT`. */
    val patternEnabled: Boolean get() = planes.any { it.planeClass == PlaneClass.STUNT }

    /** The main menu's message: why Free flight or the F2B pattern is disabled, or null. */
    val message: String? get() = when {
        !freeEnabled -> NO_PLANE_MESSAGE
        !patternEnabled -> NO_STUNT_MESSAGE
        else -> null
    }

    /** The planes plane select offers for [mode]: all of them in Free flight, the stunt planes in an F2B pattern. */
    fun choices(mode: Mode): List<PlaneChoice> = when (mode) {
        Mode.Free -> planes
        is Mode.Pattern -> planes.filter { it.planeClass == PlaneClass.STUNT }
    }

    // main menu

    fun freeFlight() {
        if (screen == Screen.MainMenu && freeEnabled) screen = Screen.PlaneSelect(0, Mode.Free)
    }

    fun f2bPattern() {
        if (screen == Screen.MainMenu && patternEnabled) screen = Screen.AssistSelect
    }

    fun scores() {
        val from = screen
        if (from == Screen.MainMenu) screen = Screen.Scores(from, null, ScoreTab.FREE)
        if (from is Screen.GameOver) screen = Screen.Scores(from, from.rank, tabOf(from.flying.mode))
    }

    fun quitGame() {
        if (screen == Screen.MainMenu) quit = true
    }

    // the score table's tabs

    fun previousTab() = turnTab(-1)

    fun nextTab() = turnTab(1)

    private fun turnTab(by: Int) {
        val s = screen as? Screen.Scores ?: return
        val tabs = ScoreTab.entries
        screen = s.copy(tab = tabs[Math.floorMod(s.tab.ordinal + by, tabs.size)], selected = 0)
    }

    /** Up and Down on an F2B tab of the score table: the row [by] further, within the board. */
    fun selectRow(by: Int) {
        val s = screen as? Screen.Scores ?: return
        val level = s.tab.level ?: return
        val last = patternTable.board(level).size - 1
        if (last >= 0) screen = s.copy(selected = (s.selected + by).coerceIn(0, last))
    }

    /** Enter or a click on a row of an F2B tab (default the selected one): the flight's debrief, from its recording when it has one. */
    fun openRow(index: Int? = null) {
        val s = screen as? Screen.Scores ?: return
        val level = s.tab.level ?: return
        val row = index ?: s.selected
        val entry = patternTable.board(level).getOrNull(row) ?: return
        val recording = entry.flight?.let(patternTable.recordings::load)
        screen = Screen.Debrief(s.copy(selected = row), recording?.let(::debriefOf) ?: sheetOnlyDebrief(entry, level))
    }

    /** Debrief on GAME OVER of an F2B flight: the flight just flown, whether or not it entered a board. */
    fun debrief() {
        val s = screen as? Screen.GameOver ?: return
        val result = s.report.result as? FlightResult.Pattern ?: return
        val model = result.recording?.let(::debriefOf)
            ?: sheetOnlyDebrief(result.sheet, s.report.plane, result.level)
        screen = Screen.Debrief(s, model)
    }

    // assist level

    /** The assist level was chosen: plane select, among the stunt planes. */
    fun chooseAssist(level: AssistLevel) {
        if (screen == Screen.AssistSelect && level in OFFERED_LEVELS) screen = Screen.PlaneSelect(0, Mode.Pattern(level))
    }

    // plane select

    fun previousPlane() = turn(-1)

    fun nextPlane() = turn(1)

    private fun turn(by: Int) {
        val s = screen as? Screen.PlaneSelect ?: return
        screen = s.copy(index = Math.floorMod(s.index + by, choices(s.mode).size))
    }

    /** Enter on plane select: fly the shown plane. */
    fun choosePlane() {
        val s = screen as? Screen.PlaneSelect ?: return
        screen = Screen.Flying(choices(s.mode)[s.index], 1, s.mode)
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

    /** The flight ended: GAME OVER, after the name entry when the score enters its table or board. */
    fun flightEnded(report: FlightReport) {
        val flying = screen as? Screen.Flying ?: return
        val enters = when (val r = report.result) {
            is FlightResult.Free -> table.qualifies(r.score)
            is FlightResult.Pattern -> patternTable.qualifies(r.level, r.sheet.tenths / 10.0)
        }
        screen = if (enters) Screen.NameEntry(flying, report, lastName) else Screen.GameOver(flying, report, null)
    }

    /** The name was entered: the flight is saved under it (at most [NAME_LENGTH] characters, "Pilot" when blank). */
    fun nameEntered(name: String) {
        val s = screen as? Screen.NameEntry ?: return
        val clean = name.trim().take(NAME_LENGTH).trim().ifEmpty { "Pilot" }
        lastName = clean
        val r = s.report
        val rank = when (val result = r.result) {
            is FlightResult.Free ->
                table.add(ScoreEntry(clean, r.plane, result.score, result.laps, result.bestCombo, r.flightTime, now().toString()))
            is FlightResult.Pattern -> patternTable.add(
                result.level,
                PatternScoreEntry(clean, r.plane, result.sheet.tenths / 10.0, r.flightTime, now().toString(), result.sheet.marks.map { it.toDouble() }),
                result.recording,
            )
        }
        screen = Screen.GameOver(s.flying, r, rank)
    }

    /** Retry on GAME OVER: the same plane again, in the same mode and level, from takeoff. */
    fun retry() {
        val s = screen as? Screen.GameOver ?: return
        screen = Screen.Flying(s.flying.plane, s.flying.attempt + 1, s.flying.mode)
    }

    /** Main menu, from GAME OVER or the pause menu. */
    fun mainMenu() {
        if (screen is Screen.GameOver || screen is Screen.Paused) screen = Screen.MainMenu
    }

    /** Escape or Back: one screen back, or pause while flying. */
    fun back() {
        when (val s = screen) {
            Screen.AssistSelect -> screen = Screen.MainMenu
            is Screen.PlaneSelect -> screen = if (s.mode is Mode.Pattern) Screen.AssistSelect else Screen.MainMenu
            is Screen.Flying -> pause()
            is Screen.Paused -> resume()
            is Screen.Scores -> screen = s.from
            is Screen.Debrief -> screen = s.from
            else -> Unit
        }
    }
}
