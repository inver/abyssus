/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ScreenViewport
import net.nevinsky.abyssus.app.game.controlline.flight.FlightSession
import net.nevinsky.abyssus.app.game.controlline.flow.FlightReport
import net.nevinsky.abyssus.app.game.controlline.flow.FlightResult
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Mode
import net.nevinsky.abyssus.app.game.controlline.flow.NAME_LENGTH
import net.nevinsky.abyssus.app.game.controlline.flow.OFFERED_LEVELS
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.flow.ScoreTab
import net.nevinsky.abyssus.app.game.controlline.pattern.AssistLevel
import net.nevinsky.abyssus.app.game.controlline.pattern.DEBRIEF_SPEEDS
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefModel
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefView
import net.nevinsky.abyssus.app.game.controlline.render.FLOWN_COLOR
import net.nevinsky.abyssus.app.game.controlline.render.IDEAL_COLOR
import net.nevinsky.abyssus.app.game.controlline.pattern.FLIGHT_TIME
import net.nevinsky.abyssus.app.game.controlline.pattern.HeightWarning
import net.nevinsky.abyssus.app.game.controlline.pattern.PAUSE_HEIGHT_MAX
import net.nevinsky.abyssus.app.game.controlline.pattern.PAUSE_HEIGHT_MIN
import net.nevinsky.abyssus.app.game.controlline.pattern.PAUSE_LAPS
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternJudge
import net.nevinsky.abyssus.app.game.controlline.pattern.SCHEDULE
import net.nevinsky.abyssus.app.game.controlline.pattern.SheetEntry
import java.util.Locale

/** The HUD's tension bar reads up to this (N). */
private const val TENSION_SCALE = 60f

/** A maneuver's name shows on the HUD this long (s). */
private const val TOAST_TIME = 2.5f

private val HIGHLIGHT = Color(1f, 0.85f, 0.2f, 1f)

/**
 * The game's screens and HUD in Scene2D with libGDX's sample `uiskin`: each [Screen] of [flow] is a layout built when
 * the flow moves to it. Buttons and keys only send events to [flow]; [quit] closes the game. GL thread only.
 */
class GameUi(private val flow: GameFlow, private val skin: Skin, private val quit: () -> Unit) : Disposable {
    val stage = Stage(ScreenViewport())
    private var shown: Screen? = null
    private var menu: KeyMenu? = null
    private var nameField: TextField? = null
    private var shownRevision = 0
    private val debriefStatus = Label("", skin)

    // the HUD
    private val tension = Label("", skin)
    private val tensionBar = ProgressBar(0f, TENSION_SCALE, 0.1f, false, skin)
    private val laps = Label("", skin)
    private val score = Label("", skin)
    private val combo = Label("", skin)
    private val fuel = Label("", skin)
    private val clock = Label("", skin)
    private val next = Label("", skin)
    private val pauseLaps = Label("", skin)
    private val warning = Label("", skin).apply { color = Color.SALMON }
    private val toast = Label("", skin).apply { setFontScale(1.6f); color = HIGHLIGHT }
    private var toastShown: Any? = null
    private var toastDecided = 0
    private var toastJudge: PatternJudge? = null
    private var toastTime = 0f

    /** Follows the flow to its current screen and the HUD to [session] (and the F2B [judge] of a pattern flight). */
    fun update(session: FlightSession?, judge: PatternJudge?, seconds: Float) {
        val screen = flow.screen
        if (screen != shown || (screen is Screen.Debrief && screen.model.revision != shownRevision)) {
            shown = screen
            rebuild(screen)
        }
        if (screen is Screen.Debrief) debriefStatus.setText(debriefStatusText(screen.model))
        if (session != null && (shown is Screen.Flying || shown is Screen.Paused)) hud(session, judge, seconds)
        stage.act(seconds)
    }

    fun draw() = stage.draw()

    fun resize(width: Int, height: Int) = stage.viewport.update(width, height, true)

