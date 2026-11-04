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
import net.nevinsky.abyssus.lib.physics.jolt.ShapeCache
import net.nevinsky.abyssus.lib.core.scene.RuntimeSceneLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

/** The project file of the bundled native project. */
private const val PROJECT_FILE = "ControlLine.abss"

private val TAUT_LINE = Color(0.95f, 0.95f, 0.95f, 1f)
private val SLACK_LINE = Color(0.5f, 0.5f, 0.5f, 1f)

/**
 * The game (design decision 8): the field of [project] drawn by [FieldRenderer], the screens of [GameFlow] by
 * [GameUiHost] (the project's `screens` UI asset), and a [FlightSession] while flying, which [FlightRunner] starts, flies
 * and ends with the flow. Everything runs on the LWJGL3 main thread (design decision 11); only
 * asset preparation runs on the context's executor, which [dispose] stops with the context. Scores are kept in [scoresFile].
 */
class ControlLineGame(
    private val project: Path, private val scoresFile: Path,
    private val patternScoresFile: Path = scoresFile.resolveSibling("f2b-scores.json"),
    private val fieldView: FieldView? = null,
    private val scaleScoresFile: Path = scoresFile.resolveSibling("scale-scores.json"),
    private val controlsFile: Path = scoresFile.resolveSibling("controls.json"),
    private val displayFile: Path = scoresFile.resolveSibling("display.json"),
    private val controllerSource: ControllerSource = CombinedControllerSource(listOf(GdxControllers(), GlfwJoysticks())),
    /** Where a failure is recorded (spec `control-line-runtime-stability`); `main` passes the one it installed. */
    private val crashes: CrashReporter = CrashReporter(defaultLogsFolder()),
    /** Problems go to stderr through `physics`'s slf4j-simple binding. */
    private val log: Logger = LoggerFactory.getLogger("control-line"),
    /** Ends the application after a failure; the game's own [Gdx.app] by default. */
    exit: () -> Unit = { Gdx.app.exit() },
    /** Marks the frame's spans and the field's passes for the performance workload; the shipped game's does nothing. */
    private val probe: FrameProbe = FrameProbe.NONE,
    /** The frame's time (s) before clamping: the window's, or a fixed step in the performance workload. */
    private val frameSeconds: () -> Float = { Gdx.graphics.deltaTime },
    /** Called once in [create] with the flow, before the first frame: a test or the workload drives the screens from it. */
    private val onStart: (GameFlow) -> Unit = {},
    /** What the raster field is drawn with; the workload's `legacy` run passes [FieldRenderSettings.LEGACY]. */
    private val fieldSettings: FieldRenderSettings = FieldRenderSettings(),
) : ApplicationAdapter() {
    private val guard = FrameGuard(crashes, exit)
    private val natives = JoltNatives()

    // one model and terrain reader and one set of collider shapes for the game run (design decision D11): a flight
    // start built every plane's convex hull again, 1.05 s of it the Stunter's
    private val physicsAssets by lazy { PhysicsAssets(project.toFile()) }
    private val shapes = ShapeCache()

    private lateinit var flow: GameFlow
    private val raster = lazy { FieldRenderer(ctx, requested = fieldSettings, probe = probe, cloudFrameBudget = CLOUD_STEP_DOWN_INTERVAL) }

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

    override fun create() = guard.run {
        // the GL strings are kept as values: a crash record names them without touching GL
        crashes.environment = startEnvironment(crashes.environment, Gdx.gl, optIn, log)
        // TerrainMesh sets every layer's uniforms; the game's shader may not use them all
        ShaderProgram.pedantic = false
        // the window is a 3.2 core context (foliage is drawn instanced): the shaders keep their legacy GLSL, which gets
        // its version line and the defines that make `attribute`, `varying`, `texture2D` and `gl_FragColor` valid
        ShaderProgram.prependVertexCode = "#version 150\n#define attribute in\n#define varying out\n"
        ShaderProgram.prependFragmentCode = "#version 150\n#define varying in\n#define texture2D texture\n" +
            "#define textureCube texture\n#define gl_FragColor fragColor\nout vec4 fragColor;\n"
        parked = loadScene().also { it.standOnTables(physicsAssets::modelPoints) }
        flow = GameFlow(parked.planes, ScoreTable(scoresFile), PatternScoreTable(patternScoresFile), ScaleScoreTable(scaleScoresFile))
        flights = FlightRunner(flow, ::loadScene, { physicsAssets }, log, natives, shapes)
        controllers = ControllerService(controllerSource, ControllerProfiles(controlsFile), flights.input)
        display = GdxDisplay(DisplaySettings(displayFile))
        ui = createGameUi(project, PROJECT_FILE, flow, log, controllers, display) { Gdx.app.exit() }
        cameras = Cameras(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        // a GL test's context has no input
        Gdx.input?.inputProcessor = InputMultiplexer(Keys(), ui.inputProcessor)
        ui.resize()
        onStart(flow)
    }

    override fun resize(width: Int, height: Int) = guard.run {
        if (width <= 0 || height <= 0) return@run
        cameras.resize(width.toFloat(), height.toFloat())
        renderer.resize(width.toFloat(), height.toFloat())
        ui.resize()
    }

    /** The models the raster field drew in the last frame, or -1 before it drew one: a test checks no frame lost them. */
    internal val lastDrawnModels: Int get() = if (raster.isInitialized()) raster.value.lastDrawnModels else -1

    /** Whether an asset is still being prepared or built: the performance workload waits for the field to settle. */
    internal val assetsLoading: Boolean get() = ctx.assetStorage.isLoading()

    /** One frame, guarded: an exception leaves a crash record and ends the game (design decision D1). */
    override fun render() = guard.run { probe.span(FrameSpan.FRAME, ::frame) }

    private fun frame() {
        val seconds = frameSeconds().coerceIn(0f, 0.1f)
        display.applyPending()
        probe.begin(FrameSpan.FLIGHT)
        controllers.drive(flow, seconds, ui::navigate)
        followFlow()
        flights.advance(seconds)
        probe.end(FrameSpan.FLIGHT)
        val screen = flow.screen
        val s = flights.session
        val guide = flights.guide

        val scene = (screen as? Screen.Debrief)?.let { debriefSceneOf(it.model) } ?: flights.scene ?: parked
        var lines = emptyList<LineSegment>()
        var cables = emptyList<Cable>()
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
                // the camera follows the plane where it is drawn, not where it is simulated
                cameras.flying(pilot, s.drawnPosition)
                val color = if (s.flight.tension >= CONTROL_TENSION) TAUT_LINE else SLACK_LINE
                // the lines bend with their tension, weight and drag: lit cables where the renderer draws them, wire ribbons otherwise
                val diameter = s.rig.planeSettings.lineDiameter
                val wires = s.wires(seconds)
                if (renderer.drawsCables) {
                    cables = wires.map { wire -> Cable(wire.map { Vector3(it) }, diameter, color) }
                } else {
                    lines = wires.flatMap { wire ->
                        wire.zipWithNext { a, b -> LineSegment(Vector3(a), Vector3(b), color, thickness = diameter) }
                    }
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
        probe.begin(FrameSpan.FIELD)
        renderer.draw(
            scene,
            cameras.camera,
            lines,
            hidden = hidden,
            clips = if (screen is Screen.Debrief) emptyMap() else s?.flight?.crash?.let { mapOf(s.plane to it.clip) }.orEmpty(),
            seconds = seconds,
            cables = cables,
            poses = if (s == null || screen is Screen.Debrief) emptyMap() else mapOf(s.plane to s.drawnTransform()),
        )
        probe.end(FrameSpan.FIELD)
        (optIn as? FailableFieldView)?.failure?.let(::fallBackToRaster)
        probe.begin(FrameSpan.UI)
        ui.update(s, flights.judge, seconds, flights.scaleJudge, flights.instructor)
        ui.draw(seconds)
        probe.end(FrameSpan.UI)
    }

    /** Ray rendering failed after start: dispose it and continue raster, with the reason in the title and the log. */
    private fun fallBackToRaster(reason: String) {
        log.warn("Ray-traced field view stopped: {}; continuing with the raster field", reason)
        Gdx.graphics.setTitle("Control Line (raster: $reason)")
        optIn?.dispose()
        optIn = null
    }

    /**
     * The field the debrief of [model] is drawn in: the last flight's scene while the flight runner still holds it (a
     * debrief from GAME OVER; the recording overwrites the plane's pose, and the flight's physics puts it back when the
     * debrief is left), else its own load (a debrief from the scores). Taken when it opens, dropped when it is left.
     */
    private fun debriefSceneOf(model: DebriefModel): FieldScene {
        if (debriefOf !== model) {
            debriefOf = model
            debriefScene = flights.scene ?: loadScene()
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

    override fun dispose() = guard.release(log) {
        if (::flights.isInitialized) flights.close()
        // after the last world: the bodies that used the shapes are gone
        shapes.close()
        if (::ui.isInitialized) ui.dispose()
        optIn?.dispose()
        // only a raster renderer that was made: the ray view may have failed before the raster field drew a frame
        if (raster.isInitialized()) raster.value.dispose()
        // last: the context owns the asset storage the renderers borrowed, and the asset-prepare threads
        ctx.close()
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
