/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import com.badlogic.gdx.Input
import net.nevinsky.abyssus.app.game.controlline.flight.FlightSession
import net.nevinsky.abyssus.app.game.controlline.flow.FlightReport
import net.nevinsky.abyssus.app.game.controlline.flow.FlightResult
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Mode
import net.nevinsky.abyssus.app.game.controlline.flow.NAME_LENGTH
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.flow.ScoreTab
import net.nevinsky.abyssus.app.game.controlline.pattern.AssistLevel
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefModel
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefView
import net.nevinsky.abyssus.app.game.controlline.pattern.FLIGHT_TIME
import net.nevinsky.abyssus.app.game.controlline.pattern.HeightWarning
import net.nevinsky.abyssus.app.game.controlline.pattern.PAUSE_HEIGHT_MAX
import net.nevinsky.abyssus.app.game.controlline.pattern.PAUSE_HEIGHT_MIN
import net.nevinsky.abyssus.app.game.controlline.pattern.PAUSE_LAPS
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternJudge
import net.nevinsky.abyssus.app.game.controlline.pattern.SCHEDULE
import net.nevinsky.abyssus.app.game.controlline.pattern.SheetEntry
import net.nevinsky.abyssus.lib.core.ui.state.UiSnapshot
import net.nevinsky.abyssus.lib.core.ui.state.UiValue
import net.nevinsky.abyssus.lib.core.ui.state.UiWrite
import net.nevinsky.abyssus.lib.core.ui.state.UiWriteResult
import java.util.Locale

/** The HUD's tension bar reads up to this (N); the asset's `maximum` is the same number. */
private const val TENSION_SCALE = 60f

/** A maneuver's name shows on the HUD this long (s). */
private const val TOAST_TIME = 2.5f

private val NO_REPLAY_KEYS = "Up / Down: manoeuvre    Esc: back"
private val REPLAY_KEYS = "Up / Down: manoeuvre    Space: pause    R: restart    1 / 2 / 3: speed ½× 1× 2×    V: view    Esc: back"

/** The UI layers a [Screen] shows: the bottom one, and the overlay over it (the pause menu over the HUD). */
data class GameLayers(val screen: String, val overlay: String? = null)

/**
 * The game side of the authored screens, with no graphics: the values it publishes to the `screens` UI asset from the
 * [flow], the [FlightSession] and the F2B [PatternJudge], the writes and named actions it accepts, and the game's own
 * keys. [GameUiHost] connects it to a presenter. Single-threaded, on the GL thread.
 */
class GameUiModel(private val flow: GameFlow, private val quit: () -> Unit) {
    /** The name field's value; it starts from the flow's offered name each time name entry opens. */
    var playerName = ""
        private set
    private var nameFor: Screen.NameEntry? = null

    // the HUD: what the last flight update showed
    private var tension = 0f
    private var fuel = ""
    private var laps = ""
    private var score = ""
    private var combo = ""
    private var clock = ""
    private var next = ""
    private var pauseLaps = ""
    private var warning = ""
    private var toast = ""
    private var toastTime = 0f
    private var toastShown: Any? = null
    private var toastJudge: PatternJudge? = null
    private var toastDecided = 0

    /** The layers [screen] is shown with. */
    fun layersOf(screen: Screen): GameLayers = when (screen) {
        Screen.MainMenu -> GameLayers(SCREEN_MAIN_MENU)
        Screen.AssistSelect -> GameLayers(SCREEN_ASSIST)
        is Screen.PlaneSelect -> GameLayers(SCREEN_PLANE)
        is Screen.Flying -> GameLayers(hudOf(screen.mode))
        is Screen.Paused -> GameLayers(hudOf(screen.flying.mode), SCREEN_PAUSED)
        is Screen.NameEntry -> GameLayers(SCREEN_NAME_ENTRY)
        is Screen.GameOver -> GameLayers(SCREEN_GAME_OVER)
        is Screen.Scores -> GameLayers(SCREEN_SCORES)
        is Screen.Debrief -> GameLayers(SCREEN_DEBRIEF)
    }

    private fun hudOf(mode: Mode) = if (mode is Mode.Pattern) SCREEN_HUD_PATTERN else SCREEN_HUD_FREE

