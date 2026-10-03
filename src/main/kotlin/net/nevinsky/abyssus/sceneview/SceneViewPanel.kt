/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview

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
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.Timer

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
class SceneViewPanel(
    initial: SceneRenderParams,
    private val renderer: SceneRenderer = service<AbyssusCore>().let { SceneRenderer(it.loading, it.sceneShaders) },
) : JPanel(BorderLayout()), SceneView {

    private val frame = GdxFrame()
    private val orbit = OrbitCamera.from(initial.camera)
    private val interaction = SceneInteraction(renderer, orbit)
    private var gdx: GdxContext? = null

    private var lastCamera = initial.camera
    private var capabilities: GLCapabilities? = null

    override val view: JComponent get() = this

    private val moveButton = JToggleButton(AbyssusBundle.message("sceneViewMove"), true)
    private val rotateButton = JToggleButton(AbyssusBundle.message("sceneViewRotate"))
    private val cameraCombo = ComboBox<CameraChoice>()
    private var choices: List<CameraChoice> = emptyList()
    private var updatingControls = false

    init {
        renderer.params = initial
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
                GdxRuntime.withContext(ctx) { renderer.dispose() }
            } catch (e: Throwable) {
                thisLogger().warn("Failed to release scene view GL resources", e)
            }
        }

        override fun onContextAbandoned() {
            gdx = null
            abandoned = true
        }

        override fun paintGL() {
            val ctx = gdx ?: return
            capabilities?.let { GL.setCapabilities(it) }
            frame.tick(framebufferWidth, framebufferHeight)
            GdxRuntime.withContext(ctx) { renderer.render(frame.width, frame.height, orbit, frame.deltaSeconds) }
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
            add(cameraCombo)
        }
    }

    /** W and E switch the gizmo, Esc cancels a drag, whenever the focus is anywhere in the view. */
    private fun bindKeys() {
        fun bind(key: Int, action: () -> Unit) =
            registerKeyboardAction({ action() }, KeyStroke.getKeyStroke(key, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        bind(KeyEvent.VK_W) { interaction.mode = GizmoMode.MOVE }
        bind(KeyEvent.VK_E) { interaction.mode = GizmoMode.ROTATE }
        bind(KeyEvent.VK_ESCAPE) { interaction.escape() }
    }

    /** Brings the toolbar's buttons and selector in line with the interaction state. */
    private fun syncControls() {
        updatingControls = true
        try {
            moveButton.isSelected = interaction.mode == GizmoMode.MOVE
            rotateButton.isSelected = interaction.mode == GizmoMode.ROTATE
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
        fun sync() {
            interaction.size = ViewSize(target.width, target.height, target.framebufferWidth, target.framebufferHeight)
        }
        val input = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                target.requestFocusInWindow()
                sync()
                interaction.pressed(e.x, e.y, SwingUtilities.isLeftMouseButton(e))
            }

            override fun mouseReleased(e: MouseEvent) {
                sync()
                interaction.released(e.x, e.y, SwingUtilities.isLeftMouseButton(e))
            }

            override fun mouseDragged(e: MouseEvent) {
                sync()
                interaction.dragged(e.x, e.y, SwingUtilities.isLeftMouseButton(e))
            }

            override fun mouseMoved(e: MouseEvent) {
                sync()
                interaction.moved(e.x, e.y)
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
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

    override fun setParams(params: SceneRenderParams) {
        renderer.params = params
        if (params.camera != lastCamera) {
            lastCamera = params.camera
            orbit.reset(params.camera)
        }
        interaction.paramsChanged(params)
        refreshCameraChoices(params)
    }

    override fun addNotify() {
        replaceAbandonedCanvas()
        super.addNotify()
        timer.start()
    }

    override fun removeNotify() {
        stopLoop()
        super.removeNotify()
    }

    override fun dispose() {
        stopLoop()
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
