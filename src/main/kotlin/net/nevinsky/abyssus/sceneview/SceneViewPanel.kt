/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.intellij.ide.DataManager
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
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JButton
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.Timer
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
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
    ray: RayIntegration? = null,
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
    private val cameraCombo = ComboBox<CameraChoice>()
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
        // Ray tracing is optional: without an integration the view stays raster only.
        ray?.let(::installRay)
    }

    /**
     * Binds this view's Ray Tracing mode to [integration], replacing any earlier binding. Nothing native is created
     * until ray tracing is switched on from Abyssus Properties.
     */
    internal fun installRay(integration: RayIntegration) {
        rayFeed?.close()
        val feed = integration.newFeed("scene-view-${NEXT_VIEW.incrementAndGet()}")
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
        interaction.onStateChanged = ::syncControls
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
            add(cameraCombo)
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

    /** Choices retain a supplier so placement follows the current orbit target at the moment of creation. */
    internal fun lightChoices(): DefaultActionGroup? = if (canAddLight()) lightActions?.invoke { orbit.target } else null

    /** W/E switch the gizmo, D drops the selection, Esc cancels a drag, with focus anywhere in the view. */
    private fun bindKeys() {
        fun bind(key: Int, action: () -> Unit) {
            val stroke = KeyStroke.getKeyStroke(key, 0)
            registerKeyboardAction({ if (!experimenting) action() }, stroke, WHEN_FOCUSED)
            registerKeyboardAction({ if (!experimenting) action() }, stroke, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        }
        bind(KeyEvent.VK_W) { interaction.mode = GizmoMode.MOVE }
        bind(KeyEvent.VK_E) { interaction.mode = GizmoMode.ROTATE }
        bind(KeyEvent.VK_D) { interaction.drop() }
        bind(KeyEvent.VK_ESCAPE) { interaction.escape() }
    }

    /** Brings the toolbar's buttons and selector in line with the interaction state. */
    private fun syncControls() {
        updatingControls = true
        try {
            moveButton.isSelected = interaction.mode == GizmoMode.MOVE
            rotateButton.isSelected = interaction.mode == GizmoMode.ROTATE
            moveButton.isEnabled = !experimenting
            rotateButton.isEnabled = !experimenting
            cameraCombo.isEnabled = !experimenting
            dropButton.isEnabled = !experimenting && interaction.canDrop
            addLightButton.isEnabled = !experimenting && lightActions != null && canAddLight()
            cameraCombo.selectedItem = choices.firstOrNull { it.id == interaction.viewCamera } ?: choices.firstOrNull()
        } finally {
            updatingControls = false
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
                sync()
                interaction.pressed(e.x, e.y, SwingUtilities.isLeftMouseButton(e))
            }

            override fun mouseReleased(e: MouseEvent) {
                if (experimenting) return
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