    private fun modeOf(screen: Screen): Mode? = when (screen) {
        is Screen.Flying -> screen.mode
        is Screen.Paused -> screen.flying.mode
        else -> null
    }

    /** Every binding of the contract, for the flow's current screen and the flight [session] (with the F2B [judge]). */
    fun snapshot(session: FlightSession?, judge: PatternJudge?, seconds: Float): UiSnapshot {
        val screen = flow.screen
        if (screen is Screen.NameEntry && screen !== nameFor) {
            nameFor = screen
            playerName = screen.name
        }
        if (session != null) hud(screen, session, judge, seconds)
        val values = LinkedHashMap<String, UiValue>()
        menus(values)
        planeSelect(values, screen)
        hudValues(values, modeOf(screen))
        summary(values, (screen as? Screen.NameEntry)?.report ?: (screen as? Screen.GameOver)?.report)
        values["playerName"] = text(playerName)
        val over = screen as? Screen.GameOver
        values["rankText"] = text(over?.rank?.let { "Saved in place $it" }.orEmpty())
        values["hasRank"] = flag(over?.rank != null)
        values["canDebrief"] = flag(over?.report?.result is FlightResult.Pattern)
        scores(values, screen as? Screen.Scores)
        debrief(values, (screen as? Screen.Debrief)?.model)
        return UiSnapshot(values)
    }

    private fun text(value: String) = UiValue.Text(value)

    private fun flag(value: Boolean) = UiValue.Bool(value)

    private fun rows(items: List<Map<String, String>>) = UiValue.Rows(items.map { row -> row.mapValues { text(it.value) } })

    private fun menus(values: MutableMap<String, UiValue>) {
        values["freeEnabled"] = flag(flow.freeEnabled)
        values["patternEnabled"] = flag(flow.patternEnabled)
        values["hasMessage"] = flag(flow.message != null)
        values["message"] = text(flow.message.orEmpty())
    }

    private fun planeSelect(values: MutableMap<String, UiValue>, screen: Screen) {
        val select = screen as? Screen.PlaneSelect
        val choices = select?.let { flow.choices(it.mode) }.orEmpty()
        val plane = select?.let { choices.getOrNull(it.index) }
        val label = (select?.mode as? Mode.Pattern)?.let { "F2B pattern - ${it.level.label}" }
        values["hasModeLabel"] = flag(label != null)
        values["modeLabel"] = text(label.orEmpty())
        values["planeCount"] = text(select?.let { "Choose a plane  (${it.index + 1} of ${choices.size})" }.orEmpty())
        values["planeName"] = text(plane?.name.orEmpty())
        values["planeFacts"] = text(plane?.let {
            String.format(Locale.ROOT, "%s    Lines %.0f m    Mass %.2f kg    Fuel %.0f s",
                it.planeClass.name.lowercase().replaceFirstChar { c -> c.uppercase() }, it.lineLength, it.mass, it.fuelTime)
        }.orEmpty())
    }

    private fun hud(screen: Screen, session: FlightSession, judge: PatternJudge?, seconds: Float) {
        val mode = modeOf(screen)
        if (mode == null) return
        val flight = session.flight
        val keeper = flight.scoring.keeper
        tension = flight.tension
        fuel = String.format(Locale.ROOT, "%.0f s", flight.fuelLeft)
        if (mode is Mode.Pattern && judge != null) {
            clock = "${time(flight.time)} / ${time(FLIGHT_TIME)}"
            if (mode.level.prompted) guidance(judge)
        } else {
            laps = keeper.laps.toString()
            score = keeper.score.toString()
            combo = "x${keeper.multiplier}"
            val last = keeper.lastManeuver
            if (last != null && last !== toastShown) {
                toastShown = last
                toast = last.maneuver.label + if (keeper.multiplier > 1) "  x${keeper.multiplier}" else ""
                toastTime = TOAST_TIME
            }
        }
        toastTime -= seconds
    }

