/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.ui.UiGesturePreview
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutResult
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import net.nevinsky.abyssus.plugin.sceneview.GlHarness
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * The UI editor's canvas in a real GL 3.2 core context: it draws the asset through the shared presenter, adds the
 * selection frame in edit mode and not in preview, and follows a document change. Opens a window: runs only with
 * `-Dabyssus.glTests=true` and a display.
 */
class UiCanvasViewGlTest {
    @Before fun glOnly() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private val dir = Files.createTempDirectory("ui-canvas").toFile()
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
    private var window: JFrame? = null
    private var view: UiCanvasView? = null

    @After fun cleanUp() {
        SwingUtilities.invokeAndWait { view?.dispose(); window?.dispose() }
        dir.deleteRecursively()
    }

    private fun state(root: String): UiEditorState.Editable {
        val text = """{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"entryScreen":"main","screens":[
            {"id":"main","referenceSize":{"width":400,"height":300},"root":$root}]}}}"""
        return UiEditorState.Editable(UiCollectionDecoder().decode(SceneJson().parse(text)) as UiAdmission.Admitted, text)
    }

    private class FakeModel(override var state: UiEditorState, override var mode: UiEditorMode = UiEditorMode.EDIT) : UiCanvasModel {
        override var previewState: net.nevinsky.abyssus.lib.core.editor.ui.UiPreviewState? = null
        override var previewViewport = UiViewport(1280, 720)
        override var backdropScene: String? = null
        override var resourceRevision = 0L
        override val screenId = "main"
        override var selectedWidgetId: String? = null
        override val gestureActive = false
        override var layoutProvider: (String) -> UiLayoutResult? = { null }
        val listeners = mutableListOf<() -> Unit>()
        override fun addListener(listener: () -> Unit) { listeners += listener }
        override fun select(widgetId: String?) { selectedWidgetId = widgetId }
        override fun beginGesture(widgetId: String, x: Float, y: Float) { selectedWidgetId = widgetId }
        override fun updateGesture(x: Float, y: Float): UiGesturePreview = UiGesturePreview.None
        override fun endGesture(x: Float, y: Float) = false
        override fun deleteSelected() = false
        override fun duplicateSelected() = false
    }

    private val root = """{"id":"root","type":"panel","children":[
        {"id":"box","type":"button","layout":{"x":100,"y":100,"width":200,"height":100}},
        {"id":"red","type":"panel","layout":{"x":10,"y":10,"width":40,"height":40},"properties":{"styleOverrides":{"background":"#ff0000ff"}}}]}"""