    /** A key on a menu screen: arrows and Enter move through the menu, Left / Right change the plane or the score tab, Escape goes back. */
    fun keyDown(keycode: Int): Boolean {
        (shown as? Screen.Debrief)?.let { return debriefKey(it.model, keycode) }
        when (keycode) {
            Input.Keys.ESCAPE -> flow.back()
            Input.Keys.LEFT -> when (shown) {
                is Screen.PlaneSelect -> flow.previousPlane()
                is Screen.Scores -> flow.previousTab()
                else -> return false
            }
            Input.Keys.RIGHT -> when (shown) {
                is Screen.PlaneSelect -> flow.nextPlane()
                is Screen.Scores -> flow.nextTab()
                else -> return false
            }
            Input.Keys.UP -> if (onBoard()) flow.selectRow(-1) else menu?.move(-1) ?: return false
            Input.Keys.DOWN -> if (onBoard()) flow.selectRow(1) else menu?.move(1) ?: return false
            Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER -> when {
                shown is Screen.NameEntry -> nameField?.let { flow.nameEntered(it.text) }
                shown is Screen.PlaneSelect -> flow.choosePlane()
                onBoard() -> flow.openRow()
                else -> menu?.press() ?: return false
            }
            else -> return false
        }
        return true
    }

    /** Whether the score table is on an F2B tab, where Up, Down and Enter work on its rows. */
    private fun onBoard(): Boolean = (shown as? Screen.Scores)?.tab?.level != null

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

    private fun modeOf(screen: Screen?): Mode? = when (screen) {
        is Screen.Flying -> screen.mode
        is Screen.Paused -> screen.flying.mode
        else -> null
    }

    private fun rebuild(screen: Screen) {
        stage.clear()
        menu = null
        nameField = null
        (screen as? Screen.Debrief)?.let { shownRevision = it.model.revision }
        val root = Table(skin).apply { setFillParent(true) }
        stage.addActor(root)
        when (screen) {
            Screen.MainMenu -> mainMenu(root)
            Screen.AssistSelect -> assistSelect(root)
            is Screen.PlaneSelect -> planeSelect(root, screen)
            is Screen.Flying -> hudLayout(root, screen.mode)
            is Screen.Paused -> {
                hudLayout(root, screen.flying.mode)
                val window = Table(skin).apply { setFillParent(true) }
                stage.addActor(window)
                window.add(title("Paused")).padBottom(20f).row()
                menu(window, listOf(button("Resume", flow::resume), button("Main menu", flow::mainMenu)))
            }
            is Screen.NameEntry -> nameEntry(root, screen)
            is Screen.GameOver -> gameOver(root, screen)
            is Screen.Scores -> scores(root, screen)
            is Screen.Debrief -> debrief(root, screen.model)
        }
    }

    private fun mainMenu(root: Table) {
        root.add(title("Control Line")).padBottom(40f).row()
        val free = button("Free flight", flow::freeFlight).apply { isDisabled = !flow.freeEnabled }
        val pattern = button("F2B pattern", flow::f2bPattern).apply { isDisabled = !flow.patternEnabled }
        menu(root, listOf(free, pattern, button("Scores", flow::scores), button("Quit") { flow.quitGame(); quit() }))
        flow.message?.let { root.add(Label(it, skin).apply { color = HIGHLIGHT }).padTop(20f).row() }
    }

    private fun assistSelect(root: Table) {
        root.add(title("F2B pattern")).padBottom(10f).row()
        root.add(Label("The 15 manoeuvres of the FAI schedule in 7 minutes. Choose the assist level.", skin)).padBottom(24f).row()
        val buttons = OFFERED_LEVELS.map { level -> button(level.label) { flow.chooseAssist(level) } }
        menu(root, buttons + button("Back", flow::back))
        for (level in OFFERED_LEVELS) root.add(Label("${level.label}: ${describe(level)}", skin).apply { color = Color.LIGHT_GRAY }).padTop(4f).row()
    }

    private fun describe(level: AssistLevel) = when (level) {
        AssistLevel.PRO -> "no help: tension, fuel and the clock"
        AssistLevel.MID -> "the next manoeuvre, the pause count, a height warning and each mark as you fly"
        AssistLevel.NOOB -> "all of Mid's, and the ideal track drawn ahead of the plane"
    }

