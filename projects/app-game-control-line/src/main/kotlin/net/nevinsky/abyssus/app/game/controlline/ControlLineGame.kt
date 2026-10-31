/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import com.badlogic.gdx.*
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.HdpiUtils
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.GAME_COMPONENTS
import net.nevinsky.abyssus.app.game.controlline.flight.CONTROL_TENSION
import net.nevinsky.abyssus.app.game.controlline.flight.FlightSession
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.controller.ControllerProfiles
import net.nevinsky.abyssus.app.game.controlline.controller.ControllerService
import net.nevinsky.abyssus.app.game.controlline.controller.ControllerSource
import net.nevinsky.abyssus.app.game.controlline.controller.drive
import net.nevinsky.abyssus.app.game.controlline.display.DisplaySettings
import net.nevinsky.abyssus.app.game.controlline.display.GdxDisplay
import net.nevinsky.abyssus.app.game.controlline.controller.gdx.GdxControllers
import net.nevinsky.abyssus.app.game.controlline.controller.glfw.GlfwJoysticks
import net.nevinsky.abyssus.app.game.controlline.controller.CombinedControllerSource
import net.nevinsky.abyssus.app.game.controlline.flow.Control
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.render.*
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefModel
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefFrame
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefView
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.scale.ScaleScoreTable
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
 * [GameUiHost] (the project's `screens` UI asset), and a [FlightSession] while flying, which [FlightRunner] starts, flies
 * and ends with the flow. Everything runs on the LWJGL3 main thread (design decision 11); only
 * asset preparation runs on [executor]. Scores are kept in [scoresFile].
 */
class ControlLineGame(
    private val project: Path, private val scoresFile: Path,
    private val patternScoresFile: Path = scoresFile.resolveSibling("f2b-scores.json"),
    private val fieldView: FieldView? = null,
    private val scaleScoresFile: Path = scoresFile.resolveSibling("scale-scores.json"),
    private val controlsFile: Path = scoresFile.resolveSibling("controls.json"),
    private val displayFile: Path = scoresFile.resolveSibling("display.json"),
    private val controllerSource: ControllerSource = CombinedControllerSource(listOf(GdxControllers(), GlfwJoysticks())),
) : ApplicationAdapter() {
    /** Problems go to stderr through `physics`'s slf4j-simple binding. */
    private val log = LoggerFactory.getLogger("control-line")
    private val executor: ExecutorService =
        Executors.newFixedThreadPool(2) { r -> Thread(r, "asset-prepare").apply { isDaemon = true } }
    private val natives = JoltNatives()

    private lateinit var flow: GameFlow
    private val raster = lazy { FieldRenderer(ctx) }

    /** The opt-in view (ray mode) while it works; null once it failed or when there was none: the raster field draws. */
    private var optIn: FieldView? = fieldView
    private val renderer: FieldView get() = optIn ?: raster.value
    private lateinit var ui: GameUiHost
    private lateinit var cameras: Cameras
    private lateinit var parked: FieldScene

    private lateinit var flights: FlightRunner
    private lateinit var controllers: ControllerService
    private lateinit var display: GdxDisplay

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
        parked = loadScene().also { it.standOnTables(PhysicsAssets(project.toFile())::modelPoints) }
        flow = GameFlow(parked.planes, ScoreTable(scoresFile), PatternScoreTable(patternScoresFile), ScaleScoreTable(scaleScoresFile))
        flights = FlightRunner(flow, ::loadScene, { PhysicsAssets(project.toFile()) }, log, natives)
        controllers = ControllerService(controllerSource, ControllerProfiles(controlsFile), flights.input)
        display = GdxDisplay(DisplaySettings(displayFile))
        ui = createGameUi(project, PROJECT_FILE, flow, log, controllers, display) { Gdx.app.exit() }
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
        display.applyPending()
        controllers.drive(flow, seconds, ui::navigate)
        followFlow()
        flights.advance(seconds)
        val screen = flow.screen
        val s = flights.session
        val guide = flights.guide

        val scene = (screen as? Screen.Debrief)?.let { debriefSceneOf(it.model) } ?: flights.scene ?: parked
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
                val thickness = s.rig.planeSettings.lineDiameter
                lines = s.wires(seconds).flatMap { wire ->
                    wire.zipWithNext { a, b -> LineSegment(Vector3(a), Vector3(b), color, thickness = thickness) }
                }
                if (screen is Screen.Flying || screen is Screen.Paused) guide?.let { lines = lines + it.segments().toLines(pilot) }
            } else {
                cameras.menu(
                    scene.pilot?.getComponent(PositionComponent::class.java)?.localPosition ?: Vector3(),
                    seconds
                )
            }
        }
        // the UI of the last frame left the GL viewport on its letterboxed rectangle; the field uses the whole window
        HdpiUtils.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        renderer.draw(
            scene,
            cameras.camera,
            lines,
            hidden = hidden,
            clips = if (screen is Screen.Debrief) emptyMap() else s?.flight?.crash?.let { mapOf(s.plane to it.clip) }.orEmpty(),
            seconds = seconds,
        )
        (optIn as? FailableFieldView)?.failure?.let(::fallBackToRaster)
        ui.update(s, flights.judge, seconds, flights.scaleJudge)
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

    /** Starts and ends flights with the flow; the debrief's field goes with the debrief. */
    private fun followFlow() {
        flights.follow()
        if (flow.screen !is Screen.Debrief && debriefScene != null) {
            debriefScene = null
            debriefOf = null
        }
    }

    private fun loadScene(): FieldScene {
        val loaded = sceneLoader.load(project.resolve(FIELD_SCENE).fileName.toString())
            ?: throw IllegalStateException("The field scene ${project.resolve(FIELD_SCENE)} could not be read; see the log")
        return FieldScene(loaded).also { scene -> scene.foliageWarnings.forEach(log::warn) }
    }

    override fun dispose() {
        flights.close()
        ui.dispose()
        optIn?.dispose()
        if (optIn == null || raster.isInitialized()) raster.value.dispose()
        executor.shutdownNow()
    }

    /** The handle while flying (the player's, not in a Watch AI demonstration); Escape pauses. Menus get their keys from [GameUiHost]. */
    private inner class Keys : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            if (flow.screen is Screen.Flying) {
                if (keycode == Input.Keys.ESCAPE) {
                    flow.pause()
                    return true
                }
                return flights.key(Input.Keys.toString(keycode), true)
            }
            return ui.keyDown(keycode)
        }

        override fun keyUp(keycode: Int): Boolean = flights.key(Input.Keys.toString(keycode), false)

        override fun mouseMoved(screenX: Int, screenY: Int): Boolean =
            flights.mouseMoved(screenY.toFloat(), Gdx.graphics.height.toFloat())
    }
}
