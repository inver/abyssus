/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import com.badlogic.gdx.*
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.GAME_COMPONENTS
import net.nevinsky.abyssus.app.game.controlline.flight.CONTROL_TENSION
import net.nevinsky.abyssus.app.game.controlline.flight.FlightSession
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Mode
import net.nevinsky.abyssus.app.game.controlline.flow.reportOf
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.input.HandleInput
import net.nevinsky.abyssus.app.game.controlline.render.*
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefModel
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefFrame
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefView
import net.nevinsky.abyssus.app.game.controlline.pattern.GuidePlanner
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternJudge
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
import net.nevinsky.abyssus.app.game.controlline.screens.GameUiHost
import net.nevinsky.abyssus.app.game.controlline.screens.createGameUi
import net.nevinsky.abyssus.lib.core.BaseCtx
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.physics.PHYSICS_COMPONENTS
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import net.nevinsky.abyssus.lib.core.scene.RuntimeSceneLoader
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** The project file of the bundled native project. */
private const val PROJECT_FILE = "ControlLine.abss"

private val TAUT_LINE = Color(0.95f, 0.95f, 0.95f, 1f)
private val SLACK_LINE = Color(0.5f, 0.5f, 0.5f, 1f)

/**
 * The game (design decision 8): the field of [project] drawn by [FieldRenderer], the screens of [GameFlow] by
 * [GameUiHost] (the project's `screens` UI asset), and a [FlightSession] while flying. Everything runs on the LWJGL3 main thread (design decision 11); only
 * asset preparation runs on [executor]. Scores are kept in [scoresFile].
 */