    /** The Mid and Noob assist levels' guidance: the next manoeuvre, the pause count, the height warning and a toast per mark. */
    private fun guidance(judge: PatternJudge) {
        next = judge.expected?.let { "${it.label} (K ${it.k})" } ?: "Land"
        pauseLaps = judge.pauseLaps?.let { String.format(Locale.ROOT, "%.1f / %.1f laps", it.coerceAtMost(PAUSE_LAPS), PAUSE_LAPS) } ?: "-"
        warning = when (judge.heightWarning) {
            HeightWarning.BELOW -> "Below ${PAUSE_HEIGHT_MIN.toInt()} m"
            HeightWarning.ABOVE -> "Above ${PAUSE_HEIGHT_MAX.toInt()} m"
            null -> ""
        }
        if (judge !== toastJudge) {
            toastJudge = judge
            toastDecided = 0
        }
        if (judge.decided != toastDecided) {
            toastDecided = judge.decided
            judge.lastScored?.let {
                toast = entryText(it)
                toastTime = TOAST_TIME
            }
        }
    }

    /** `Take-off 8.5 x 2 = 17.0`, or `Take-off 0 (too soon)`. */
    private fun entryText(entry: SheetEntry): String = if (entry.reason == null) {
        String.format(Locale.ROOT, "%s %.1f × %d = %.1f", entry.figure.label, entry.mark, entry.figure.k, entry.points / 10f)
    } else "${entry.figure.label} 0 (${entry.reason.label})"

    private fun hudValues(values: MutableMap<String, UiValue>, mode: Mode?) {
        values["tensionText"] = text(String.format(Locale.ROOT, "%.1f N", tension))
        values["tensionValue"] = UiValue.Num(tension.coerceAtMost(TENSION_SCALE).toDouble())
        values["fuel"] = text(fuel)
        values["laps"] = text(laps)
        values["score"] = text(score)
        values["combo"] = text(combo)
        values["time"] = text(clock)
        values["next"] = text(next)
        values["pauseLaps"] = text(pauseLaps)
        values["warning"] = text(warning)
        values["prompted"] = flag((mode as? Mode.Pattern)?.level?.prompted == true)
        values["toast"] = text(toast)
        values["toastVisible"] = flag(toastTime > 0f && toast.isNotEmpty())
    }

    private fun summary(values: MutableMap<String, UiValue>, report: FlightReport?) {
        val free = report?.result as? FlightResult.Free
        val pattern = report?.result as? FlightResult.Pattern
        values["endLabel"] = text(report?.end?.label.orEmpty())
        values["isFree"] = flag(free != null)
        values["isPattern"] = flag(pattern != null)
        values["facts"] = rows(if (free == null) emptyList() else listOf(
            "Plane" to report.plane, "Score" to free.score.toString(), "Laps" to free.laps.toString(),
            "Best combo" to "x${free.bestCombo}", "Flight time" to time(report.flightTime),
        ).map { (name, value) -> mapOf("name" to name, "value" to value) })
        values["patternHeader"] = text(if (pattern == null) "" else "F2B pattern - ${pattern.level.label} - ${report.plane}")
        values["sheetScore"] = text(if (pattern == null) "" else "Score ${pattern.sheet.score}    Flight time ${time(report.flightTime)}")
        values["sheet"] = rows(if (pattern == null) emptyList() else SCHEDULE.mapIndexed { i, figure ->
            val entry = pattern.sheet[figure]
            val result = when {
                entry == null -> "-"
                entry.reason != null -> "0 (${entry.reason.label})"
                else -> String.format(Locale.ROOT, "%.1f × %d = %.1f", entry.mark, figure.k, entry.points / 10f)
            }
            mapOf("title" to "${i + 1}. ${figure.label}", "k" to "K ${figure.k}", "result" to result,
                "style" to if (entry?.reason != null) "fail" else "normal")
        })
    }