    private fun planeSelect(root: Table, screen: Screen.PlaneSelect) {
        val choices = flow.choices(screen.mode)
        val plane = choices[screen.index]
        root.bottom().padBottom(40f)
        (screen.mode as? Mode.Pattern)?.let { root.add(Label("F2B pattern - ${it.level.label}", skin).apply { color = HIGHLIGHT }).colspan(3).row() }
        root.add(Label("Choose a plane  (${screen.index + 1} of ${choices.size})", skin)).colspan(3).row()
        root.add(button("<", flow::previousPlane)).width(50f)
        root.add(title(plane.name)).width(420f).center()
        root.add(button(">", flow::nextPlane)).width(50f).row()
        val facts = String.format(Locale.ROOT, "%s    Lines %.0f m    Mass %.2f kg    Fuel %.0f s",
            plane.planeClass.name.lowercase().replaceFirstChar { it.uppercase() }, plane.lineLength, plane.mass, plane.fuelTime)
        root.add(Label(facts, skin)).colspan(3).padTop(10f).row()
        val buttons = Table(skin)
        buttons.add(button("Fly  (Enter)", flow::choosePlane)).pad(6f)
        buttons.add(button("Back  (Esc)", flow::back)).pad(6f)
        root.add(buttons).colspan(3).padTop(16f).row()
    }

    private fun hudLayout(root: Table, mode: Mode) {
        root.top().left().pad(16f)
        val panel = Table(skin).apply { background = skin.getDrawable("default-pane"); pad(10f) }
        panel.defaults().left().pad(2f, 6f, 2f, 6f)
        panel.add(Label("Tension", skin)); panel.add(tensionBar).width(140f); panel.add(tension).width(70f).row()
        when (mode) {
            Mode.Free -> {
                panel.add(Label("Laps", skin)); panel.add(laps).colspan(2).row()
                panel.add(Label("Score", skin)); panel.add(score).colspan(2).row()
                panel.add(Label("Combo", skin)); panel.add(combo).colspan(2).row()
                panel.add(Label("Fuel", skin)); panel.add(fuel).colspan(2).row()
            }
            is Mode.Pattern -> {
                panel.add(Label("Fuel", skin)); panel.add(fuel).colspan(2).row()
                panel.add(Label("Time", skin)); panel.add(clock).colspan(2).row()
                if (mode.level.prompted) {
                    panel.add(Label("Next", skin)); panel.add(next).colspan(2).row()
                    panel.add(Label("Pause", skin)); panel.add(pauseLaps).colspan(2).row()
                    panel.add(warning).colspan(3).row()
                }
            }
        }
        root.add(panel).top().left()
        val toasts = Table(skin).apply { setFillParent(true); top().padTop(60f) }
        toasts.add(toast)
        stage.addActor(toasts)
        root.row()
        root.add(Label("W / S or Up / Down or the mouse: handle    Esc: pause", skin).apply { color = Color.LIGHT_GRAY })
            .expand().bottom().left()
    }

    private fun hud(session: FlightSession, judge: PatternJudge?, seconds: Float) {
        val flight = session.flight
        val keeper = flight.scoring.keeper
        tension.setText(String.format(Locale.ROOT, "%.1f N", flight.tension))
        tensionBar.value = flight.tension.coerceAtMost(TENSION_SCALE)
        fuel.setText(String.format(Locale.ROOT, "%.0f s", flight.fuelLeft))
        val mode = modeOf(shown)
        if (mode is Mode.Pattern && judge != null) {
            clock.setText("${time(flight.time)} / ${time(FLIGHT_TIME)}")
            if (mode.level.prompted) midGuidance(judge)
        } else {
            laps.setText(keeper.laps.toString())
            score.setText(keeper.score.toString())
            combo.setText("x${keeper.multiplier}")
            val last = keeper.lastManeuver
            if (last != null && last !== toastShown) {
                toastShown = last
                toast.setText(last.maneuver.label + if (keeper.multiplier > 1) "  x${keeper.multiplier}" else "")
                toastTime = TOAST_TIME
            }
        }
        toastTime -= seconds
        toast.isVisible = toastTime > 0f
    }