class ControlLineGame(
    private val project: Path, private val scoresFile: Path,
    private val patternScoresFile: Path = scoresFile.resolveSibling("f2b-scores.json"),
    private val fieldView: FieldView? = null,
) : ApplicationAdapter() {
    /** Problems go to stderr through `physics`'s slf4j-simple binding. */
    private val log = LoggerFactory.getLogger("control-line")
    private val executor: ExecutorService =
        Executors.newFixedThreadPool(2) { r -> Thread(r, "asset-prepare").apply { isDaemon = true } }
    private val natives = JoltNatives()
    private val input = HandleInput()

    private lateinit var flow: GameFlow
    private val raster = lazy { FieldRenderer(ctx) }

    /** The opt-in view (ray mode) while it works; null once it failed or when there was none: the raster field draws. */
    private var optIn: FieldView? = fieldView
    private val renderer: FieldView get() = optIn ?: raster.value
    private lateinit var ui: GameUiHost
    private lateinit var cameras: Cameras
    private lateinit var parked: FieldScene

    private var session: FlightSession? = null
    private var judge: PatternJudge? = null

    /** The Noob guide of the flight, or null at the other levels. */
    private var guide: GuidePlanner? = null
    private var sessionScene: FieldScene? = null
    private var flying: Screen.Flying? = null

    // the debrief: its own load of the field, and what to draw for the selected manoeuvre
    private var debriefScene: FieldScene? = null
    private var debriefOf: DebriefModel? = null
    private var debriefRevision = -1
    private var debriefLines = emptyList<LineSegment>()
    private var debriefFrame: DebriefFrame? = null

    private val ctx = BaseCtx(project.toString(), "/shader", ControlLineGame::class.java, log)
        .also { it.componentRegistry.registerAll(PHYSICS_COMPONENTS + GAME_COMPONENTS) }

    private val sceneLoader = RuntimeSceneLoader(ctx.sceneLoader, ctx.ecsLoader, log)

    override fun create() {
        // TerrainMesh sets every layer's uniforms; the game's shader may not use them all
        ShaderProgram.pedantic = false
        // the window is a 3.2 core context (foliage is drawn instanced): the shaders keep their legacy GLSL, which gets
        // its version line and the defines that make `attribute`, `varying`, `texture2D` and `gl_FragColor` valid
        ShaderProgram.prependVertexCode = "#version 150\n#define attribute in\n#define varying out\n"
        ShaderProgram.prependFragmentCode = "#version 150\n#define varying in\n#define texture2D texture\n" +
            "#define textureCube texture\n#define gl_FragColor fragColor\nout vec4 fragColor;\n"
        parked = loadScene()
        flow = GameFlow(parked.planes, ScoreTable(scoresFile), PatternScoreTable(patternScoresFile))
        ui = createGameUi(project, PROJECT_FILE, flow, log) { Gdx.app.exit() }
        cameras = Cameras(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        Gdx.input.inputProcessor = InputMultiplexer(Keys(), ui.inputProcessor)
        ui.resize()
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        cameras.resize(width.toFloat(), height.toFloat())
        renderer.resize(width.toFloat(), height.toFloat())
        ui.resize()
    }

    override fun render() {
        val seconds = Gdx.graphics.deltaTime.coerceIn(0f, 0.1f)
        followFlow()
        val screen = flow.screen
        val s = session
        if (s != null && screen is Screen.Flying) {
            s.advance(seconds, input)
            judge?.let { guide?.update(it) }
            flying?.let { f -> reportOf(s, f.mode, judge, f.plane.name)?.let(flow::flightEnded) }
        } else if (s != null && screen !is Screen.Debrief && (screen is Screen.GameOver || screen is Screen.NameEntry)) {
            // the plane comes to rest; the flight itself is over
            s.advance(seconds, HandleInput())
        }

        val scene = (screen as? Screen.Debrief)?.let { debriefSceneOf(it.model) } ?: sessionScene ?: parked
        var lines = emptyList<LineSegment>()
        var hidden = if (s != null && screen !is Screen.PlaneSelect) s.rig.pilot else null
        when (screen) {
            is Screen.Debrief -> {
                lines = drawDebrief(scene, screen.model, seconds)
                hidden = scene.pilot.takeIf { screen.model.view == DebriefView.PILOT }
            }

            is Screen.PlaneSelect -> parked.entity(flow.choices(screen.mode)[screen.index].entityId)?.let { plane ->
                val p = parked.position(plane)
                cameras.planeSelect(
                    p.localPosition,
                    p.localRotation.transform(Vector3(0f, 0f, 1f)).also { it.y = 0f }.nor(),
                    seconds
                )
            }

            else -> if (s != null) {
                val pilot = scene.position(s.rig.pilot).localPosition
                cameras.flying(pilot, scene.position(s.plane).localPosition)
                val color = if (s.flight.tension >= CONTROL_TENSION) TAUT_LINE else SLACK_LINE
                lines = s.lines().map { (a, b) -> LineSegment(a, b, color) }
                if (screen is Screen.Flying || screen is Screen.Paused) guide?.let { lines = lines + it.segments().toLines(pilot) }
            } else {
                cameras.menu(
                    scene.pilot?.getComponent(PositionComponent::class.java)?.localPosition ?: Vector3(),
                    seconds
                )
            }
        }
        renderer.draw(
            scene,
            cameras.camera,
            lines,
            hidden = hidden,
            clips = if (screen is Screen.Debrief) emptyMap() else s?.flight?.crash?.let { mapOf(s.plane to it.clip) }.orEmpty(),
            seconds = seconds,
        )
        (optIn as? FailableFieldView)?.failure?.let(::fallBackToRaster)
        ui.update(s, judge, seconds)
        ui.draw(seconds)
    }

    /** Ray rendering failed after start: dispose it and continue raster, with the reason in the title and the log. */
    private fun fallBackToRaster(reason: String) {
        log.warn("Ray-traced field view stopped: {}; continuing with the raster field", reason)
        Gdx.graphics.setTitle("Control Line (raster: $reason)")
        optIn?.dispose()
        optIn = null
    }

    /** The debrief's own load of the field for [model], made when it opens and dropped when the screen is left. */
    private fun debriefSceneOf(model: DebriefModel): FieldScene {
        if (debriefOf !== model) {
            debriefOf = model
            debriefScene = loadScene()
            debriefRevision = -1
        }
        return checkNotNull(debriefScene)
    }

    /**
     * Poses the recorded plane in the debrief's [scene], points the camera at the figure and returns the tracks to draw.
     * The tracks are rebuilt only when another manoeuvre is selected.
     */
    private fun drawDebrief(scene: FieldScene, model: DebriefModel, seconds: Float): List<LineSegment> {
        model.advance(seconds)
        val ground = scene.pilot?.let { Vector3(scene.position(it).localPosition) } ?: Vector3()
        if (debriefRevision != model.revision) {
            debriefRevision = model.revision
            debriefFrame = model.frame()
            debriefLines = model.segments().flatMap { track ->
                track.points.zipWithNext { a, b ->
                    LineSegment(Vector3(ground).add(a), Vector3(ground).add(b), track.kind.color(), track.kind.lineWidth())
                }
            }
        }
        debriefFrame?.let { cameras.debrief(model.view, ground, it) }
        val pose = model.planePose() ?: return debriefLines
        val name = model.recording?.plane
        val plane = scene.planes.firstOrNull { it.name == name }?.let { scene.entity(it.entityId) }
        val at = Vector3(ground).add(pose.position)
        if (plane != null) {
            val body = scene.position(plane)
            body.localPosition.set(at)
            body.localRotation.set(pose.rotation)
            return debriefLines
        }
        // no plane of that name in the field: a small cross marks where it was
        return debriefLines + listOf(Vector3.X, Vector3.Y, Vector3.Z).map { axis ->
            LineSegment(Vector3(at).mulAdd(axis, -0.6f), Vector3(at).mulAdd(axis, 0.6f), FLOWN_COLOR, 3f)
        }
    }

    /** Starts a flight when the flow flies a plane (again), and ends it when the flow leaves the flight's screens. */
    private fun followFlow() {
        when (val screen = flow.screen) {
            is Screen.Flying -> if (screen != flying) startFlight(screen)
            is Screen.Paused, is Screen.NameEntry, is Screen.GameOver, is Screen.Debrief -> Unit
            is Screen.Scores -> if (screen.from !is Screen.GameOver) endFlight()
            else -> endFlight()
        }
        // the debrief's field goes with the debrief
        if (flow.screen !is Screen.Debrief && debriefScene != null) {
            debriefScene = null
            debriefOf = null
        }
    }

    private fun startFlight(screen: Screen.Flying) {
        endFlight()
        val scene = loadScene()
        val plane = scene.entity(screen.plane.entityId) ?: return
        val pilot = scene.pilot ?: return
        val assets = PhysicsAssets(project.toFile())
        // an F2B pattern is judged over a hemisphere of the plane's line length
        val pattern = screen.mode as? Mode.Pattern
        judge = pattern?.let { PatternJudge(screen.plane.lineLength) }
        guide = pattern?.takeIf { it.level.guided }?.let { GuidePlanner(screen.plane.lineLength) }
        session = FlightSession(scene.engine, pilot, plane, assets, log, natives, judge)
        sessionScene = scene
        flying = screen
        input.reset()
    }

    private fun loadScene(): FieldScene {
        val loaded = sceneLoader.load(project.resolve(FIELD_SCENE).fileName.toString())
            ?: throw IllegalStateException("The field scene ${project.resolve(FIELD_SCENE)} could not be read; see the log")
        return FieldScene(loaded).also { scene -> scene.foliageWarnings.forEach(log::warn) }
    }

    private fun endFlight() {
        session?.close()
        session = null
        judge = null
        guide = null
        sessionScene = null
        flying = null
    }

    override fun dispose() {
        endFlight()
        ui.dispose()
        optIn?.dispose()
        if (optIn == null || raster.isInitialized()) raster.value.dispose()
        executor.shutdownNow()
    }

    /** The handle while flying; Escape pauses. Menus get their keys from [GameUiHost]. */
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