    private fun scores(values: MutableMap<String, UiValue>, screen: Screen.Scores?) {
        val tab = screen?.tab
        for (t in ScoreTab.entries) {
            values["tab${t.name.lowercase().replaceFirstChar { it.uppercase() }}Style"] = text(if (t == tab) "tabSelected" else "tab")
        }
        val level = tab?.level
        values["isFreeTab"] = flag(screen != null && level == null)
        values["isBoardTab"] = flag(screen != null && level != null)
        values["scoresHint"] = text(when {
            screen == null -> ""
            level != null -> "Left / Right: change the tab    Up / Down, Enter or a click: replay a flight"
            else -> "Left / Right: change the tab"
        })
        val damaged = when {
            screen == null -> null
            level == null -> "The old scores could not be read; they were kept as scores.json.bad.".takeIf { flow.table.damaged }
            else -> "The old F2B scores could not be read; they were kept as f2b-scores.json.bad.".takeIf { flow.patternTable.damaged }
        }
        values["hasDamaged"] = flag(damaged != null)
        values["damagedMessage"] = text(damaged.orEmpty())
        val highlight = screen?.highlight?.takeIf { screen.highlightTab == screen.tab }
        fun look(rank: Int, picked: Boolean) = when {
            picked -> "selected"
            highlight == rank -> "highlight"
            else -> "normal"
        }
        values["freeRows"] = rows(if (screen == null || level != null) emptyList() else flow.table.entries.mapIndexed { i, e ->
            mapOf("rank" to (i + 1).toString(), "name" to e.name, "plane" to e.plane, "score" to e.score.toString(), "laps" to e.laps.toString(),
                "combo" to "x${e.bestCombo}", "time" to time(e.flightTime), "date" to e.date.take(10), "style" to look(i + 1, false))
        })
        values["boardRows"] = rows(if (screen == null || level == null) emptyList() else flow.patternTable.board(level).mapIndexed { i, e ->
            val picked = i == screen.selected
            mapOf("rank" to (if (picked) "> " else "") + (i + 1), "name" to e.name, "plane" to e.plane,
                "score" to String.format(Locale.ROOT, "%.2f", e.score), "time" to time(e.flightTime), "date" to e.date.take(10),
                "style" to look(i + 1, picked))
        })
    }

    private fun debrief(values: MutableMap<String, UiValue>, model: DebriefModel?) {
        values["debriefTitle"] = text(model?.title.orEmpty())
        values["hasRecording"] = flag(model?.recording != null)
        values["debriefRows"] = rows(model?.rows.orEmpty().mapIndexed { i, row ->
            val picked = i == model!!.selected
            val result = when {
                !row.decided -> "-"
                row.reason != null -> "0 (${row.reason.label})"
                else -> String.format(Locale.ROOT, "%.1f × %d = %.1f", row.tenths / 10f, row.figure.k, row.tenths * row.figure.k / 10f)
            }
            mapOf("label" to (if (picked) "> " else "  ") + "${i + 1}. ${row.figure.label}", "k" to "K ${row.figure.k}", "result" to result,
                "style" to when { picked -> "highlight"; row.reason != null -> "fail"; else -> "normal" },
                "kStyle" to if (picked) "highlight" else "muted")
        })
        values["deductionTitle"] = text(model?.let { "${it.row.figure.label}: deductions" }.orEmpty())
        values["deductions"] = rows(model?.deductionLines().orEmpty().map { mapOf("text" to it, "style" to if (it.startsWith("Scored 0")) "fail" else "normal") })
        values["hasNotice"] = flag(model?.notice != null)
        values["notice"] = text(model?.notice?.let { "$it: only the marks were kept." }.orEmpty())
        values["debriefStatus"] = text(model?.let(::debriefStatus).orEmpty())
        values["debriefKeys"] = text(if (model == null) "" else if (model.recording != null) REPLAY_KEYS else NO_REPLAY_KEYS)
    }

    private fun debriefStatus(model: DebriefModel): String {
        if (model.recording == null) return ""
        val view = if (model.view == DebriefView.PILOT) "Pilot view" else "Outside view"
        val speed = when (model.speed) { 0.5f -> "½×"; 2f -> "2×"; else -> "1×" }
        val state = if (model.playing) "playing" else "paused"
        return String.format(Locale.ROOT, "%s    %s    %s    %s / %s", view, speed, state, seconds(model.clock), seconds(model.duration))
    }

    private fun seconds(value: Float) = String.format(Locale.ROOT, "%.1f s", value)

    private fun time(seconds: Float): String {
        val s = seconds.toInt()
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
    }

    // --- what the UI sends back ---

    /** The only writable binding is the player's name; its length is the asset's `maxLength`, checked again here. */
    fun write(write: UiWrite): UiWriteResult {
        val value = write.value as? UiValue.Text
        if (write.binding != "playerName" || value == null) return UiWriteResult.Rejected("${write.binding} is read-only")
        if (value.value.length > NAME_LENGTH) return UiWriteResult.Rejected("A name has at most $NAME_LENGTH characters")
        playerName = value.value
        return UiWriteResult.Accepted(value)
    }