    /** The Mid and Noob assist levels' guidance: the next manoeuvre, the pause count, the height warning and a toast per mark. */
    private fun midGuidance(judge: PatternJudge) {
        next.setText(judge.expected?.let { "${it.label} (K ${it.k})" } ?: "Land")
        pauseLaps.setText(judge.pauseLaps?.let { String.format(Locale.ROOT, "%.1f / %.1f laps", it.coerceAtMost(PAUSE_LAPS), PAUSE_LAPS) } ?: "-")
        warning.setText(when (judge.heightWarning) {
            HeightWarning.BELOW -> "Below ${PAUSE_HEIGHT_MIN.toInt()} m"
            HeightWarning.ABOVE -> "Above ${PAUSE_HEIGHT_MAX.toInt()} m"
            null -> ""
        })
        if (judge !== toastJudge) {
            toastJudge = judge
            toastDecided = 0
        }
        if (judge.decided != toastDecided) {
            toastDecided = judge.decided
            judge.lastScored?.let {
                toast.setText(entryText(it))
                toastTime = TOAST_TIME
            }
        }
    }

    /** `Take-off 8.5 x 2 = 17.0`, or `Take-off 0 (too soon)`. */
    private fun entryText(entry: SheetEntry): String = if (entry.reason == null) {
        String.format(Locale.ROOT, "%s %.1f × %d = %.1f", entry.figure.label, entry.mark, entry.figure.k, entry.points / 10f)
    } else "${entry.figure.label} 0 (${entry.reason.label})"

    private fun summary(table: Table, report: FlightReport) {
        table.add(title("GAME OVER")).padBottom(10f).row()
        table.add(Label(report.end.label, skin).apply { setFontScale(1.5f); color = HIGHLIGHT }).padBottom(10f).row()
        when (val result = report.result) {
            is FlightResult.Free -> {
                val facts = Table(skin)
                facts.defaults().left().pad(2f, 10f, 2f, 10f)
                for ((name, value) in listOf(
                    "Plane" to report.plane, "Score" to result.score.toString(), "Laps" to result.laps.toString(),
                    "Best combo" to "x${result.bestCombo}", "Flight time" to time(report.flightTime),
                )) {
                    facts.add(Label(name, skin))
                    facts.add(Label(value, skin)).row()
                }
                table.add(facts).padBottom(20f).row()
            }
            is FlightResult.Pattern -> {
                table.add(Label("F2B pattern - ${result.level.label} - ${report.plane}", skin).apply { color = Color.LIGHT_GRAY }).padBottom(8f).row()
                val sheet = Table(skin).apply { background = skin.getDrawable("default-pane"); pad(8f) }
                sheet.defaults().pad(1f, 8f, 1f, 8f).left()
                for ((i, figure) in SCHEDULE.withIndex()) {
                    val entry = result.sheet[figure]
                    sheet.add(Label("${i + 1}. ${figure.label}", skin))
                    sheet.add(Label("K ${figure.k}", skin).apply { color = Color.LIGHT_GRAY })
                    val text = when {
                        entry == null -> "-"
                        entry.reason != null -> "0 (${entry.reason.label})"
                        else -> String.format(Locale.ROOT, "%.1f × %d = %.1f", entry.mark, figure.k, entry.points / 10f)
                    }
                    sheet.add(Label(text, skin).apply { if (entry?.reason != null) color = Color.SALMON })
                    sheet.row()
                }
                table.add(sheet).padBottom(8f).row()
                table.add(Label("Score ${result.sheet.score}    Flight time ${time(report.flightTime)}", skin).apply { setFontScale(1.2f) }).padBottom(12f).row()
            }
        }
    }

    private fun nameEntry(root: Table, screen: Screen.NameEntry) {
        summary(root, screen.report)
        root.add(Label("Your flight made the top 10. Your name:", skin)).padBottom(8f).row()
        val field = TextField(screen.name, skin).apply {
            maxLength = NAME_LENGTH
            setCursorPosition(text.length)
            selectAll()
        }
        nameField = field
        val row = Table(skin)
        row.add(field).width(220f).padRight(8f)
        row.add(button("Save") { flow.nameEntered(field.text) })
        root.add(row).row()
        stage.keyboardFocus = field
    }

    private fun gameOver(root: Table, screen: Screen.GameOver) {
        summary(root, screen.report)
        screen.rank?.let { root.add(Label("Saved in place $it", skin)).padBottom(16f).row() }
        val buttons = mutableListOf(button("Retry", flow::retry))
        if (screen.report.result is FlightResult.Pattern) buttons += button("Debrief", flow::debrief)
        menu(root, buttons + listOf(button("Scores", flow::scores), button("Main menu", flow::mainMenu)))
    }

