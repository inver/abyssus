/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.content.CameraPlacement

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import net.nevinsky.abyssus.AbyssusCore
import com.intellij.openapi.components.service
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.GdxGlBridge
import com.intellij.openapi.diagnostic.thisLogger
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.sceneview.gizmo.GizmoMode
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.opengl.awt.GLData
import com.intellij.openapi.ui.ComboBox
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JButton
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.Timer
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.filetype.documentDisplayMessage as displayMessage

/** An entry of the camera selector: [id] is the camera entity to look through, null for the free orbit view. */
data class CameraChoice(val id: String?, val label: String) {
    override fun toString() = label
}

/** "Free camera" followed by the scene's cameras by name (their id when unnamed, which [CameraPlacement.name] already is). */
fun cameraChoices(content: SceneContent, freeLabel: String): List<CameraChoice> =
    listOf(CameraChoice(null, freeLabel)) + content.cameras.map { CameraChoice(it.entityId, it.name) }

/**
 * Swing panel hosting a core-profile GL canvas that renders a scene with libGDX. Orbit/pan/zoom with the mouse; click
 * selects an object, whose gizmo (Move: W, Rotate: E) can be dragged; a selector looks through a camera entity.
 */
class SceneViewPanel internal constructor(
    initial: SceneRenderParams,
    private val renderer: SceneRenderer,
    private val lightActions: ((() -> Vec3) -> DefaultActionGroup)? = null,
    private val canAddLight: () -> Boolean = { lightActions != null },
    /** The project's models and terrains to place at the orbit target; null: no Add Asset button. */
    private val assetActions: ((() -> Vec3) -> DefaultActionGroup)? = null,
    private val canAddAsset: () -> Boolean = { assetActions != null },
    ray: RayIntegration? = null,
    /** Play in this view; without a simulation provider there are no play controls. */
    private val play: PlayState = PlayState(null),
    /** What Play starts from; read when Play is pressed. */
    private val simulationRequest: ((selection: String?) -> SimulationRequest)? = null,
    /** Other plugins' overlays for this view. */
    private val overlays: SceneOverlayHost? = null,
) : JPanel(BorderLayout()), SceneView, RayControlProvider {

    private val frame = GdxFrame()
    private val orbit = OrbitCamera.from(initial.camera)
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
    private val cameraCombo = ComboBox<CameraChoice>()
    private val playButton = JButton(AbyssusBundle.message("sceneViewPlay")).apply { name = "play" }
    private val pauseButton = JButton(AbyssusBundle.message("sceneViewPause")).apply { name = "pause" }
    private val stepButton = JButton(AbyssusBundle.message("sceneViewStep")).apply { name = "step" }
    private val stopButton = JButton(AbyssusBundle.message("sceneViewStop")).apply { name = "stop" }
    private val playStatus = JLabel()
    private var choices: List<CameraChoice> = emptyList()
    private var updatingControls = false
    private var rayFeed: RayViewFeed? = null
    private var shownRay: RayModeSnapshot? = null
    private val rayListeners = mutableListOf<() -> Unit>()

    /** The only switch for Ray Tracing: the Abyssus Properties panel flips it. The Scene View toolbar has no ray control. */
    override val rayControl: RayControl? get() = if (rayFeed == null) null else panelRayControl
    private val panelRayControl = object : RayControl {
        override val mode: RayModeSnapshot? get() = rayMode
        override fun setRequested(enabled: Boolean) {
            rayFeed?.runtime?.setRequested(enabled)
            refreshRay()
        }
        override fun retry() {
            rayFeed?.runtime?.retry()
            refreshRay()
        }
        override fun addListener(parent: com.intellij.openapi.Disposable, listener: () -> Unit) {
            rayListeners += listener
            com.intellij.openapi.util.Disposer.register(parent) { rayListeners -= listener }
        }
    }
    private val experimentButton = if (java.lang.Boolean.getBoolean("abyssus.raytracing.experiment"))
        JToggleButton(AbyssusBundle.message("sceneViewRayExperiment")) else null
    private val experiment = experimentButton?.let { RayFeasibilityPreview { message -> thisLogger().info(message) } }
    private val experimenting: Boolean get() = experimentButton?.isSelected == true

    init {
        renderer.params = initial
        renderer.overlays = overlays
        // Ray tracing is optional: without an integration the view stays raster only.
        ray?.let(::installRay)
    }

    /**
     * Binds this view's Ray Tracing mode to [integration], replacing any earlier binding. Nothing native is created
     * until ray tracing is switched on from Abyssus Properties.
     */
    internal fun installRay(integration: RayIntegration) {
        rayFeed?.close()
        val feed = integration.newFeed("scene-view-${NEXT_VIEW.incrementAndGet()}", renderer.assets)
        feed.runtime.mode.addListener { SwingUtilities.invokeLater { if (rayFeed === feed) refreshRay() } }
        rayFeed = feed
        renderer.rayFrameProvider = { context -> feed.frame(context) }
        shownRay = null
        refreshRay()
    }

    /** The current Ray Tracing mode of this view (Off until it is switched on from Abyssus Properties). */
    internal val rayMode: RayModeSnapshot? get() = rayFeed?.runtime?.mode?.snapshot

    /** Tells listeners (the Properties panel's switch) when the mode changed. Cheap when nothing did. */
    private fun refreshRay() {
        val snapshot = rayFeed?.runtime?.mode?.snapshot
        if (snapshot == shownRay) return
        shownRay = snapshot
        rayListeners.toList().forEach { it() }
    }

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
                rayFeed?.reset()
                GdxRuntime.withContext(ctx) { experiment?.dispose(); renderer.dispose() }
            } catch (e: Throwable) {
                thisLogger().warn("Failed to release scene view GL resources", e)
            }
        }

        override fun onContextAbandoned() {
            rayFeed?.reset()
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
            if (!experimenting) interaction.frameRendered()
            refreshRay()
            experimentButton?.toolTipText = experiment?.failure?.let {
                AbyssusBundle.message("sceneViewRayExperimentFailure", it.displayMessage())
            } ?: AbyssusBundle.message("sceneViewRayExperimentTooltip")
            swapBuffers()
        }
    }

    private var canvas: GuardedGLCanvas = newCanvas()

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
        add(canvas, BorderLayout.CENTER)
        attachInput(canvas)
        bindKeys()
        forwardKeys()
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
        cameraCombo.isFocusable = false
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
            if (assetActions != null) add(addAssetButton)
            add(cameraCombo)
            if (play.available) addPlayControls(this)
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

    private fun addPlayControls(toolbar: JPanel) {
        playButton.toolTipText = AbyssusBundle.message("sceneViewPlayTooltip")
        pauseButton.toolTipText = AbyssusBundle.message("sceneViewPauseTooltip")
        stepButton.toolTipText = AbyssusBundle.message("sceneViewStepTooltip")
        stopButton.toolTipText = AbyssusBundle.message("sceneViewStopTooltip")
        for (button in listOf(playButton, pauseButton, stepButton, stopButton)) {
            button.isFocusable = false
            toolbar.add(button)
        }
        toolbar.add(playStatus)
        playButton.addActionListener {
            val request = simulationRequest ?: return@addActionListener
            play.play { request(renderer.state.selectedId) }
            requestFocusInWindow()
        }
        pauseButton.addActionListener { play.pause() }
        stepButton.addActionListener { play.step() }
        stopButton.addActionListener { play.stop() }
    }

    /** Each overlay's actions (such as Show Physics), in one small action toolbar; null when there are none. */
    private fun overlayToolbar(): JComponent? {
        val actions = overlays?.overlays?.flatMap { it.overlay.actions() }.orEmpty()
        if (actions.isEmpty()) return null
        val toolbar = ActionManager.getInstance().createActionToolbar("AbyssusSceneOverlays", DefaultActionGroup(actions), true)
        toolbar.targetComponent = this
        return toolbar.component
    }

    /** Choices retain a supplier so placement follows the current orbit target at the moment of creation. */
    internal fun lightChoices(): DefaultActionGroup? = if (canAddLight()) lightActions?.invoke { orbit.target } else null

    /** The Add Asset choices, placing at the orbit target when a choice is made; null while assets cannot be added. */
    internal fun assetChoices(): DefaultActionGroup? = if (canAddAsset()) assetActions?.invoke { orbit.target } else null

    /** W/E switch the gizmo, D drops the selection, Esc cancels a drag, with focus anywhere in the view. */
    private fun bindKeys() {
        fun bind(key: Int, action: () -> Unit) {
            val stroke = KeyStroke.getKeyStroke(key, 0)
            registerKeyboardAction({ if (!experimenting) action() }, stroke, WHEN_FOCUSED)
            registerKeyboardAction({ if (!experimenting) action() }, stroke, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        }
        // while playing these keys go to the simulation instead (forwardKeys), and Escape stops it
        bind(KeyEvent.VK_W) { if (!play.active) interaction.mode = GizmoMode.MOVE }
        bind(KeyEvent.VK_E) { if (!play.active) interaction.mode = GizmoMode.ROTATE }
        bind(KeyEvent.VK_D) { if (!play.active) interaction.drop() }
        bind(KeyEvent.VK_ESCAPE) { if (!play.escape()) interaction.escape() }
    }

    /** While playing, every key but Escape goes to the simulation and is consumed, so no editor shortcut fires. */
    private fun forwardKeys() {
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) = forward(e, SimulationInput.Kind.KEY_DOWN)
            override fun keyReleased(e: KeyEvent) = forward(e, SimulationInput.Kind.KEY_UP)

            private fun forward(e: KeyEvent, kind: SimulationInput.Kind) {
                if (!play.active || e.keyCode == KeyEvent.VK_ESCAPE) return
                play.input(SimulationInput(kind, key = keyName(e.keyCode)))
                e.consume()
            }
        })
    }

    /** Brings the toolbar's buttons and selector in line with the interaction state. */
    private fun syncControls() {
        updatingControls = true
        try {
            moveButton.isSelected = interaction.mode == GizmoMode.MOVE
            rotateButton.isSelected = interaction.mode == GizmoMode.ROTATE
            val editing = !experimenting && !play.active
            moveButton.isEnabled = editing
            rotateButton.isEnabled = editing
            cameraCombo.isEnabled = !experimenting
            dropButton.isEnabled = editing && interaction.canDrop
            addLightButton.isEnabled = editing && lightActions != null && canAddLight()
            addAssetButton.isEnabled = editing && assetActions != null && canAddAsset()
            syncPlayControls()
            cameraCombo.selectedItem = choices.firstOrNull { it.id == interaction.viewCamera } ?: choices.firstOrNull()
        } finally {
            updatingControls = false
        }
    }

    private fun syncPlayControls() {
        if (!play.available) {
            for (c in listOf(playButton, pauseButton, stepButton, stopButton, playStatus)) c.isVisible = false
            return
        }
        val phase = play.phase
        playButton.isEnabled = phase == PlayState.Phase.IDLE || phase == PlayState.Phase.FAILED || phase == PlayState.Phase.PAUSED
        pauseButton.isEnabled = phase == PlayState.Phase.PLAYING
        stepButton.isEnabled = phase == PlayState.Phase.PAUSED
        stopButton.isEnabled = play.active
        playStatus.text = when (phase) {
            PlayState.Phase.IDLE -> ""
            PlayState.Phase.STARTING -> AbyssusBundle.message("sceneViewPlayStarting")
            PlayState.Phase.PLAYING -> AbyssusBundle.message("sceneViewPlaying")
            PlayState.Phase.PAUSED -> AbyssusBundle.message("sceneViewPaused")
            PlayState.Phase.FAILED -> AbyssusBundle.message("sceneViewPlayFailed")
        }
    }

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

    private fun attachInput(target: GuardedGLCanvas) {
        // The panel takes focus instead of the canvas: a heavyweight AWT canvas as the focus owner breaks IDE popups
        // ("Unexpected component for dataContext"), which need a JComponent.
        target.isFocusable = false
        isFocusable = true
        fun sync() {
            interaction.size = ViewSize(target.width, target.height, target.framebufferWidth, target.framebufferHeight)
        }
        val input = object : MouseAdapter() {
            private var experimentX = 0
            private var experimentY = 0
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                if (experimenting) { experimentX = e.x; experimentY = e.y; return }
                forwardMouse(SimulationInput.Kind.BUTTON_DOWN, e)
                sync()
                interaction.pressed(e.x, e.y, SwingUtilities.isLeftMouseButton(e))
            }

            override fun mouseReleased(e: MouseEvent) {
                if (experimenting) return
                forwardMouse(SimulationInput.Kind.BUTTON_UP, e)
                sync()
                interaction.released(e.x, e.y, SwingUtilities.isLeftMouseButton(e))
            }

            override fun mouseDragged(e: MouseEvent) {
                if (experimenting) {
                    val dx = (e.x - experimentX).toFloat()
                    val dy = (e.y - experimentY).toFloat()
                    experimentX = e.x; experimentY = e.y
                    if (SwingUtilities.isLeftMouseButton(e)) experiment?.orbit?.orbit(dx, dy)
                    else experiment?.orbit?.pan(dx, dy)
                    return
                }
                sync()
                interaction.dragged(e.x, e.y, SwingUtilities.isLeftMouseButton(e))
            }

            override fun mouseMoved(e: MouseEvent) {
                if (experimenting) return
                forwardMouse(SimulationInput.Kind.MOUSE_MOVE, e)
                sync()
                interaction.moved(e.x, e.y)
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                if (experimenting) { experiment?.orbit?.zoom(e.preciseWheelRotation.toFloat()); return }
                interaction.wheel(e.preciseWheelRotation.toFloat())
            }
        }
        target.addMouseListener(input)
        target.addMouseMotionListener(input)
        target.addMouseWheelListener(input)
    }

    /** While playing, mouse buttons and moves also go to the simulation; the camera still orbits, pans and zooms. */
    private fun forwardMouse(kind: SimulationInput.Kind, e: MouseEvent) {
        if (play.active) play.input(SimulationInput(kind, button = e.button, x = e.x, y = e.y))
    }

    /**
     * An abandoned canvas keeps a native surface that macOS no longer sizes with the component, so showing it again
     * renders into a stale, cropped drawable. A new canvas gets a new native surface and a new context.
     */
    private fun replaceAbandonedCanvas() {
        if (!abandoned) return
        abandoned = false
        remove(canvas)
        canvas = newCanvas()
        attachInput(canvas)
        add(canvas, BorderLayout.CENTER)
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

    override fun selectEntity(entityId: String) {
        renderer.state.selectedId = entityId
        syncControls()
    }

    override fun setParams(params: SceneRenderParams) {
        renderer.params = params
        if (params.camera != lastCamera) {
            lastCamera = params.camera
            orbit.reset(params.camera)
        }
        interaction.paramsChanged(params)
        refreshCameraChoices(params)
    }

    override fun refreshAssets(revision: AssetRevisionBatch) = renderer.queueAssetRevision(revision)

    override fun stopPlay() = play.documentChanging()

    /** This view's play state; for tests. */
    internal val playState: PlayState get() = play

    override fun addNotify() {
        replaceAbandonedCanvas()
        super.addNotify()
        rayFeed?.runtime?.setVisible(true)
        timer.start()
    }

    override fun removeNotify() {
        stopLoop()
        experiment?.stop()
        rayFeed?.runtime?.setVisible(false)
        super.removeNotify()
    }

    override fun dispose() {
        stopLoop()
        play.stop()
        overlays?.dispose()
        experiment?.stop()
        rayFeed?.close()
        rayFeed = null
        renderer.rayFrameProvider = null
        canvas.disposeCanvas() // releases GL resources through disposeGL while the context is still current
    }

    private companion object {
        const val FRAME_MILLIS = 16
        val NEXT_VIEW = java.util.concurrent.atomic.AtomicLong()

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