    /** A named action of the asset (`openRow#<row>` from a board row); false for a name the game does not know. */
    fun emit(name: String): Boolean {
        if (name.startsWith("openRow#")) {
            val row = name.removePrefix("openRow#").toIntOrNull() ?: return false
            flow.openRow(row)
            return true
        }
        when (name) {
            "freeFlight" -> flow.freeFlight()
            "f2bPattern" -> flow.f2bPattern()
            "scores" -> flow.scores()
            "quit" -> { flow.quitGame(); quit() }
            "assistPro" -> flow.chooseAssist(AssistLevel.PRO)
            "assistMid" -> flow.chooseAssist(AssistLevel.MID)
            "assistNoob" -> flow.chooseAssist(AssistLevel.NOOB)
            "back" -> flow.back()
            "previousPlane" -> flow.previousPlane()
            "nextPlane" -> flow.nextPlane()
            "choosePlane" -> flow.choosePlane()
            "resume" -> flow.resume()
            "mainMenu" -> flow.mainMenu()
            "saveName" -> flow.nameEntered(playerName)
            "retry" -> flow.retry()
            "debrief" -> flow.debrief()
            "tabFree" -> showTab(ScoreTab.FREE)
            "tabPro" -> showTab(ScoreTab.PRO)
            "tabMid" -> showTab(ScoreTab.MID)
            "tabNoob" -> showTab(ScoreTab.NOOB)
            else -> return false
        }
        return true
    }

    private fun showTab(tab: ScoreTab) {
        while (flow.screen.let { it is Screen.Scores && it.tab != tab }) flow.nextTab()
    }

    // --- the game's own keys ---

    /**
     * Whether [keycode] is one of the game's keys on the current screen, done here: Escape, Left and Right on plane
     * select and the score table, Up, Down and Enter on an F2B board, Enter on plane select, and all of the debrief's.
     * The rest (focus movement, pressing the focused button) belongs to the presenter. [fieldFocused] is whether the
     * name field has the focus: Enter there saves the name.
     */
    fun key(keycode: Int, fieldFocused: Boolean = false): Boolean {
        val screen = flow.screen
        if (screen is Screen.Debrief) return debriefKey(screen.model, keycode)
        val board = (screen as? Screen.Scores)?.tab?.level != null
        when (keycode) {
            Input.Keys.ESCAPE -> flow.back()
            Input.Keys.LEFT -> when (screen) {
                is Screen.PlaneSelect -> flow.previousPlane()
                is Screen.Scores -> flow.previousTab()
                else -> return false
            }
            Input.Keys.RIGHT -> when (screen) {
                is Screen.PlaneSelect -> flow.nextPlane()
                is Screen.Scores -> flow.nextTab()
                else -> return false
            }
            Input.Keys.UP -> if (board) flow.selectRow(-1) else return false
            Input.Keys.DOWN -> if (board) flow.selectRow(1) else return false
            Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER -> when {
                screen is Screen.NameEntry && fieldFocused -> flow.nameEntered(playerName)
                screen is Screen.PlaneSelect -> flow.choosePlane()
                board -> flow.openRow()
                else -> return false
            }
            else -> return false
        }
        return true
    }

    /** The debrief's keys: Up and Down select the manoeuvre, Space pauses, R restarts, 1 / 2 / 3 set the speed, V the view. */
    private fun debriefKey(model: DebriefModel, keycode: Int): Boolean {
        when (keycode) {
            Input.Keys.ESCAPE -> flow.back()
            Input.Keys.UP -> model.select(-1)
            Input.Keys.DOWN -> model.select(1)
            Input.Keys.SPACE -> model.togglePause()
            Input.Keys.R -> model.restart()
            Input.Keys.NUM_1, Input.Keys.NUMPAD_1 -> model.setSpeed(0)
            Input.Keys.NUM_2, Input.Keys.NUMPAD_2 -> model.setSpeed(1)
            Input.Keys.NUM_3, Input.Keys.NUMPAD_3 -> model.setSpeed(2)
            Input.Keys.V -> model.toggleView()
            else -> return false
        }
        return true
    }
}