    private fun debriefStatusText(model: DebriefModel): String {
        if (model.recording == null) return ""
        val view = if (model.view == DebriefView.PILOT) "Pilot view" else "Outside view"
        val speed = when (model.speed) { 0.5f -> "½×"; 2f -> "2×"; else -> "1×" }
        val state = if (model.playing) "playing" else "paused"
        return String.format(Locale.ROOT, "%s    %s    %s    %s / %s", view, speed, state, clockText(model.clock), clockText(model.duration))
    }

    private fun clockText(seconds: Float) = String.format(Locale.ROOT, "%.1f s", seconds)

    private fun debrief(root: Table, model: DebriefModel) {
        root.top().left().pad(16f)
        val sheet = Table(skin).apply { background = skin.getDrawable("default-pane"); pad(10f) }
        sheet.defaults().left().pad(1f, 6f, 1f, 6f)
        sheet.add(Label(model.title, skin).apply { color = HIGHLIGHT }).colspan(3).padBottom(4f).row()
        for ((i, row) in model.rows.withIndex()) {
            val picked = i == model.selected
            val text = when {
                !row.decided -> "-"
                row.reason != null -> "0 (${row.reason.label})"
                else -> String.format(Locale.ROOT, "%.1f × %d = %.1f", row.tenths / 10f, row.figure.k, row.tenths * row.figure.k / 10f)
            }
            val color = when {
                picked -> HIGHLIGHT
                row.reason != null -> Color.SALMON
                else -> Color.WHITE
            }
            sheet.add(Label((if (picked) "> " else "  ") + "${i + 1}. ${row.figure.label}", skin).apply { this.color = color })
            sheet.add(Label("K ${row.figure.k}", skin).apply { this.color = if (picked) HIGHLIGHT else Color.LIGHT_GRAY })
            sheet.add(Label(text, skin).apply { this.color = color }).row()
        }
        if (model.recording != null) {
            val legend = Table(skin)
            legend.add(Label("Ideal track", skin).apply { color = IDEAL_COLOR }).padRight(16f)
            legend.add(Label("Flown track", skin).apply { color = FLOWN_COLOR }).padRight(16f)
            legend.add(Label("Base and 45° parallel", skin).apply { color = Color.LIGHT_GRAY })
            sheet.add(legend).colspan(3).padTop(6f).row()
        }
        root.add(sheet).top().left().row()

        val details = Table(skin).apply { background = skin.getDrawable("default-pane"); pad(10f) }
        details.defaults().left().pad(1f, 6f, 1f, 6f)
        details.add(Label("${model.row.figure.label}: deductions", skin).apply { color = HIGHLIGHT }).row()
        val lines = model.deductionLines()
        for (line in lines) details.add(Label(line, skin).apply { if (line.startsWith("Scored 0")) color = Color.SALMON }).row()
        model.notice?.let { details.add(Label("$it: only the marks were kept.", skin).apply { color = HIGHLIGHT }).padTop(4f).row() }
        root.add(details).top().left().padTop(10f).row()

        root.add(debriefStatus).left().padTop(10f).row()
        val keys = if (model.recording != null) {
            "Up / Down: manoeuvre    Space: pause    R: restart    1 / 2 / 3: speed ½× 1× 2×    V: view    Esc: back"
        } else "Up / Down: manoeuvre    Esc: back"
        root.add(Label(keys, skin).apply { color = Color.LIGHT_GRAY }).expand().bottom().left()
    }

