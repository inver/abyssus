/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.plugin.settings.AntialiasingLevel

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.thisLogger
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.pick.*
import net.nevinsky.abyssus.lib.core.editor.ray.RayModeSnapshot
import net.nevinsky.abyssus.lib.core.editor.scene.AssetRevisionBatch
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.sceneview.skybox.CloudViewState
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import org.lwjgl.opengl.GL
import java.awt.BorderLayout
import java.awt.event.*
import javax.swing.*

/**
 * Swing panel hosting a core-profile GL canvas that renders a scene with libGDX. Orbit/pan/zoom with the mouse; click
 * selects an object, whose gizmo (Move: W, Rotate: E) can be dragged; a selector looks through a camera entity.
 */
class SceneViewPanel internal constructor(
    initial: SceneRenderParams,
    private val renderer: SceneRenderer,
    /** The Add Light choices, placed at the orbit target; null: the Add Light button stays disabled. */
    private val lightMenu: PlacementMenu<() -> Vector3>? = null,
    /** The project's models and terrains to place at the orbit target; null: no Add Asset button. */
    private val assetMenu: PlacementMenu<() -> Vector3>? = null,
    /** The Lake and Sea presets to place at the orbit target; null: no Add Water button. */
    private val waterMenu: PlacementMenu<() -> Vector3>? = null,
    ray: RayIntegration? = null,
    /** Play in this view; without a simulation provider there are no play controls. */
    private val play: PlayState = PlayState(null),
    /** What Play starts from; read when Play is pressed. */
    private val simulationRequest: ((selection: String?) -> SimulationRequest)? = null,
    /** Other plugins' overlays for this view. */
    private val overlays: SceneOverlayHost? = null,
    private val foliageActions: ((String) -> DefaultActionGroup?)? = null,
    private val paintSession: ((String, () -> FoliagePaintMode?, (net.nevinsky.abyssus.plugin.foliage.FoliagePaintSession?) -> Unit) -> Unit)? = null,
    /** The project's models to arrange along a path; the callback starts drawing. Null: no Add Path Arrangement button. */
    private val pathMenu: PlacementMenu<(RenderAsset) -> Unit>? = null,
    /** The project's `Show FPS`, read before every frame so a missed change event cannot leave the counter off; null: only [setFpsEnabled]. */
    private val showFps: (() -> Boolean)? = null,
    antialiasingLevel: AntialiasingLevel = AntialiasingLevel.DEFAULT,
) : JPanel(BorderLayout()), SceneView, RayControlProvider {

    internal val orbit = OrbitCamera(initial.camera)
    private val interaction = SceneInteraction(renderer.state, renderer.queries, orbit)

    private var lastCamera = initial.camera

    override val view: JComponent get() = this

    /** Gizmo space, snapping, shading and layer toggles of this view; never saved. */
    internal val tools = SceneViewToolsModel()
    /** The tool, space and snap, such as `Move · Local · Snap 0.5`; the stats strip shows it. */
    internal val toolStatusText: String get() = toolbar.statusText
    /** This view's frame rate: sampled only while the project shows it, reset on every lifecycle break. */
    private val fps = FpsCounter()
    private var fpsEnabled = false

    /** Monotonic nanoseconds; tests replace it. */
    internal var nanoClock: () -> Long = System::nanoTime

    override fun setFpsEnabled(enabled: Boolean) {
        if (enabled == fpsEnabled) return
        fpsEnabled = enabled
        fps.reset()
    }

    /** The per-frame read of the project's `Show FPS`; [updateCornerSprites] runs it, tests call it directly. */
    internal fun refreshFpsSetting() {
        showFps?.let { setFpsEnabled(it()) }
    }

    /** A frame is about to be drawn. Only while enabled; the sample starts before the first frame. */
    internal fun fpsFrameStarted() {
        if (fpsEnabled) fps.frameStarted(nanoClock())
    }

    /** A frame was presented (after the buffer swap). */
    internal fun fpsFrameCompleted() {
        if (fpsEnabled) fps.frameCompleted(nanoClock())
    }

    /** The surface cannot be drawn on, or the view was hidden, failed or re-created: the next sample starts fresh. */
    internal fun fpsInterrupted() = fps.reset()

    private val orientation = OrientationWidget()
    private val statsStrip = StatsStrip()
    private var spriteScale = 0.0
    private var orientationSprite: CornerSprite? = null
    private var statsSprite: CornerSprite? = null
    private val paint: FoliagePaintControls = FoliagePaintControls(renderer, interaction, play, paintSession, ::syncControls)
    private val playStrip = PlayControlsStrip(play, renderer, simulationRequest, { paint.end() }, { requestFocusInWindow() })
    private val clouds = CloudControls(renderer) { experimenting }

    /** This view's Clouds choice and fallbacks; a reopened view gets a new panel, so it starts at Asset again. */
    internal val cloudState: CloudViewState get() = clouds.state
    private val rayBinding = RayControlBinding(renderer)

    /** The only switch for Ray Tracing: the Abyssus Properties panel flips it. The Scene View toolbar has no ray control. */
    override val rayControl: RayControl? get() = rayBinding.control
    private val experimentButton = if (java.lang.Boolean.getBoolean("abyssus.raytracing.experiment"))
        JToggleButton(AbyssusBundle.message("sceneViewRayExperiment")) else null
    private val experiment = experimentButton?.let { RayFeasibilityPreview { message -> thisLogger().info(message) } }
    private val experimenting: Boolean get() = experimentButton?.isSelected == true
    private val input = SceneInputForwarder(this, renderer, interaction, play, { experimenting }, { experiment?.orbit }, paint::update, ::frameSelection, ::pressOnOrientationWidget)

    private val toolbar: SceneViewToolbar = SceneViewToolbar(
        this, renderer, interaction, tools, play, paint, playStrip, clouds, experimentButton,
        { experiment?.stop() },
        SceneViewToolbarSources(
            lightMenu, assetMenu, waterMenu, pathMenu, foliageActions, overlays,
            ::lightChoices, ::assetChoices, ::waterChoices, ::pathChoices, { onRegenerate }, { unavailable },
        ),
        ::frameSelection,
    )

    init {
        renderer.params = initial
        renderer.overlays = overlays
        // Ray tracing is optional: without an integration the view stays raster only.
        ray?.let(::installRay)
    }

    /** Binds this view's Ray Tracing mode to [integration], replacing any earlier binding (see [RayControlBinding.install]). */
    internal fun installRay(integration: RayIntegration) = rayBinding.install(integration)

    /** The current Ray Tracing mode of this view (Off until it is switched on from Abyssus Properties). */
    internal val rayMode: RayModeSnapshot? get() = rayBinding.mode

    private fun refreshRay() = rayBinding.refresh()

    /** Called (on the AWT thread) when rendering fails, e.g. when no GL 3.2 core context can be created. */
    override var onFailure: ((Throwable) -> Unit)? = null

    private val antialiasing: CanvasAntialiasing = CanvasAntialiasing(
        antialiasingLevel,
        ::replaceCanvas,
        { samples, error -> thisLogger().warn("Scene view refused $samples MSAA samples; trying lower sample counts", error) },
        { error ->
            thisLogger().warn("Scene render failed, stopping the view", error)
            driver.stop()
            onFailure?.invoke(error)
        },
    )

    /** Sample count requested for the current canvas, after any pixel-format fallback. */
    internal val effectiveSampleCount: Int get() = antialiasing.samples

    override fun setAntialiasing(level: AntialiasingLevel) {
        com.intellij.openapi.application.ApplicationManager.getApplication().assertIsDispatchThread()
        if (unavailable) return
        antialiasing.setLevel(level, isShowing)
    }

    private val lifecycle: SceneGlLifecycle = SceneGlLifecycle(renderer, object : SceneGlHooks {
        override fun beforeDispose() {
            rayBinding.feed?.reset()
        }

        override fun disposeInContext() {
            experiment?.dispose()
        }

        override fun abandoned() {
            fpsInterrupted()
            rayBinding.feed?.reset()
            experiment?.abandon()
        }

        override fun contextLost() = antialiasing.abandoned()

        override fun paintFrame(canvas: GuardedGLCanvas, ctx: GdxContext, frame: GdxFrame) {
            updateCornerSprites(canvas.width, canvas.framebufferWidth)
            fpsFrameStarted()
            frame.tick(canvas.framebufferWidth, canvas.framebufferHeight)
            renderer.state.poses = play.poses() ?: emptyMap()
            renderer.state.gizmosEnabled = !play.active
            GdxRuntime.withContext(ctx) {
                if (experimenting) experiment?.draw(frame.width, frame.height)
                else renderer.render(frame.width, frame.height, orbit, frame.deltaSeconds)
            }
            if (!experimenting) {
                interaction.frameRendered()
                cloudFrameRendered(frame.deltaSeconds)
            }
            refreshRay()
            experimentButton?.toolTipText = experiment?.failure?.let {
                AbyssusBundle.message("sceneViewRayExperimentFailure", it.documentDisplayMessage())
            } ?: AbyssusBundle.message("sceneViewRayExperimentTooltip")
        }

        override fun framePresented() = fpsFrameCompleted()
    })

    private var canvas: GuardedGLCanvas = lifecycle.newCanvas(antialiasing.samples, antialiasing.swapControl)
    private fun newCanvasHost() = CanvasOverlayHost(canvas) {
        try {
            canvas.captureFrame { lifecycle.capabilities?.let { GL.setCapabilities(it) } }
        } catch (e: Exception) {
            thisLogger().warn("Failed to capture the scene beneath an IDE overlay", e)
            null
        }
    }
    private var canvasHost = newCanvasHost()

    /** Renders at the full frame rate while something moves or may change at once; an idle view (the sky still drifts) renders slowly. */
    private val driver: SceneFrameDriver = SceneFrameDriver(
        this, { canvas },
        { play.active || experimenting || renderer.loading || renderer.presentedRayFrame },
        ::fpsInterrupted,
        { error -> antialiasing.renderFailed(error, lifecycle.contextInitialized) },
    )

    /** True once rendering failed for good: the viewport shows a message and the toolbar is disabled. */
    private var unavailable = false

    override fun showUnavailable(message: String): Boolean {
        driver.stop()
        fpsInterrupted()
        unavailable = true
        remove(canvasHost)
        canvasHost.stop()
        add(com.intellij.ui.components.JBLabel(message, SwingConstants.CENTER).apply { name = "scene-unavailable" }, BorderLayout.CENTER)
        syncControls()
        revalidate()
        repaint()
        return true
    }

    init {
        add(toolbar.component, BorderLayout.NORTH)
        add(canvasHost, BorderLayout.CENTER)
        input.attach(canvas)
        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && isShowing && !unavailable) {
                antialiasing.shown()
            }
        }
        input.bindKeys()
        input.forwardKeys()
        interaction.onStateChanged = ::syncControls
        tools.onChanged = ::syncControls
        play.onChanged = ::syncControls
        syncControls()
        toolbar.refreshCameraChoices(initial)
    }

    /**
     * Repaints the orientation widget and the stats strip into the sprites the renderer draws in the corners, when what they
     * show or the display scale changed. The widget shows the camera the frame is drawn from, so it also follows a scene camera.
     */
    private fun updateCornerSprites(logicalWidth: Int, framebufferWidth: Int) {
        refreshFpsSetting()
        val scale = (framebufferWidth.toDouble() / logicalWidth.coerceAtLeast(1)).coerceAtLeast(1.0)
        val d = renderer.frameCamera.direction
        val widgetChanged = orientation.show(
            kotlin.math.atan2(-d.x, -d.z), kotlin.math.asin((-d.y).coerceIn(-1f, 1f)),
        )
        val statsChanged = statsStrip.show(statsText())
        val rescaled = scale != spriteScale
        if (!widgetChanged && !statsChanged && !rescaled && orientationSprite != null) return
        spriteScale = scale
        val margin = Math.round(CORNER_MARGIN * scale).toInt()
        if (widgetChanged || rescaled || orientationSprite == null) {
            orientationSprite = CornerSprite(orientation.corner, orientation.toImage(scale), orientation.version, margin)
        }
        if (statsChanged || rescaled || statsSprite == null) {
            statsSprite = CornerSprite(statsStrip.corner, statsStrip.toImage(scale), statsStrip.version, margin)
        }
        renderer.cornerSprites = listOfNotNull(orientationSprite, statsSprite)
    }

    internal fun statsText(): String = statsLine(
        fps = if (fpsEnabled) fps.display?.let { AbyssusBundle.message("sceneViewFps", it) } ?: AbyssusBundle.message("sceneViewFps", "--") else null,
        objectsText = AbyssusBundle.message("sceneViewObjects", renderer.drawnModels.size + renderer.drawnTerrains.size),
        status = toolStatusText,
    )

    /**
     * A left press at ([x], [y]) in the canvas: true when it landed on the orientation widget's disc and so is not for the
     * scene. On one of its axes it turns the orbit camera to look along that axis, from the axis's side.
     */
    internal fun pressOnOrientationWidget(x: Int, y: Int): Boolean {
        val rect = cornerRect(Corner.UPPER_RIGHT, canvas.width, canvas.height, OrientationWidget.SIZE, OrientationWidget.SIZE, CORNER_MARGIN)
        if (!rect.contains(x, y)) return false
        val radius = OrientationWidget.SIZE / 2f
        val dx = x - rect.x - radius
        val dy = y - rect.y - radius
        if (dx * dx + dy * dy > radius * radius) return false
        if (play.active || experimenting || interaction.viewCamera != null) return true
        orientation.axisAt(x - rect.x, y - rect.y)?.let { axis ->
            val direction = axis.direction
            orbit.lookAlong(Vector3(-direction.x, -direction.y, -direction.z))
        }
        return true
    }

    /**
     * Frame selection: the orbit camera looks at the selected object, or at everything drawn when nothing is selected,
     * keeping its view direction. Nothing while looking through a scene camera, playing or in the ray experiment.
     */
    internal fun frameSelection() {
        if (play.active || experimenting || interaction.viewCamera != null) return
        val box = renderer.framedBounds(interaction.selectedId) ?: return
        val camera = renderer.frameCamera
        val aspect = (camera.viewportWidth / camera.viewportHeight).takeIf { it.isFinite() && it > 0f } ?: 1f
        orbit.frame(
            FrameSelection().frame(
                Vector3(box.min.x, box.min.y, box.min.z), Vector3(box.max.x, box.max.y, box.max.z), camera.fieldOfView, aspect,
            ),
        )
    }

    /** Choices retain a supplier so placement follows the current orbit target at the moment of creation. */
    internal fun lightChoices(): DefaultActionGroup? = lightMenu?.choicesAt { orbit.target }

    /** The Add Asset choices, placing at the orbit target when a choice is made; null while assets cannot be added. */
    internal fun assetChoices(): DefaultActionGroup? = assetMenu?.choicesAt { orbit.target }

    /** The Add Water presets, placing at the orbit target when a choice is made; null while water cannot be added. */
    internal fun waterChoices(): DefaultActionGroup? = waterMenu?.choicesAt { orbit.target }

    /** The Add Path Arrangement choices (the project's models); null while arrangements cannot be added. */
    internal fun pathChoices(): DefaultActionGroup? = pathMenu?.choicesAt { asset -> startDrawing(asset) }

    private fun syncControls() = toolbar.sync()

    /** After each rendered frame; see [CloudControls.frameRendered]. */
    internal fun cloudFrameRendered(
        interval: Float,
        volumetric: Boolean = renderer.drawnCloudTechnique == CloudTechnique.VOLUMETRIC,
    ) = clouds.frameRendered(interval, volumetric)

    /** Reuses the guarded disposal path; only the native canvas changes, never the view's CPU state. */
    private fun replaceCanvas(samples: Int) {
        fpsInterrupted()
        canvasHost.stop()
        canvas.disposeCanvas()
        remove(canvasHost)
        lifecycle.contextInitialized = false
        canvas = lifecycle.newCanvas(samples, antialiasing.swapControl)
        input.attach(canvas)
        canvasHost = newCanvasHost()
        add(canvasHost, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    override var onPick: ((String) -> Unit)?
        get() = interaction.onPick
        set(value) {
            interaction.onPick = value
        }

    override var onPickWater: ((String) -> Unit)?
        get() = interaction.onPickWater
        set(value) {
            interaction.onPickWater = value
        }

    override var onWaterMove: ((String, Vector3) -> Boolean)?
        get() = interaction.onWaterMove
        set(value) {
            interaction.onWaterMove = value
        }

    override var onTransform: ((String, TransformEdit) -> Boolean)?
        get() = interaction.onTransform
        set(value) {
            interaction.onTransform = value
        }

    override var onArrangementCreate: ((RenderAsset, List<Vector3>, Boolean) -> Boolean)?
        get() = interaction.onArrangementCreate
        set(value) {
            interaction.onArrangementCreate = value
        }

    override var onArrangementEdit: ((String, List<Vector3>) -> Boolean)?
        get() = interaction.onArrangementEdit
        set(value) {
            interaction.onArrangementEdit = value
        }

    override var onRegenerate: ((String) -> Unit)? = null
        set(value) {
            field = value
            syncControls()
        }

    override fun setPathEditing(entityId: String?) {
        if (interaction.pathEditing == entityId) return
        paint.end()
        interaction.setPathEditing(entityId)
        if (entityId != null) requestFocusInWindow()
        syncControls()
    }

    override var onPathEditEnded: (() -> Unit)?
        get() = interaction.onPathEditEnded
        set(value) {
            interaction.onPathEditEnded = value
        }

    override fun startDrawing(asset: RenderAsset) {
        paint.end()
        interaction.startDrawing(asset)
        requestFocusInWindow()
    }

    override fun groundHeights(): GroundHeight? = renderer.groundHeights()

    override fun selectWater(waterId: String) {
        paint.end()
        interaction.selectWater(waterId)
        interaction.selectionChanged()
        syncControls()
    }

    override fun selectEntity(entityId: String) {
        if (renderer.state.selectedId != entityId) paint.end()
        renderer.state.selectedId = entityId
        interaction.selectionChanged()
        syncControls()
    }

    override fun setParams(params: SceneRenderParams) {
        val selected = interaction.selectedId
        fun binding(content: net.nevinsky.abyssus.lib.core.editor.scene.SceneContent) =
            content.foliages.firstOrNull { it.entityId == selected }?.let { it.foliageName to it.terrainName }
        val bindingChanged = binding(renderer.params.content) != binding(params.content)
        renderer.params = params
        if (params.camera != lastCamera) {
            lastCamera = params.camera
            orbit.reset(params.camera)
        }
        interaction.paramsChanged(params)
        if (bindingChanged) paint.refreshSession(force = true)
        toolbar.refreshCameraChoices(params)
    }

    override fun refreshAssets(revision: AssetRevisionBatch) {
        renderer.queueAssetRevision(revision)
        paint.refreshSession(force = true)
    }

    override fun stopPlay() = play.documentChanging()

    override fun placementPoint(): Vector3 = orbit.target

    override val playing: Boolean get() = play.active

    /** This view's play state; for tests. */
    internal val playState: PlayState get() = play

    override fun addNotify() {
        antialiasing.shown()
        super.addNotify()
        rayBinding.feed?.runtime?.setVisible(true)
        driver.start()
    }

    override fun removeNotify() {
        driver.release()
        fpsInterrupted()
        experiment?.stop()
        rayBinding.feed?.runtime?.setVisible(false)
        super.removeNotify()
    }

    override fun dispose() {
        paint.close()
        driver.stop()
        fpsInterrupted()
        canvasHost.stop()
        play.stop()
        overlays?.dispose()
        experiment?.stop()
        rayBinding.close()
        canvas.disposeCanvas() // releases GL resources through disposeGL while the context is still current
    }
}

/** A stable name for an AWT key code, as the `VK_` constant without its prefix (`W`, `SPACE`, `LEFT`). */
private val KEY_NAMES: Map<Int, String> by lazy {
    KeyEvent::class.java.fields.filter { it.name.startsWith("VK_") && it.type == Int::class.javaPrimitiveType }
        .associate { it.getInt(null) to it.name.removePrefix("VK_") }
}

internal fun keyName(code: Int): String = KEY_NAMES[code] ?: "KEY_$code"
