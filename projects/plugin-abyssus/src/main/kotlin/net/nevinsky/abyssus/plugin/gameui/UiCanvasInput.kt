/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import net.nevinsky.abyssus.lib.core.editor.ui.UiGesturePreview
import net.nevinsky.abyssus.lib.core.editor.ui.UiHitTest
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutResult
import net.nevinsky.abyssus.lib.core.ui.layout.UiRect
import net.nevinsky.abyssus.lib.core.ui.render.UiEditOverlay
import net.nevinsky.abyssus.lib.core.ui.render.UiPresenter
import net.nevinsky.abyssus.lib.core.ui.state.uiBaseId
import net.nevinsky.abyssus.plugin.sceneview.GuardedGLCanvas
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** How the preview picture sits in the panel: its scale and the offsets (framebuffer pixels). The canvas sets it each frame. */
internal class PreviewFit {
    var offsetX = 0f
    var offsetY = 0f
    var scale = 1f

    /** A pointer coordinate in the preview's own pixels, undoing the panel scale and the centring of the fitted picture. */
    fun x(x: Float) = (x - offsetX) / scale
    fun y(y: Float) = (y - offsetY) / scale
}

/**
 * Turns the canvas's pointer and key events into the model's edit gestures, or, in preview mode, into presenter input.
 * It holds no GL: the canvas passes the presenter and the layout it currently shows. AWT thread only.
 */
internal class UiCanvasInput(
    private val owner: JComponent,
    private val controller: UiCanvasModel,
    private val presenter: () -> UiPresenter?,
    private val layoutOf: (screenId: String) -> UiLayoutResult?,
    private val overlay: UiEditOverlay,
    private val fit: PreviewFit,
    /** The ratio of framebuffer pixels to AWT pixels of the current canvas. */
    private val scale: () -> Float,
) {
    private val hits = UiHitTest()

    /** The key handling of the panel: Delete and Ctrl+D edit; in preview mode keys go to the presenter. */
    fun keyListener() = object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
            if (controller.mode == UiEditorMode.PREVIEW) {
                if (presenter()?.keyDown(toGdxKey(e.keyCode), e.isShiftDown) == true) e.consume()
            } else when {
                e.keyCode == KeyEvent.VK_DELETE || e.keyCode == KeyEvent.VK_BACK_SPACE -> controller.deleteSelected()
                e.keyCode == KeyEvent.VK_D && (e.isControlDown || e.isMetaDown) -> controller.duplicateSelected()
            }
        }

        override fun keyTyped(e: KeyEvent) {
            if (controller.mode == UiEditorMode.PREVIEW) presenter()?.keyTyped(e.keyChar)
        }
    }

    /** The pointer handling of [target]; the panel, not the heavyweight canvas, takes focus. */
    fun attach(target: GuardedGLCanvas) {
        // The panel takes focus instead of the heavyweight canvas, which breaks IDE popups as the focus owner.
        target.isFocusable = false
        val input = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                owner.requestFocusInWindow()
                if (!SwingUtilities.isLeftMouseButton(e)) return
                val x = e.x * scale(); val y = e.y * scale()
                if (controller.mode == UiEditorMode.PREVIEW) { presenter()?.touchDown(fit.x(x), fit.y(y)); return }
                val id = pick(x, y)
                if (id != null) controller.beginGesture(id, x, y) else controller.select(null)
            }

            override fun mouseDragged(e: MouseEvent) {
                val x = e.x * scale(); val y = e.y * scale()
                if (controller.mode == UiEditorMode.PREVIEW) { presenter()?.touchDragged(fit.x(x), fit.y(y)); return }
                if (!controller.gestureActive) return
                overlay.ghost = ghostOf(controller.updateGesture(x, y))
            }

            override fun mouseReleased(e: MouseEvent) {
                val x = e.x * scale(); val y = e.y * scale()
                if (controller.mode == UiEditorMode.PREVIEW) { presenter()?.touchUp(fit.x(x), fit.y(y)); return }
                if (controller.gestureActive) controller.endGesture(x, y)
                overlay.ghost = null
            }

            override fun mouseMoved(e: MouseEvent) {
                val x = e.x * scale(); val y = e.y * scale()
                if (controller.mode == UiEditorMode.PREVIEW) { presenter()?.pointerMoved(fit.x(x), fit.y(y)); return }
                overlay.hoverId = pick(x, y)
            }

            override fun mouseExited(e: MouseEvent) { overlay.hoverId = null }
        }
        target.addMouseListener(input)
        target.addMouseMotionListener(input)
    }

    /** The widget under ([x], [y]); any row stands for its template. */
    private fun pick(x: Float, y: Float): String? =
        controller.screenId?.let(layoutOf)?.let { hits.pick(it, x, y) }?.let(::uiBaseId)

    /** The drop target a drag would use now, as a frame in reference space. */
    private fun ghostOf(preview: UiGesturePreview): UiRect? {
        val layout = controller.screenId?.let(layoutOf) ?: return null
        return when (preview) {
            is UiGesturePreview.Move -> preview.bounds
            is UiGesturePreview.Reorder -> layout.nodes[preview.parentId]?.bounds
            is UiGesturePreview.Reparent -> layout.nodes[preview.parentId]?.bounds
            UiGesturePreview.None -> null
        }
    }
}