    private fun scores(root: Table, screen: Screen.Scores) {
        root.add(title("Best flights")).padBottom(10f).row()
        val tabs = Table(skin)
        for (tab in ScoreTab.entries) {
            tabs.add(TextButton(tab.label, skin, "toggle").apply {
                isChecked = tab == screen.tab
                addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent, x: Float, y: Float) {
                        while (flow.screen.let { it is Screen.Scores && it.tab != tab }) flow.nextTab()
                    }
                })
            }).width(130f).pad(4f)
        }
        root.add(tabs).padBottom(8f).row()
        val hint = if (screen.tab.level != null) "Left / Right: change the tab    Up / Down, Enter or a click: replay a flight" else "Left / Right: change the tab"
        root.add(Label(hint, skin).apply { color = Color.LIGHT_GRAY }).padBottom(10f).row()
        val level = screen.tab.level
        if (level == null && flow.table.damaged) {
            root.add(Label("The old scores could not be read; they were kept as scores.json.bad.", skin).apply { color = HIGHLIGHT })
                .padBottom(10f).row()
        }
        if (level != null && flow.patternTable.damaged) {
            root.add(Label("The old F2B scores could not be read; they were kept as f2b-scores.json.bad.", skin).apply { color = HIGHLIGHT })
                .padBottom(10f).row()
        }
        val table = Table(skin).apply { background = skin.getDrawable("default-pane"); pad(12f) }
        table.defaults().pad(3f, 10f, 3f, 10f).left()
        val highlight = screen.highlight.takeIf { screen.highlightTab == screen.tab }
        if (level == null) {
            for (h in listOf("#", "Name", "Plane", "Score", "Laps", "Combo", "Time", "Date")) table.add(Label(h, skin).apply { color = Color.LIGHT_GRAY })
            table.row()
            val entries = flow.table.entries
            if (entries.isEmpty()) table.add(Label("No flights yet", skin)).colspan(8).row()
            entries.forEachIndexed { i, e ->
                val color = if (highlight == i + 1) HIGHLIGHT else Color.WHITE
                for (cell in listOf((i + 1).toString(), e.name, e.plane, e.score.toString(), e.laps.toString(), "x${e.bestCombo}", time(e.flightTime), e.date.take(10))) {
                    table.add(Label(cell, skin).apply { this.color = color })
                }
                table.row()
            }
        } else {
            for (h in listOf("#", "Name", "Plane", "Score", "Time", "Date")) table.add(Label(h, skin).apply { color = Color.LIGHT_GRAY })
            table.row()
            val entries = flow.patternTable.board(level)
            if (entries.isEmpty()) table.add(Label("No flights yet", skin)).colspan(6).row()
            entries.forEachIndexed { i, e ->
                val color = if (highlight == i + 1) HIGHLIGHT else Color.WHITE
                val picked = i == screen.selected
                val cells = listOf((i + 1).toString(), e.name, e.plane, String.format(Locale.ROOT, "%.2f", e.score), time(e.flightTime), e.date.take(10))
                for ((c, cell) in cells.withIndex()) {
                    table.add(Label((if (picked && c == 0) "> " else "") + cell, skin).apply {
                        this.color = if (picked) Color.CYAN else color
                        addListener(object : ClickListener() {
                            override fun clicked(event: InputEvent, x: Float, y: Float) = flow.openRow(i)
                        })
                    })
                }
                table.row()
            }
        }
        root.add(table).padBottom(20f).row()
        menu(root, listOf(button("Back", flow::back)))
    }

    private fun title(text: String) = Label(text, skin).apply { setFontScale(2.5f); setAlignment(Align.center) }

    private fun button(text: String, action: () -> Unit) = TextButton(text, skin, "toggle").apply {
        userObject = action
        addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                if (!isDisabled) action()
            }
        })
    }

    private fun menu(table: Table, buttons: List<TextButton>) {
        for (b in buttons) table.add(b).width(220f).height(44f).pad(6f).row()
        menu = KeyMenu(buttons)
    }

    private fun time(seconds: Float): String {
        val s = seconds.toInt()
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
    }

    override fun dispose() = stage.dispose()

    /** A column of buttons that the arrow keys move through and Enter presses; the chosen one shows pressed. */
    private class KeyMenu(private val buttons: List<TextButton>) {
        private var index = buttons.indexOfFirst { !it.isDisabled }

        init {
            show()
        }

        fun move(by: Int) {
            if (index < 0) return
            do index = Math.floorMod(index + by, buttons.size) while (buttons[index].isDisabled)
            show()
        }

        fun press() {
            if (index < 0) return
            @Suppress("UNCHECKED_CAST")
            (buttons[index].userObject as () -> Unit)()
        }

        private fun show() = buttons.forEachIndexed { i, b -> b.setProgrammaticChangeEvents(false); b.isChecked = i == index }
    }
}
