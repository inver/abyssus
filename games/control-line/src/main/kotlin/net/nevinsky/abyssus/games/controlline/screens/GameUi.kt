/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.screens

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
import net.nevinsky.abyssus.games.controlline.flight.FlightSession
import net.nevinsky.abyssus.games.controlline.flow.FlightReport
import net.nevinsky.abyssus.games.controlline.flow.GameFlow
import net.nevinsky.abyssus.games.controlline.flow.NAME_LENGTH
import net.nevinsky.abyssus.games.controlline.flow.Screen
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

    // the HUD
    private val tension = Label("", skin)
    private val tensionBar = ProgressBar(0f, TENSION_SCALE, 0.1f, false, skin)
    private val laps = Label("", skin)
    private val score = Label("", skin)
    private val combo = Label("", skin)
    private val fuel = Label("", skin)
    private val toast = Label("", skin).apply { setFontScale(1.6f); color = HIGHLIGHT }
    private var toastShown: Any? = null
    private var toastTime = 0f

    /** Follows the flow to its current screen and the HUD to [session]. */
    fun update(session: FlightSession?, seconds: Float) {
        if (flow.screen != shown) {
            shown = flow.screen
            rebuild(flow.screen)
        }
        if (session != null && (shown is Screen.Flying || shown is Screen.Paused)) hud(session, seconds)
        stage.act(seconds)
    }

    fun draw() = stage.draw()

    fun resize(width: Int, height: Int) = stage.viewport.update(width, height, true)

    /** A key on a menu screen: arrows and Enter move through the menu, Left / Right change the plane, Escape goes back. */
    fun keyDown(keycode: Int): Boolean {
        when (keycode) {
            Input.Keys.ESCAPE -> flow.back()
            Input.Keys.LEFT -> if (shown is Screen.PlaneSelect) flow.previousPlane() else return false
            Input.Keys.RIGHT -> if (shown is Screen.PlaneSelect) flow.nextPlane() else return false
            Input.Keys.UP -> menu?.move(-1) ?: return false
            Input.Keys.DOWN -> menu?.move(1) ?: return false
            Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER -> when {
                shown is Screen.NameEntry -> nameField?.let { flow.nameEntered(it.text) }
                shown is Screen.PlaneSelect -> flow.choosePlane()
                else -> menu?.press() ?: return false
            }
            else -> return false
        }
        return true
    }

    private fun rebuild(screen: Screen) {
        stage.clear()
        menu = null
        nameField = null
        val root = Table(skin).apply { setFillParent(true) }
        stage.addActor(root)
        when (screen) {
            Screen.MainMenu -> mainMenu(root)
            is Screen.PlaneSelect -> planeSelect(root, screen)
            is Screen.Flying -> hudLayout(root)
            is Screen.Paused -> {
                hudLayout(root)
                val window = Table(skin).apply { setFillParent(true) }
                stage.addActor(window)
                window.add(title("Paused")).padBottom(20f).row()
                menu(window, listOf(button("Resume", flow::resume), button("Main menu", flow::mainMenu)))
            }
            is Screen.NameEntry -> nameEntry(root, screen)
            is Screen.GameOver -> gameOver(root, screen)
            is Screen.Scores -> scores(root, screen)
        }
    }

    private fun mainMenu(root: Table) {
        root.add(title("Control Line")).padBottom(40f).row()
        val start = button("Start", flow::start).apply { isDisabled = !flow.startEnabled }
        menu(root, listOf(start, button("Scores", flow::scores), button("Quit") { flow.quitGame(); quit() }))
        flow.message?.let { root.add(Label(it, skin).apply { color = HIGHLIGHT }).padTop(20f).row() }
    }

    private fun planeSelect(root: Table, screen: Screen.PlaneSelect) {
        val plane = flow.planes[screen.index]
        root.bottom().padBottom(40f)
        root.add(Label("Choose a plane  (${screen.index + 1} of ${flow.planes.size})", skin)).colspan(3).row()
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

    private fun hudLayout(root: Table) {
        root.top().left().pad(16f)
        val panel = Table(skin).apply { background = skin.getDrawable("default-pane"); pad(10f) }
        panel.defaults().left().pad(2f, 6f, 2f, 6f)
        panel.add(Label("Tension", skin)); panel.add(tensionBar).width(140f); panel.add(tension).width(70f).row()
        panel.add(Label("Laps", skin)); panel.add(laps).colspan(2).row()
        panel.add(Label("Score", skin)); panel.add(score).colspan(2).row()
        panel.add(Label("Combo", skin)); panel.add(combo).colspan(2).row()
        panel.add(Label("Fuel", skin)); panel.add(fuel).colspan(2).row()
        root.add(panel).top().left()
        val toasts = Table(skin).apply { setFillParent(true); top().padTop(60f) }
        toasts.add(toast)
        stage.addActor(toasts)
        root.row()
        root.add(Label("W / S or Up / Down or the mouse: handle    Esc: pause", skin).apply { color = Color.LIGHT_GRAY })
            .expand().bottom().left()
    }

    private fun hud(session: FlightSession, seconds: Float) {
        val flight = session.flight
        val keeper = flight.scoring.keeper
        tension.setText(String.format(Locale.ROOT, "%.1f N", flight.tension))
        tensionBar.value = flight.tension.coerceAtMost(TENSION_SCALE)
        laps.setText(keeper.laps.toString())
        score.setText(keeper.score.toString())
        combo.setText("x${keeper.multiplier}")
        fuel.setText(String.format(Locale.ROOT, "%.0f s", flight.fuelLeft))
        val last = keeper.lastManeuver
        if (last != null && last !== toastShown) {
            toastShown = last
            toast.setText(last.maneuver.label + if (keeper.multiplier > 1) "  x${keeper.multiplier}" else "")
            toastTime = TOAST_TIME
        }
        toastTime -= seconds
        toast.isVisible = toastTime > 0f
    }

    private fun summary(table: Table, report: FlightReport) {
        table.add(title("GAME OVER")).padBottom(10f).row()
        table.add(Label(report.end.label, skin).apply { setFontScale(1.5f); color = HIGHLIGHT }).padBottom(16f).row()
        val facts = Table(skin)
        facts.defaults().left().pad(2f, 10f, 2f, 10f)
        for ((name, value) in listOf(
            "Plane" to report.plane, "Score" to report.score.toString(), "Laps" to report.laps.toString(),
            "Best combo" to "x${report.bestCombo}", "Flight time" to time(report.flightTime),
        )) {
            facts.add(Label(name, skin))
            facts.add(Label(value, skin)).row()
        }
        table.add(facts).padBottom(20f).row()
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
        menu(root, listOf(button("Retry", flow::retry), button("Scores", flow::scores), button("Main menu", flow::mainMenu)))
    }

    private fun scores(root: Table, screen: Screen.Scores) {
        root.add(title("Best flights")).padBottom(16f).row()
        if (flow.table.damaged) {
            root.add(Label("The old scores could not be read; they were kept as scores.json.bad.", skin).apply { color = HIGHLIGHT })
                .padBottom(10f).row()
        }
        val table = Table(skin).apply { background = skin.getDrawable("default-pane"); pad(12f) }
        table.defaults().pad(3f, 10f, 3f, 10f).left()
        for (h in listOf("#", "Name", "Plane", "Score", "Laps", "Combo", "Time", "Date")) table.add(Label(h, skin).apply { color = Color.LIGHT_GRAY })
        table.row()
        val entries = flow.table.entries
        if (entries.isEmpty()) table.add(Label("No flights yet", skin)).colspan(8).row()
        entries.forEachIndexed { i, e ->
            val color = if (screen.highlight == i + 1) HIGHLIGHT else Color.WHITE
            for (cell in listOf((i + 1).toString(), e.name, e.plane, e.score.toString(), e.laps.toString(), "x${e.bestCombo}", time(e.flightTime), e.date.take(10))) {
                table.add(Label(cell, skin).apply { this.color = color })
            }
            table.row()
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
