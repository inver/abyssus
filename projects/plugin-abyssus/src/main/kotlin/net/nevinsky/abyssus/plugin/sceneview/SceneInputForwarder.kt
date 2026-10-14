/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.pick.*
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.awt.event.*
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * Keyboard and mouse input of the Scene view: W/E/D/Esc/Enter/Backspace on the [panel], the canvas mouse into the
 * [interaction] (select, gizmo, orbit, the arrangement point menu, the foliage brush) and, while [play] runs, into the
 * simulation. [experimenting] and [experimentOrbit] stand for the ray feasibility experiment, which takes the mouse over;
 * [updatePaintMode] re-reads the foliage brush (the Shift key erases). AWT thread.
 */
internal class SceneInputForwarder(
    private val panel: JComponent,
    private val renderer: SceneRenderer,
    private val interaction: SceneInteraction,
    private val play: PlayState,
    private val experimenting: () -> Boolean,
    private val experimentOrbit: () -> OrbitCamera?,
    private val updatePaintMode: (shift: Boolean) -> Unit,
    private val frameSelection: () -> Unit = {},
    private val pressOnOverlay: (x: Int, y: Int) -> Boolean = { _, _ -> false },
) {
    /** Q/W/E switch the tool (Select, Move, Rotate), F frames the selection, D drops the selection, Esc cancels a drag, with focus anywhere in the view. */
    fun bindKeys() {
        fun bind(key: Int, action: () -> Unit) {
            val stroke = KeyStroke.getKeyStroke(key, 0)
            panel.registerKeyboardAction({ if (!experimenting()) action() }, stroke, JComponent.WHEN_FOCUSED)
            panel.registerKeyboardAction({ if (!experimenting()) action() }, stroke, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        }
        // while playing these keys go to the simulation instead (forwardKeys), and Escape stops it
        bind(KeyEvent.VK_Q) { if (!play.active) interaction.mode = GizmoMode.SELECT }
        bind(KeyEvent.VK_W) { if (!play.active) interaction.mode = GizmoMode.MOVE }
        bind(KeyEvent.VK_E) { if (!play.active) interaction.mode = GizmoMode.ROTATE }
        bind(KeyEvent.VK_F) { if (!play.active) frameSelection() }
        bind(KeyEvent.VK_D) { if (!play.active) interaction.drop() }
        bind(KeyEvent.VK_ESCAPE) { if (!play.escape()) interaction.escape() }
        bind(KeyEvent.VK_ENTER) { if (!play.active) interaction.enter() }
        bind(KeyEvent.VK_BACK_SPACE) { if (!play.active) interaction.backspace() }
    }

    /** While playing, every key but Escape goes to the simulation and is consumed, so no editor shortcut fires. */
    fun forwardKeys() {
        panel.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) = forward(e, SimulationInput.Kind.KEY_DOWN)
            override fun keyReleased(e: KeyEvent) = forward(e, SimulationInput.Kind.KEY_UP)

            private fun forward(e: KeyEvent, kind: SimulationInput.Kind) {
                if (!play.active || e.keyCode == KeyEvent.VK_ESCAPE) return
                play.input(SimulationInput(kind, key = keyName(e.keyCode)))
                e.consume()
            }
        })
    }

    fun attach(target: GuardedGLCanvas) {
        // The panel takes focus instead of the canvas: a heavyweight AWT canvas as the focus owner breaks IDE popups
        // ("Unexpected component for dataContext"), which need a JComponent.
        target.isFocusable = false
        panel.isFocusable = true
        fun sync() {
            interaction.size = ViewSize(target.width, target.height, target.framebufferWidth, target.framebufferHeight)
        }

        val input = object : MouseAdapter() {
            private var experimentX = 0
            private var experimentY = 0

            /** True from a left press that landed on a viewport overlay until its release: the scene gets none of it. */
            private var onOverlay = false

            override fun mousePressed(e: MouseEvent) {
                panel.requestFocusInWindow()
                if (experimenting()) {
                    experimentX = e.x; experimentY = e.y; return
                }
                onOverlay = SwingUtilities.isLeftMouseButton(e) && pressOnOverlay(e.x, e.y)
                if (onOverlay) return
                forwardMouse(SimulationInput.Kind.BUTTON_DOWN, e)
                sync()
                if (e.isPopupTrigger && pointMenu(e)) return
                updatePaintMode(e.isShiftDown)
                cursor(e)
                interaction.pressed(e.x, e.y, SwingUtilities.isLeftMouseButton(e))
            }

            override fun mouseReleased(e: MouseEvent) {
                if (experimenting()) return
                if (onOverlay) { onOverlay = false; return }
                forwardMouse(SimulationInput.Kind.BUTTON_UP, e)
                sync()
                if (e.isPopupTrigger && pointMenu(e)) return
                interaction.released(e.x, e.y, SwingUtilities.isLeftMouseButton(e), e.clickCount)
            }

            override fun mouseDragged(e: MouseEvent) {
                if (experimenting()) {
                    val dx = (e.x - experimentX).toFloat()
                    val dy = (e.y - experimentY).toFloat()
                    experimentX = e.x; experimentY = e.y
                    if (SwingUtilities.isLeftMouseButton(e)) experimentOrbit()?.orbit(dx, dy)
                    else experimentOrbit()?.pan(dx, dy)
                    return
                }
                if (onOverlay) return
                sync()
                updatePaintMode(e.isShiftDown)
                cursor(e)
                interaction.dragged(e.x, e.y, SwingUtilities.isLeftMouseButton(e))
            }

            override fun mouseMoved(e: MouseEvent) {
                if (experimenting()) return
                forwardMouse(SimulationInput.Kind.MOUSE_MOVE, e)
                sync()
                interaction.moved(e.x, e.y)
                cursor(e)
            }

            override fun mouseExited(e: MouseEvent) { renderer.brushCursor = null }

            private fun cursor(e: MouseEvent) {
                val mode = renderer.state.paint
                val size = interaction.size
                if (mode == null || size.isEmpty) { renderer.brushCursor = null; return }
                val at = size.toFramebuffer(e.x, e.y)
                val ray = renderer.queries.rayAt(at.x, at.y, size.framebufferWidth, size.framebufferHeight)
                val terrain = renderer.queries.terrainOf(mode.entityId)
                val hit = ray?.let { renderer.queries.terrainHit(mode.entityId, it) }
                renderer.brushCursor = if (terrain != null && hit != null) FoliageBrushCursor(terrain, hit, mode.radius) else null
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                if (experimenting()) {
                    experimentOrbit()?.zoom(e.preciseWheelRotation.toFloat()); return
                }
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
     * The right-click menu of a point handle of the selected arrangement: Remove Point, disabled while only two points
     * remain. True when the click hit a handle and the menu was shown (the press is then not a pan).
     */
    private fun pointMenu(e: MouseEvent): Boolean {
        val id = interaction.selectedId ?: return false
        if (interaction.drawing != null || play.active) return false
        val size = interaction.size
        if (size.isEmpty) return false
        val at = size.toFramebuffer(e.x, e.y)
        val hit = renderer.queries.pathHandleAt(at.x, at.y, size.framebufferWidth, size.framebufferHeight) as? PathHit.Point ?: return false
        val menu = JPopupMenu()
        menu.add(JMenuItem(AbyssusBundle.message("removePathPoint")).apply {
            name = "remove-path-point"
            isEnabled = interaction.canRemovePoint(id)
            addActionListener { interaction.removePoint(id, hit.index) }
        })
        menu.show(e.component, e.x, e.y)
        return true
    }
}
