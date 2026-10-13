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
    private val orbit = OrbitCamera(initial.camera)
    private val interaction = SceneInteraction(renderer.state, renderer.queries, orbit)
    private var gdx: GdxContext? = null

    private var lastCamera = initial.camera
    private var capabilities: GLCapabilities? = null

    override val view: JComponent get() = this

    private val moveButton = JToggleButton(AbyssusBundle.message("sceneViewMove"), true)
    private val rotateButton = JToggleButton(AbyssusBundle.message("sceneViewRotate"))
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
    private val input = SceneInputForwarder(this, renderer, interaction, play, { experimenting }, { experiment?.orbit }, paint::update)

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

    private val timer: Timer = Timer(FRAME_MILLIS) {
        if (!canvas.glSafe()) return@Timer
        try {
            canvas.render()
        } catch (e: Throwable) {
            thisLogger().warn("Scene render failed, stopping the view", e)
            stopLoop()
            onFailure?.invoke(e)
        }
    }

    private fun stopLoop() = timer.stop()

    init {
        add(buildToolbar(), BorderLayout.NORTH)
        add(canvasHost, BorderLayout.CENTER)
        input.attach(canvas)
        input.bindKeys()
        input.forwardKeys()
        interaction.onStateChanged = ::syncControls
        play.onChanged = ::syncControls
        syncControls()
        refreshCameraChoices(initial)
    }

    private fun buildToolbar(): JPanel {
        val group = ButtonGroup()
        group.add(moveButton)
        group.add(rotateButton)
        moveButton.toolTipText = AbyssusBundle.message("sceneViewMoveTooltip")
        rotateButton.toolTipText = AbyssusBundle.message("sceneViewRotateTooltip")
        moveButton.isFocusable = false
        rotateButton.isFocusable = false
        moveButton.addActionListener { if (!updatingControls) interaction.mode = GizmoMode.MOVE }
        rotateButton.addActionListener { if (!updatingControls) interaction.mode = GizmoMode.ROTATE }
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
        return JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(moveButton)
            add(rotateButton)
            add(dropButton)
            add(addLightButton)
            if (assetMenu != null) add(addAssetButton)
            if (pathMenu != null) add(addPathButton)
            add(regenerateButton)
            add(drawingHint)
            if (foliageActions != null) add(addFoliageButton)
            paint.addTo(this)
            add(cameraCombo)
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
        paint.beforeSync()
        updatingControls = true
        try {
            val toolbar = toolbarState(
                interaction.mode, experimenting, play.active, interaction.canDrop,
                lightMenu?.available?.invoke() == true, assetMenu?.available?.invoke() == true,
            )
            moveButton.isSelected = toolbar.moveSelected
            rotateButton.isSelected = toolbar.rotateSelected
            moveButton.isEnabled = toolbar.gizmoEnabled
            rotateButton.isEnabled = toolbar.gizmoEnabled
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
            if (paint.painting) { moveButton.isEnabled = false; rotateButton.isEnabled = false; dropButton.isEnabled = false }
            playStrip.sync()
            cameraCombo.selectedItem = choices.firstOrNull { it.id == interaction.viewCamera } ?: choices.firstOrNull()
        } finally {
            updatingControls = false
        }
        clouds.sync()
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
        timer.start()
    }

    override fun removeNotify() {
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
