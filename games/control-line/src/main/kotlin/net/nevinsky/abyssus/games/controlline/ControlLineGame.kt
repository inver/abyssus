/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import net.nevinsky.abyssus.assets.AssetLoading
import net.nevinsky.abyssus.assets.AssetLog
import net.nevinsky.abyssus.assets.ShaderSource
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.games.controlline.flight.CONTROL_TENSION
import net.nevinsky.abyssus.games.controlline.flight.FlightSession
import net.nevinsky.abyssus.games.controlline.flow.GameFlow
import net.nevinsky.abyssus.games.controlline.flow.HandleInput
import net.nevinsky.abyssus.games.controlline.flow.Screen
import net.nevinsky.abyssus.games.controlline.render.Cameras
import net.nevinsky.abyssus.games.controlline.render.FieldLoader
import net.nevinsky.abyssus.games.controlline.render.FieldRenderer
import net.nevinsky.abyssus.games.controlline.render.FieldScene
import net.nevinsky.abyssus.games.controlline.render.LineSegment
import net.nevinsky.abyssus.games.controlline.score.ScoreTable
import net.nevinsky.abyssus.games.controlline.screens.GameUi
import net.nevinsky.abyssus.physics.PhysicsAssets
import net.nevinsky.abyssus.physics.jolt.JoltNatives
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val TAUT_LINE = Color(0.95f, 0.95f, 0.95f, 1f)
private val SLACK_LINE = Color(0.5f, 0.5f, 0.5f, 1f)

/**
 * The game (design decision 8): the field of [project] drawn by [FieldRenderer], the screens of [GameFlow] by
 * [GameUi], and a [FlightSession] while flying. Everything runs on the LWJGL3 main thread (design decision 11); only
 * asset preparation runs on [executor]. Scores are kept in [scoresFile].
 */
class ControlLineGame(private val project: Path, private val scoresFile: Path) : ApplicationAdapter() {
    private val log = AssetLog { message, error ->
        System.err.println(message)
        error?.printStackTrace()
    }
    private val executor: ExecutorService = Executors.newFixedThreadPool(2) { r -> Thread(r, "asset-prepare").apply { isDaemon = true } }
    private val natives = JoltNatives()
    private val input = HandleInput()
    private val loader = FieldLoader(project, log)

    private lateinit var flow: GameFlow
    private lateinit var renderer: FieldRenderer
    private lateinit var ui: GameUi
    private lateinit var skin: Skin
    private lateinit var cameras: Cameras
    private lateinit var parked: FieldScene

    private var session: FlightSession? = null
    private var sessionScene: FieldScene? = null
    private var flying: Screen.Flying? = null

    override fun create() {
        // TerrainMesh sets every layer's uniforms; the game's shader may not use them all
        ShaderProgram.pedantic = false
        parked = loader.load()
        flow = GameFlow(parked.planes, ScoreTable(scoresFile))
        val json = JsonProcessor()
        val loading = AssetLoading(json, log, executor, ShaderSource("/shader/sky", AssetLoading::class.java))
        renderer = FieldRenderer(loading, project.toFile(), ShaderSource("/shader", ControlLineGame::class.java))
        skin = Skin(Gdx.files.classpath("uiskin/uiskin.json"))
        ui = GameUi(flow, skin) { Gdx.app.exit() }
        cameras = Cameras(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        Gdx.input.inputProcessor = InputMultiplexer(Keys(), ui.stage)
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        cameras.resize(width.toFloat(), height.toFloat())
        ui.resize(width, height)
    }

    override fun render() {
        val seconds = Gdx.graphics.deltaTime.coerceIn(0f, 0.1f)
        followFlow()
        val screen = flow.screen
        val s = session
        if (s != null && screen is Screen.Flying) {
            s.advance(seconds, input)
            s.report()?.let(flow::flightEnded)
        } else if (s != null && (screen is Screen.GameOver || screen is Screen.NameEntry)) {
            // the plane comes to rest; the flight itself is over
            s.advance(seconds, HandleInput())
        }

        val scene = sessionScene ?: parked
        var lines = emptyList<LineSegment>()
        when (screen) {
            is Screen.PlaneSelect -> parked.entity(flow.planes[screen.index].entityId)?.let { plane ->
                val p = parked.position(plane)
                cameras.planeSelect(p.localPosition, p.localRotation.transform(Vector3(0f, 0f, 1f)).also { it.y = 0f }.nor(), seconds)
            }
            else -> if (s != null) {
                val pilot = scene.position(s.rig.pilot).localPosition
                cameras.flying(pilot, scene.position(s.plane).localPosition)
                val color = if (s.flight.tension >= CONTROL_TENSION) TAUT_LINE else SLACK_LINE
                lines = s.lines().map { (a, b) -> LineSegment(a, b, color) }
            } else {
                cameras.menu(scene.pilot?.getComponent(PositionComponent::class.java)?.localPosition ?: Vector3(), seconds)
            }
        }
        renderer.draw(scene, cameras.camera, lines, hidden = if (s != null && screen !is Screen.PlaneSelect) s.rig.pilot else null)
        ui.update(s, seconds)
        ui.draw()
    }

    /** Starts a flight when the flow flies a plane (again), and ends it when the flow leaves the flight's screens. */
    private fun followFlow() {
        when (val screen = flow.screen) {
            is Screen.Flying -> if (screen != flying) startFlight(screen)
            is Screen.Paused, is Screen.NameEntry, is Screen.GameOver -> Unit
            is Screen.Scores -> if (screen.from !is Screen.GameOver) endFlight()
            else -> endFlight()
        }
    }

    private fun startFlight(screen: Screen.Flying) {
        endFlight()
        val scene = loader.load()
        val plane = scene.entity(screen.plane.entityId) ?: return
        val pilot = scene.pilot ?: return
        val assets = PhysicsAssets(AssetFiles(project.toFile(), JsonProcessor()))
        session = FlightSession(scene.engine, pilot, plane, assets, log, natives)
        sessionScene = scene
        flying = screen
        input.reset()
    }

    private fun endFlight() {
        session?.close()
        session = null
        sessionScene = null
        flying = null
    }

    override fun dispose() {
        endFlight()
        ui.dispose()
        skin.dispose()
        renderer.dispose()
        executor.shutdownNow()
    }

    /** The handle while flying; Escape pauses. Menus get their keys from [GameUi]. */
    private inner class Keys : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            if (flow.screen is Screen.Flying) {
                if (keycode == Input.Keys.ESCAPE) {
                    flow.pause()
                    return true
                }
                return input.key(Input.Keys.toString(keycode), true)
            }
            return ui.keyDown(keycode)
        }

        override fun keyUp(keycode: Int): Boolean =
            flow.screen is Screen.Flying && input.key(Input.Keys.toString(keycode), false)

        override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
            if (flow.screen !is Screen.Flying) return false
            input.mouseMoved(screenY.toFloat(), Gdx.graphics.height.toFloat())
            return true
        }
    }
}