    /** Pumps the AWT queue until [done] holds or five seconds pass. */
    private fun waitFor(what: String, done: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < end) {
            var ok = false
            SwingUtilities.invokeAndWait { ok = done() }
            if (ok) return
            Thread.sleep(40)
        }
        fail("timed out waiting for $what")
    }

    private fun open(model: FakeModel): UiCanvasView {
        SwingUtilities.invokeAndWait {
            val v = UiCanvasView(model, "menus", dir, json, net.nevinsky.abyssus.lib.core.ui.state.UiCollectionSource { null })
            view = v
            window = JFrame("Abyssus UI canvas test").apply { contentPane.add(v); setSize(800, 600); isVisible = true }
        }
        waitFor("the first drawn frame") { view!!.layoutOf("main") != null }
        return view!!
    }

    private fun snapshot(): BufferedImage {
        var image: BufferedImage? = null
        waitFor("a readable frame") { image = view!!.snapshot(); image != null }
        return image!!
    }

    /** The colour at the logical point ([x], [y]) of the 400x300 reference screen. */
    private fun at(image: BufferedImage, layout: UiLayoutResult, x: Float, y: Float): Int {
        val fit = layout.fit!!
        return image.getRGB(fit.toViewportX(x).toInt().coerceIn(0, image.width - 1), fit.toViewportY(y).toInt().coerceIn(0, image.height - 1)) and 0xffffff
    }

    /** Like [at], for a preview: the UI's point is mapped through the preview size, then through its fit into the panel. */
    private fun atPreview(canvas: UiCanvasView, image: BufferedImage, layout: UiLayoutResult, x: Float, y: Float): Int {
        val (scale, offsetX, offsetY) = canvas.previewSnapshotScale()
        val fit = layout.fit!!
        return image.getRGB((offsetX + fit.toViewportX(x) * scale).toInt().coerceIn(0, image.width - 1), (offsetY + fit.toViewportY(y) * scale).toInt().coerceIn(0, image.height - 1)) and 0xffffff
    }

    @Test fun editModeDrawsTheAssetWithASelectionFrameAndPreviewDrawsNone() {
        val model = FakeModel(state(root))
        val canvas = open(model)
        val layout = canvas.layoutOf("main")!!
        // the bundled button background, the authored red panel, and the background around the fitted screen
        waitFor("the button to be drawn") { at(canvas.snapshot()!!, layout, 200f, 110f) == 0x3a4a5c }
        val frameImage = snapshot()
        assertEquals("authored colour reaches the screen", 0xff0000, at(frameImage, layout, 30f, 30f))
        assertEquals("the editor background fills the rest", 0x1c1f24, frameImage.getRGB(2, 2) and 0xffffff)
        assertNotEquals("nothing is selected yet", 0x2d8cff, at(frameImage, layout, 101f, 150f))
        // selecting a widget draws the 2-unit frame (scaled) on its left edge
        model.selectedWidgetId = "box"
        waitFor("the selection frame") { at(canvas.snapshot()!!, layout, 100f, 150f) == 0x2d8cff }
        // preview shows the plain UI
        model.mode = UiEditorMode.PREVIEW
        model.listeners.forEach { it() }
        waitFor("preview without the frame") { atPreview(canvas, canvas.snapshot()!!, layout, 100f, 150f) != 0x2d8cff }
        assertEquals(0x3a4a5c, atPreview(canvas, snapshot(), layout, 200f, 110f))
    }

    @Test fun aClickPicksTheWidgetUnderItAtAReducedZoom() {
        val model = FakeModel(state(root))
        val canvas = open(model)
        SwingUtilities.invokeAndWait { window!!.setSize(260, 220) }
        waitFor("a smaller layout") { canvas.layoutOf("main")?.fit?.scale?.let { it < 1f } == true }
        val layout = canvas.layoutOf("main")!!
        val fit = layout.fit!!
        fun gl(c: java.awt.Container): java.awt.Component? = c.components.firstNotNullOfOrNull { if (it is net.nevinsky.abyssus.plugin.sceneview.GuardedGLCanvas) it else (it as? java.awt.Container)?.let(::gl) }
        val surface = checkNotNull(gl(canvas)) { "no GL surface" }
        // the framebuffer is not scaled here, so AWT pixels are viewport pixels
        val px = fit.toViewportX(200f).toInt()
        val py = fit.toViewportY(150f).toInt()
        SwingUtilities.invokeAndWait {
            surface.dispatchEvent(java.awt.event.MouseEvent(surface, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, px, py, 1, false, java.awt.event.MouseEvent.BUTTON1))
        }
        assertEquals("box", model.selectedWidgetId)
    }

    @Test fun aChangedDocumentIsDrawnWithoutRestartingTheCanvas() {
        val model = FakeModel(state(root))
        val canvas = open(model)
        val layout = canvas.layoutOf("main")!!
        waitFor("red") { at(canvas.snapshot()!!, layout, 30f, 30f) == 0xff0000 }
        model.state = state(root.replace("#ff0000ff", "#00ff00ff"))
        model.listeners.forEach { it() }
        waitFor("green") { at(canvas.snapshot()!!, canvas.layoutOf("main")!!, 30f, 30f) == 0x00ff00 }
        // a refused document leaves the last good frame's presenter gone, not a crash
        model.state = UiEditorState.Unreadable("x", "")
        waitFor("a cleared frame") { at(canvas.snapshot()!!, layout, 30f, 30f) == 0x1c1f24 }
    }

    // ---- preview ----

    private fun previewOf(model: FakeModel) {
        model.previewState = net.nevinsky.abyssus.lib.core.editor.ui.UiPreviewState.Factory().of((model.state as UiEditorState.Editable).collection, "main")
    }

    private fun png(file: java.io.File, rgb: Int) {
        file.parentFile.mkdirs()
        javax.imageio.ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB).also { img -> for (y in 0 until 4) for (x in 0 until 4) img.setRGB(x, y, rgb) }, "png", file)
    }

    private val previewRoot = """{"id":"root","type":"panel","children":[
        {"id":"red","type":"panel","layout":{"x":0,"y":0,"width":100,"height":100},"properties":{"styleOverrides":{"background":"#ff0000ff"}}}]}"""

    @Test fun thePreviewIsLaidOutForItsOwnSizeWhateverThePanelIs() {
        val model = FakeModel(state(previewRoot), UiEditorMode.PREVIEW).also { it.previewViewport = UiViewport(800, 600) }
        previewOf(model)
        val canvas = open(model)
        // the 400x300 reference screen fills the 800x600 preview exactly: scale 2, no letterbox, in any panel size
        waitFor("a laid out preview") { canvas.layoutOf("main")?.fit?.scale == 2f }
        assertEquals(0f, canvas.layoutOf("main")!!.fit!!.offsetX, 0f)
        val layout = canvas.layoutOf("main")!!
        waitFor("the picture") { at(canvas.snapshot()!!, layout, 0f, 0f).let { true } }
        // a smaller preview of the same panel changes the layout, not the picture size on screen
        model.previewViewport = UiViewport(400, 300)
        waitFor("scale 1") { canvas.layoutOf("main")?.fit?.scale == 1f }
        // the picture is shown fitted into the panel (aspect kept): its centre is the red-free reference centre
        val image = snapshot()
        assertEquals(0x0d0f14, image.getRGB(image.width / 2, image.height / 2) and 0xffffff)
    }

    @Test fun resetSendsThePreviewBackAndAChangedImageFileIsReloaded() {
        png(java.io.File(dir, "images/pic.png"), 0xff0000)
        val withImage = """{"id":"root","type":"panel","children":[
            {"id":"pic","type":"image","layout":{"x":0,"y":0,"width":100,"height":100},"properties":{"image":"pic"}}]}"""
        val text = """{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"entryScreen":"main",
            "resources":[{"id":"pic","kind":"image","path":"images/pic.png"}],
            "screens":[{"id":"main","referenceSize":{"width":400,"height":300},"root":$withImage}]}}}"""
        val model = FakeModel(UiEditorState.Editable(UiCollectionDecoder().decode(SceneJson().parse(text)) as UiAdmission.Admitted, text), UiEditorMode.PREVIEW)
        previewOf(model)
        val canvas = open(model)
        val layout = canvas.layoutOf("main")!!
        fun preview(): Int = canvas.snapshot()!!.let { img -> val fit = layout.fit!!; val s = canvas.previewSnapshotScale(); img.getRGB(((fit.toViewportX(50f) * s.first + s.second).toInt()).coerceIn(0, img.width - 1), ((fit.toViewportY(50f) * s.first + s.third).toInt()).coerceIn(0, img.height - 1)) and 0xffffff }
        waitFor("the red image") { preview() == 0xff0000 }
        png(java.io.File(dir, "images/pic.png"), 0x00ff00)
        model.resourceRevision++
        waitFor("the green image after the file changed") { preview() == 0x00ff00 }
        // a reset returns to the start screen and keeps drawing
        model.previewState!!.reset()
        waitFor("still drawing after a reset") { preview() == 0x00ff00 }
    }

    private class RecordingBackdrop(val color: Triple<Float, Float, Float>) : UiBackdrop {
        var disposed = false
        override fun render(width: Int, height: Int, delta: Float) {
            com.badlogic.gdx.Gdx.gl.glClearColor(color.first, color.second, color.third, 1f)
            com.badlogic.gdx.Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)
        }
        override fun dispose() { disposed = true }
    }

    @Test fun aSceneBackdropIsDrawnBehindThePreviewAndAFailingOneIsReportedNotFatal() {
        val model = FakeModel(state(previewRoot), UiEditorMode.PREVIEW)
        previewOf(model)
        model.backdropScene = "Main.scene"
        val made = mutableListOf<RecordingBackdrop>()
        var problem: String? = "unset"
        SwingUtilities.invokeAndWait {
            val v = UiCanvasView(model, "menus", dir, json, net.nevinsky.abyssus.lib.core.ui.state.UiCollectionSource { null }) { name ->
                if (name == "Broken.scene") error("cannot read $name") else RecordingBackdrop(Triple(0f, 0f, 1f)).also { made += it }
            }
            v.onBackdropProblem = { problem = it }
            view = v
            window = JFrame("Abyssus UI canvas test").apply { contentPane.add(v); setSize(800, 600); isVisible = true }
        }
        waitFor("the first drawn frame") { view!!.layoutOf("main") != null }
        val layout = view!!.layoutOf("main")!!
        waitFor("the backdrop colour around the UI") { view!!.snapshot()?.let { atPreview(view!!, it, layout, 300f, 250f) == 0x0000ff } == true }
        assertEquals("the UI still draws over it", 0xff0000, view!!.snapshot()!!.let { atPreview(view!!, it, layout, 20f, 20f) })
        model.backdropScene = "Broken.scene"
        waitFor("the problem to be reported") { problem?.contains("cannot read") == true }
        waitFor("the preview to carry on without a backdrop") { view!!.snapshot()?.let { atPreview(view!!, it, layout, 300f, 250f) != 0x0000ff } == true }
        assertTrue("the first backdrop was disposed when the choice changed", made.single().disposed)
    }

    @Test fun closingTheCanvasReleasesItsGlObjects() {
        val model = FakeModel(state(previewRoot), UiEditorMode.PREVIEW)
        previewOf(model)
        val canvas = open(model)
        waitFor("a drawn frame") { canvas.snapshot() != null }
        assertFalse(canvas.released)
        SwingUtilities.invokeAndWait { canvas.dispose(); window!!.contentPane.removeAll() }
        waitFor("the release") { canvas.released }
    }

    @Test fun editModeDrawsSampleRowsAndFramesEveryCopyOfTheSelectedTemplate() {
        val text = """{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"entryScreen":"main",
            "bindings":[{"name":"scores","type":"list","fields":[{"name":"n","type":"number"}],"sample":[{"n":1},{"n":2},{"n":3}]}],
            "screens":[{"id":"main","referenceSize":{"width":400,"height":300},"root":{"id":"root","type":"panel","children":[
              {"id":"table","type":"list","layout":{"x":10,"y":10,"width":100,"spacing":2},"properties":{"maxRows":2},"bindings":{"items":"scores"},
               "template":{"id":"row","type":"panel","layout":{"width":100,"height":20},"properties":{"styleOverrides":{"background":"#ff0000ff"}}}}]}}]}}}"""
        val model = FakeModel(UiEditorState.Editable(UiCollectionDecoder().decode(SceneJson().parse(text)) as UiAdmission.Admitted, text))
        val canvas = open(model)
        val layout = canvas.layoutOf("main")!!
        waitFor("both sample rows") { canvas.snapshot()?.let { at(it, layout, 50f, 20f) == 0xff0000 && at(it, layout, 50f, 42f) == 0xff0000 } == true }
        assertNotEquals("maxRows 2: no third row", 0xff0000, at(snapshot(), layout, 50f, 64f))
        model.selectedWidgetId = "row"
        waitFor("a frame on the first row") { canvas.snapshot()?.let { at(it, layout, 10f, 20f) == 0x2d8cff } == true }
        assertEquals("and on the second", 0x2d8cff, at(snapshot(), layout, 10f, 42f))
        assertEquals("the rows are ids of copies; the template itself has no node of its own", listOf("row#0", "row#1"), layout.order.filter { it.startsWith("row") })
    }
}

