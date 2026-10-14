/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.backends.lwjgl3.GdxGlBridge
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.pick.*
import net.nevinsky.abyssus.lib.core.editor.ray.RayModeSnapshot
import net.nevinsky.abyssus.lib.core.editor.scene.AssetRevisionBatch
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.sceneview.skybox.CloudViewState
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.opengl.awt.GLData
import java.awt.BorderLayout
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.WrapLayout
import java.awt.FlowLayout
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
    private val lightMenu: PlacementMenu<() -> Vec3>? = null,
    /** The project's models and terrains to place at the orbit target; null: no Add Asset button. */
    private val assetMenu: PlacementMenu<() -> Vec3>? = null,
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
) : JPanel(BorderLayout()), SceneView, RayControlProvider {

    private val frame = GdxFrame()
    internal val orbit = OrbitCamera(initial.camera)
    private val interaction = SceneInteraction(renderer.state, renderer.queries, orbit)
    private var gdx: GdxContext? = null

    private var lastCamera = initial.camera
    private var capabilities: GLCapabilities? = null

    override val view: JComponent get() = this

    /** Gizmo space, snapping, shading and layer toggles of this view; never saved. */
    internal val tools = SceneViewToolsModel()
    /** The slim line under the viewport: tool, space and snap, such as `Move · Local · Snap 0.5`. */
    private val statusLine = JLabel().apply {
        name = "scene-status"
        accessibleContext.accessibleName = AbyssusBundle.message("sceneViewStatusName")
        border = JBUI.Borders.empty(2, 10)
        foreground = JBColor.GRAY
    }
    private val orientation = OrientationWidget()
    private val statsStrip = StatsStrip()
    private var spriteScale = 0.0
    private var orientationSprite: CornerSprite? = null
    private var statsSprite: CornerSprite? = null
    private var lastToolbar = toolbarState(GizmoMode.MOVE, experimenting = false, playing = false, canDrop = false, canAddLight = false, canAddAsset = false)
    private val toolsToolbar = SceneToolsToolbar(interaction, tools, { lastToolbar }, { frameSelection() }, this)
    private val dropButton = JButton(AbyssusBundle.message("sceneViewDrop"))
    private val addLightButton = JButton(AbyssusBundle.message("addLightTitle")).apply { name = "add-light" }
    private val addAssetButton = JButton(AbyssusBundle.message("addAssetTitle")).apply { name = "add-asset" }
    private val addPathButton = JButton(AbyssusBundle.message("addPathArrangementTitle")).apply { name = "add-path-arrangement" }
    private val regenerateButton = JButton(AbyssusBundle.message("regenerateArrangementTitle")).apply { name = "regenerate-arrangement"; isVisible = false }
    private val drawingHint = JLabel(AbyssusBundle.message("pathDrawingHint")).apply { name = "path-drawing-hint"; isVisible = false }
    private val addFoliageButton = JButton(AbyssusBundle.message("addFoliageTitle")).apply { name = "add-foliage" }
    private val cameraCombo = ComboBox<CameraChoice>()
    private val paint = FoliagePaintControls(renderer, interaction, play, paintSession, ::syncControls)
    private val playStrip = PlayControlsStrip(play, renderer, simulationRequest, { paint.end() }, { requestFocusInWindow() })
    private val clouds = CloudControls(renderer) { experimenting }

    /** This view's Clouds choice and fallbacks; a reopened view gets a new panel, so it starts at Asset again. */
    internal val cloudState: CloudViewState get() = clouds.state
    private var choices: List<CameraChoice> = emptyList()
    private var updatingControls = false
    private val rayBinding = RayControlBinding(renderer)

    /** The only switch for Ray Tracing: the Abyssus Properties panel flips it. The Scene View toolbar has no ray control. */
    override val rayControl: RayControl? get() = rayBinding.control
    private val experimentButton = if (java.lang.Boolean.getBoolean("abyssus.raytracing.experiment"))
        JToggleButton(AbyssusBundle.message("sceneViewRayExperiment")) else null
    private val experiment = experimentButton?.let { RayFeasibilityPreview { message -> thisLogger().info(message) } }
    private val experimenting: Boolean get() = experimentButton?.isSelected == true
    private val input = SceneInputForwarder(this, renderer, interaction, play, { experimenting }, { experiment?.orbit }, paint::update, ::frameSelection, ::pressOnOrientationWidget)

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

    /** Set when the canvas had to give up its context while hidden; its native surface can no longer be trusted. */
    private var abandoned = false

    private fun newCanvas(): GuardedGLCanvas = object : GuardedGLCanvas(glData()) {
        override fun initGL() {
            capabilities = GL.createCapabilities()
            val ctx = GdxRuntime.newContext(frame, GdxGlBridge.gl20(), GdxGlBridge.gl30(), Lwjgl3Files())
            gdx = ctx
            GdxRuntime.withContext(ctx) { renderer.create() }
        }

        /** Runs once per GL context, with it current: on `removeNotify` and on `disposeCanvas`. */
        override fun disposeGL() {
            val ctx = gdx ?: return
            gdx = null
            capabilities?.let { GL.setCapabilities(it) }
            try {
                rayBinding.feed?.reset()
                GdxRuntime.withContext(ctx) { experiment?.dispose(); renderer.dispose() }
            } catch (e: Throwable) {
                thisLogger().warn("Failed to release scene view GL resources", e)
            }
        }

        override fun onContextAbandoned() {
            rayBinding.feed?.reset()
            experiment?.abandon()
            renderer.abandonShadows()
            gdx = null
            abandoned = true
        }

        override fun paintGL() {
            val ctx = gdx ?: return
            capabilities?.let { GL.setCapabilities(it) }
            updateCornerSprites(width, framebufferWidth)
            frame.tick(framebufferWidth, framebufferHeight)
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
            swapBuffers()
        }
    }

    private var canvas: GuardedGLCanvas = newCanvas()
    private fun newCanvasHost() = CanvasOverlayHost(canvas) {
        try {
            canvas.captureFrame { capabilities?.let { GL.setCapabilities(it) } }
        } catch (e: Exception) {
            thisLogger().warn("Failed to capture the scene beneath an IDE overlay", e)
            null
        }
    }
    private var canvasHost = newCanvasHost()

    private var lastActivityNanos = System.nanoTime()
    private var lastRenderNanos = 0L

    /** Marks input on this view: it renders at the full frame rate for [IDLE_AFTER_NANOS] after the last event. */
    private val activityListener = java.awt.event.AWTEventListener { event ->
        if (event is InputEvent && event.source.let { it is java.awt.Component && SwingUtilities.isDescendingFrom(it, this) }) {
            lastActivityNanos = System.nanoTime()
        }
    }

    /** True while something moves or may change at once; an idle view (the sky still drifts) renders at [IDLE_FRAME_MILLIS]. */
    private fun busy(now: Long): Boolean =
        now - lastActivityNanos < IDLE_AFTER_NANOS || play.active || experimenting || renderer.loading ||
            renderer.presentedRayFrame

    private val timer: Timer = Timer(FRAME_MILLIS) {
        if (!canvas.glSafe()) return@Timer
        val now = System.nanoTime()
        if (!busy(now) && now - lastRenderNanos < IDLE_FRAME_MILLIS * 1_000_000L) return@Timer
        lastRenderNanos = now
        try {
            canvas.render()
        } catch (e: Throwable) {
            thisLogger().warn("Scene render failed, stopping the view", e)
            stopLoop()
            onFailure?.invoke(e)
        }
    }

    private fun stopLoop() = timer.stop()

    /** True once rendering failed for good: the viewport shows a message and the toolbar is disabled. */
    private var unavailable = false

    override fun showUnavailable(message: String): Boolean {
        stopLoop()
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
        add(buildToolbar(), BorderLayout.NORTH)
        add(canvasHost, BorderLayout.CENTER)
        add(statusLine, BorderLayout.SOUTH)
        input.attach(canvas)
        input.bindKeys()
        input.forwardKeys()
        interaction.onStateChanged = ::syncControls
        tools.onChanged = ::syncControls
        play.onChanged = ::syncControls
        syncControls()
        refreshCameraChoices(initial)
    }

    /**
     * Repaints the orientation widget and the stats strip into the sprites the renderer draws in the corners, when what they
     * show or the display scale changed. The widget shows the camera the frame is drawn from, so it also follows a scene camera.
     */
    private fun updateCornerSprites(logicalWidth: Int, framebufferWidth: Int) {
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

    private fun statsText(): String = statsLine(
        fps = null,
        objectsText = AbyssusBundle.message("sceneViewObjects", renderer.drawnModels.size + renderer.drawnTerrains.size),
        status = statusLine.text.orEmpty(),
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
            orbit.lookAlong(Vec3(-direction.x, -direction.y, -direction.z))
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
                Vec3(box.min.x, box.min.y, box.min.z), Vec3(box.max.x, box.max.y, box.max.z), camera.fieldOfView, aspect,
            ),
        )
    }

    private fun buildToolbar(): JPanel {
        dropButton.isFocusable = false
        dropButton.toolTipText = AbyssusBundle.message("sceneViewDropTooltip")
        dropButton.addActionListener { interaction.drop() }
        addLightButton.isFocusable = false
        addLightButton.toolTipText = AbyssusBundle.message("addLightTooltip")
        addLightButton.addActionListener {
            val actions = lightChoices() ?: return@addActionListener
            JBPopupFactory.getInstance().createActionGroupPopup(
                AbyssusBundle.message("addLightTitle"), actions, DataManager.getInstance().getDataContext(this),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
            ).showUnderneathOf(addLightButton)
        }
        addAssetButton.isFocusable = false
        addAssetButton.toolTipText = AbyssusBundle.message("addAssetTooltip")
        addAssetButton.addActionListener {
            val actions = assetChoices() ?: return@addActionListener
            JBPopupFactory.getInstance().createActionGroupPopup(
                AbyssusBundle.message("addAssetTitle"), actions, DataManager.getInstance().getDataContext(this),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
            ).showUnderneathOf(addAssetButton)
        }
        addPathButton.isFocusable = false
        addPathButton.toolTipText = AbyssusBundle.message("addPathArrangementTooltip")
        addPathButton.addActionListener {
            val actions = pathChoices() ?: return@addActionListener
            JBPopupFactory.getInstance().createActionGroupPopup(
                AbyssusBundle.message("addPathArrangementTitle"), actions, DataManager.getInstance().getDataContext(this),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
            ).showUnderneathOf(addPathButton)
        }
        regenerateButton.isFocusable = false
        regenerateButton.toolTipText = AbyssusBundle.message("regenerateArrangementTooltip")
        regenerateButton.addActionListener { interaction.selectedId?.let { onRegenerate?.invoke(it) } }
        cameraCombo.isFocusable = false
        addFoliageButton.isFocusable = false
        addFoliageButton.addActionListener {
            val id = interaction.selectedId ?: return@addActionListener
            val actions = foliageActions?.invoke(id) ?: return@addActionListener
            JBPopupFactory.getInstance().createActionGroupPopup(
                AbyssusBundle.message("addFoliageTitle"), actions, DataManager.getInstance().getDataContext(this),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
            ).showUnderneathOf(addFoliageButton)
        }
        cameraCombo.toolTipText = AbyssusBundle.message("sceneViewCameraTooltip")
        cameraCombo.addActionListener {
            if (updatingControls) return@addActionListener
            val choice = cameraCombo.selectedItem as? CameraChoice ?: return@addActionListener
            interaction.viewCamera = choice.id
        }
        return JPanel(WrapLayout(FlowLayout.LEFT, 4, 2)).apply {
            name = "scene-toolbar"
            accessibleContext.accessibleName = AbyssusBundle.message("sceneViewToolbarName")
            val groups = toolsToolbar.components()
            groups.forEach { add(it) }
            add(toolsToolbar.divider())
            add(cameraCombo)
            add(toolsToolbar.frameComponent())
            add(toolsToolbar.divider())
            add(dropButton)
            add(addLightButton)
            if (assetMenu != null) add(addAssetButton)
            if (pathMenu != null) add(addPathButton)
            add(regenerateButton)
            add(drawingHint)
            if (foliageActions != null) add(addFoliageButton)
            paint.addTo(this)
            add(clouds.combo)
            add(clouds.note)
            playStrip.addTo(this)
            overlayToolbar()?.let { add(it) }
            experimentButton?.let { button ->
                button.isFocusable = false
                button.addActionListener {
                    experiment?.stop()
                    syncControls()
                }
                add(button)
            }
        }
    }

    /** Each overlay's actions (such as Show Physics), in one small action toolbar; null when there are none. */
    private fun overlayToolbar(): JComponent? {
        if (overlays == null) return null
        val group = object : DefaultActionGroup() {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> =
                overlays.overlays.flatMap { it.overlay.actions() }.toTypedArray()
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("AbyssusSceneOverlays", group, true)
        toolbar.targetComponent = this
        return toolbar.component
    }

    /** Choices retain a supplier so placement follows the current orbit target at the moment of creation. */
    internal fun lightChoices(): DefaultActionGroup? = lightMenu?.choicesAt { orbit.target }

    /** The Add Asset choices, placing at the orbit target when a choice is made; null while assets cannot be added. */
    internal fun assetChoices(): DefaultActionGroup? = assetMenu?.choicesAt { orbit.target }

    /** The Add Path Arrangement choices (the project's models); null while arrangements cannot be added. */
    internal fun pathChoices(): DefaultActionGroup? = pathMenu?.choicesAt { asset -> startDrawing(asset) }

    /** Brings the toolbar's buttons and selector in line with the interaction state. */
    private fun syncControls() {
        renderer.state.gizmoSpace = tools.space
        renderer.display = tools.display
        renderer.state.snap = if (tools.snapOn) GizmoSnap(tools.snapStep, SNAP_ANGLE_DEGREES) else null
        statusLine.text = toolStatus(
            AbyssusBundle.message(when (interaction.mode) {
                GizmoMode.SELECT -> "sceneViewToolSelect"
                GizmoMode.MOVE -> "sceneViewToolMove"
                GizmoMode.ROTATE -> "sceneViewToolRotate"
            }),
            AbyssusBundle.message(if (tools.space == GizmoSpace.WORLD) "sceneViewSpaceWorld" else "sceneViewSpaceLocal"),
            if (tools.snapOn) AbyssusBundle.message("sceneViewSnap", formatStep(tools.snapStep)) else null,
        )
        paint.beforeSync()
        updatingControls = true
        try {
            val toolbar = toolbarState(
                interaction.mode, experimenting, play.active, interaction.canDrop,
                lightMenu?.available?.invoke() == true, assetMenu?.available?.invoke() == true,
                tools.display, renderer.content.lights.isNotEmpty(), renderer.params.fog != null, interaction.viewCamera != null,
            )
            lastToolbar = when {
                unavailable -> toolbar.disabled()
                paint.painting -> toolbar.copy(gizmoEnabled = false, spaceEnabled = false, snapEnabled = false)
                else -> toolbar
            }
            cameraCombo.isEnabled = toolbar.cameraEnabled
            dropButton.isEnabled = toolbar.dropEnabled
            addLightButton.isEnabled = toolbar.addLightEnabled
            addAssetButton.isEnabled = toolbar.addAssetEnabled
            addPathButton.isEnabled = !play.active && !experimenting && pathMenu?.available?.invoke() == true
            val arrangementSelected = renderer.content.arrangements.any { it.entityId == interaction.selectedId }
            regenerateButton.isVisible = arrangementSelected
            regenerateButton.isEnabled = arrangementSelected && !play.active && onRegenerate != null
            drawingHint.isVisible = interaction.drawing != null
            val foliage = interaction.selectedId?.let { foliageActions?.invoke(it) }
            addFoliageButton.isVisible = foliage != null
            addFoliageButton.isEnabled = !play.active && foliage?.templatePresentation?.isEnabled == true
            addFoliageButton.toolTipText = foliage?.templatePresentation?.description
            paint.sync()
            if (paint.painting) dropButton.isEnabled = false
            playStrip.sync()
            cameraCombo.selectedItem = choices.firstOrNull { it.id == interaction.viewCamera } ?: choices.firstOrNull()
        } finally {
            updatingControls = false
        }
        toolsToolbar.update()
        clouds.sync()
        if (unavailable) disableToolbarComponents()
    }

    /** Every plain Swing control of the toolbar off (the action toolbars read [lastToolbar]); the view cannot render. */
    private fun disableToolbarComponents() {
        fun off(c: java.awt.Component) {
            if (c is AbstractButton || c is JComboBox<*>) c.isEnabled = false
            (c as? java.awt.Container)?.components?.forEach(::off)
        }
        components.firstOrNull { it.name == "scene-toolbar" }?.let(::off)
    }

    /** After each rendered frame; see [CloudControls.frameRendered]. */
    internal fun cloudFrameRendered(
        interval: Float,
        volumetric: Boolean = renderer.drawnCloudTechnique == CloudTechnique.VOLUMETRIC,
    ) = clouds.frameRendered(interval, volumetric)

    private fun refreshCameraChoices(params: SceneRenderParams) {
        val fresh = cameraChoices(params.content, AbyssusBundle.message("sceneViewFreeCamera"))
        if (fresh != choices) {
            choices = fresh
            updatingControls = true
            try {
                cameraCombo.removeAllItems()
                fresh.forEach(cameraCombo::addItem)
            } finally {
                updatingControls = false
            }
        }
        syncControls()
    }

    /**
     * An abandoned canvas keeps a native surface that macOS no longer sizes with the component, so showing it again
     * renders into a stale, cropped drawable. A new canvas gets a new native surface and a new context.
     */
    private fun replaceAbandonedCanvas() {
        if (!abandoned) return
        abandoned = false
        remove(canvasHost)
        canvas = newCanvas()
        input.attach(canvas)
        canvasHost = newCanvasHost()
        add(canvasHost, BorderLayout.CENTER)
    }

    override var onPick: ((String) -> Unit)?
        get() = interaction.onPick
        set(value) {
            interaction.onPick = value
        }

    override var onTransform: ((String, TransformEdit) -> Boolean)?
        get() = interaction.onTransform
        set(value) {
            interaction.onTransform = value
        }

    override var onArrangementCreate: ((RenderAsset, List<Vec3>, Boolean) -> Boolean)?
        get() = interaction.onArrangementCreate
        set(value) {
            interaction.onArrangementCreate = value
        }

    override var onArrangementEdit: ((String, List<Vec3>) -> Boolean)?
        get() = interaction.onArrangementEdit
        set(value) {
            interaction.onArrangementEdit = value
        }

    override var onRegenerate: ((String) -> Unit)? = null
        set(value) {
            field = value
            syncControls()
        }

    override fun startDrawing(asset: RenderAsset) {
        paint.end()
        interaction.startDrawing(asset)
        requestFocusInWindow()
    }

    override fun groundHeights(): GroundHeight? = renderer.groundHeights()

    override fun selectEntity(entityId: String) {
        if (renderer.state.selectedId != entityId) paint.end()
        renderer.state.selectedId = entityId
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
        refreshCameraChoices(params)
    }

    override fun refreshAssets(revision: AssetRevisionBatch) {
        renderer.queueAssetRevision(revision)
        paint.refreshSession(force = true)
    }

    override fun stopPlay() = play.documentChanging()

    override fun placementPoint(): Vec3 = orbit.target

    override val playing: Boolean get() = play.active

    /** This view's play state; for tests. */
    internal val playState: PlayState get() = play

    override fun addNotify() {
        replaceAbandonedCanvas()
        super.addNotify()
        rayBinding.feed?.runtime?.setVisible(true)
        lastActivityNanos = System.nanoTime()
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(
            activityListener,
            java.awt.AWTEvent.MOUSE_EVENT_MASK or java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK or
                java.awt.AWTEvent.MOUSE_WHEEL_EVENT_MASK or java.awt.AWTEvent.KEY_EVENT_MASK,
        )
        timer.start()
    }

    override fun removeNotify() {
        java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(activityListener)
        stopLoop()
        experiment?.stop()
        rayBinding.feed?.runtime?.setVisible(false)
        super.removeNotify()
    }

    override fun dispose() {
        paint.close()
        stopLoop()
        canvasHost.stop()
        play.stop()
        overlays?.dispose()
        experiment?.stop()
        rayBinding.close()
        canvas.disposeCanvas() // releases GL resources through disposeGL while the context is still current
    }

    private companion object {
        const val FRAME_MILLIS = 16

        /** An idle view (no input for [IDLE_AFTER_NANOS], nothing loading or playing) renders every this many ms. */
        const val IDLE_FRAME_MILLIS = 100
        const val IDLE_AFTER_NANOS = 1_000_000_000L

        fun glData() = GLData().apply {
            majorVersion = 3
            minorVersion = 2
            profile = GLData.Profile.CORE
            forwardCompatible = true
            depthSize = 24
            swapInterval = 0 // the Swing timer paces frames; a vsync-blocked swap would stall the IDE thread
        }
    }
}

/** A stable name for an AWT key code, as the `VK_` constant without its prefix (`W`, `SPACE`, `LEFT`). */
private val KEY_NAMES: Map<Int, String> by lazy {
    KeyEvent::class.java.fields.filter { it.name.startsWith("VK_") && it.type == Int::class.javaPrimitiveType }
        .associate { it.getInt(null) to it.name.removePrefix("VK_") }
}

internal fun keyName(code: Int): String = KEY_NAMES[code] ?: "KEY_$code"
