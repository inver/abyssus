/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.ui.SampleProblem
import net.nevinsky.abyssus.lib.core.editor.ui.SampleResult
import net.nevinsky.abyssus.lib.core.ui.state.UiValue
import java.awt.image.BufferedImage
import java.util.concurrent.Executor
import javax.imageio.ImageIO
import javax.swing.JPanel
import javax.swing.JTextField

class UiPreviewControllerTest : BasePlatformTestCase() {
    private val source = SceneJson().pretty("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"entryScreen":"main",
        "bindings":[{"name":"volume","type":"number","access":"write","sample":0.5,"min":0,"max":1},{"name":"name","type":"string","access":"write","sample":"Pilot"}],
        "screens":[{"id":"main","name":"Main","root":{"id":"root","type":"panel","children":[{"id":"vol","type":"slider","bindings":{"value":"volume"}}]}},
                   {"id":"pause","name":"Pause","kind":"overlay","root":{"id":"p","type":"panel"}}]}}}""")!!

    private lateinit var file: VirtualFile
    private val queue = ArrayDeque<Runnable>()
    private val uiQueue = ArrayDeque<Runnable>()

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1}""")
        myFixture.addFileToProject("p/scenes/Main.scene", """{"format":"abyssus","formatVersion":1,"ecs":{}}""")
        file = myFixture.addFileToProject("p/assets/menus/meta.json", source).virtualFile
    }

    private fun controller(prefs: UiPreferences = MemoryUiPreferences(), manual: Boolean = false): UiEditorController {
        val c = if (manual) UiEditorController(project, file, { queue.addLast(it) }, { uiQueue.addLast(it) }, preferences = prefs)
        else UiEditorController(project, file, Executor { it.run() }, { it.run() }, preferences = prefs)
        c.reload()
        return c
    }

    private val document get() = FileDocumentManager.getInstance().getDocument(file)!!

    private fun setText(text: String) = ApplicationManager.getApplication().runWriteAction { document.setText(text) }

    fun testPreviewExistsOnlyInPreviewModeAndStartsOnTheSelectedScreen() {
        val c = controller()
        assertNull("edit mode has no preview world", c.previewState)
        c.selectScreen("pause")
        c.setMode(UiEditorMode.PREVIEW)
        val state = c.previewState!!
        assertEquals("pause", state.startScreen)
        assertEquals(UiValue.Num(0.5), state.samples["volume"])
        c.setMode(UiEditorMode.EDIT)
        c.setMode(UiEditorMode.PREVIEW)
        assertSame("going back and forth keeps the same session", state, c.previewState)
    }

    fun testPreviewUsesUnsavedTextAndNeverChangesTheDocumentOrUndoHistory() {
        val c = controller()
        setText(source.replace("\"sample\": 0.5", "\"sample\": 0.25"))
        c.reload()
        assertTrue("the document is unsaved", FileDocumentManager.getInstance().isDocumentUnsaved(document))
        c.setMode(UiEditorMode.PREVIEW)
        assertEquals(UiValue.Num(0.25), c.previewState!!.samples["volume"])
        val stamp = document.modificationStamp
        val text = document.text
        val editor = UiFileEditor(project, file, Executor { it.run() }) { JPanel() }
        val undoBefore = UndoManager.getInstance(project).isUndoAvailable(editor)
        val state = c.previewState!!
        state.setSample("volume", "0.9"); state.actionSink().emit("startGame"); state.writer()
        c.setPreviewViewport(1920, 1080); c.setBackdropScene("Main.scene"); c.resetPreview()
        c.select("vol"); c.setMode(UiEditorMode.EDIT)
        assertEquals("nothing the preview did reached the document", text, document.text)
        assertEquals(stamp, document.modificationStamp)
        assertEquals("and the authoring selection is back", "vol", c.selection.widgetId)
        assertEquals("the preview added nothing to the undo history", undoBefore, UndoManager.getInstance(project).isUndoAvailable(editor))
        editor.dispose()
    }

    fun testInvalidTextSuspendsThePreviewAndRecoveryStartsAFreshOne() {
        val c = controller()
        c.setMode(UiEditorMode.PREVIEW)
        val first = c.previewState!!
        first.setSample("volume", "0.9")
        setText("{ not json")
        c.reload()
        assertNull("no preview runs on invalid text", c.previewState)
        assertTrue(c.state is UiEditorState.Unreadable)
        setText(source)
        c.reload()
        val second = c.previewState!!
        assertNotSame(first, second)
        assertEquals("the new session starts from the authored samples", UiValue.Num(0.5), second.samples["volume"])
    }

    fun testADefinitionChangeResetsTheSamplesAndTheLog() {
        val c = controller()
        c.setMode(UiEditorMode.PREVIEW)
        val before = c.previewState!!
        before.setSample("volume", "0.9"); before.actionSink().emit("x")
        assertTrue(c.edit("t") { e, m -> e.setProperty(m, "vol", "marker", com.fasterxml.jackson.databind.node.BooleanNode.TRUE) })
        val after = c.previewState!!
        assertNotSame(before, after)
        assertEquals(UiValue.Num(0.5), after.samples["volume"])
        assertTrue(after.log.isEmpty())
        assertEquals("a reload of the same text keeps the session", after, run { c.reload(); c.previewState })
    }

    fun testResetKeepsTheSessionObjectButReturnsToAuthoredValues() {
        val c = controller()
        c.setMode(UiEditorMode.PREVIEW)
        val state = c.previewState!!
        state.setSample("name", "Ace"); state.actionSink().emit("go")
        c.resetPreview()
        assertEquals(UiValue.Text("Pilot"), state.samples["name"])
        assertTrue(state.log.isEmpty())
        assertEquals(1, state.generation)
    }

    fun testTheViewportMustBePositiveAndWithinTheLimitAndIsRememberedPerViewer() {
        val prefs = MemoryUiPreferences()
        val c = controller(prefs)
        assertEquals(UiViewport(1280, 720), c.previewViewport)
        for ((w, h) in listOf(0 to 720, 1280 to 0, -5 to 5, 5000 to 100, 100 to MAX_PREVIEW_SIDE + 1)) {
            assertFalse("$w x $h", c.setPreviewViewport(w, h))
            assertEquals(UiViewport(1280, 720), c.previewViewport)
        }
        assertTrue(c.setPreviewViewport(1024, 768))
        assertTrue(c.setPreviewViewport(MAX_PREVIEW_SIDE, 1))
        c.setPreviewViewport(1024, 768)
        assertEquals("a new editor of the same asset starts where this one left off", UiViewport(1024, 768), controller(prefs).previewViewport)
        assertEquals("another viewer (empty preferences) starts at the default", UiViewport(1280, 720), controller().previewViewport)
        prefs.set("abyssus.ui.${file.path}.viewport", "garbage")
        assertEquals("a damaged preference is ignored", UiViewport(1280, 720), controller(prefs).previewViewport)
    }

    fun testTheBackdropChoiceIsAnEditorPreferenceNotADocumentField() {
        val prefs = MemoryUiPreferences()
        val c = controller(prefs)
        val text = document.text
        c.setBackdropScene("Main.scene")
        assertEquals("Main.scene", c.backdropScene)
        assertEquals("Main.scene", controller(prefs).backdropScene)
        c.setBackdropScene(null)
        assertNull(controller(prefs).backdropScene)
        assertEquals(text, document.text)
        assertEquals("the scene is untouched", """{"format":"abyssus","formatVersion":1,"ecs":{}}""",
            String(file.parent.parent.parent.findChild("scenes")!!.findChild("Main.scene")!!.contentsToByteArray()))
    }

    fun testAFileOfTheAssetChangingOnDiskBumpsTheResourceRevisionButItsMetaDoesNot() {
        val editor = UiFileEditor(project, file, Executor { it.run() }) { JPanel() }
        val c = editor.controller
        val start = c.resourceRevision
        ApplicationManager.getApplication().runWriteAction {
            val images = file.parent.createChildDirectory(this, "images")
            val png = images.createChildData(this, "logo.png")
            val out = java.io.ByteArrayOutputStream()
            ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", out)
            png.setBinaryContent(out.toByteArray())
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertTrue("a new image is a resource change", c.resourceRevision > start)
        val afterImage = c.resourceRevision
        setText(document.text.replace("\"Main\"", "\"Renamed\""))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals("editing meta.json is not", afterImage, c.resourceRevision)
        editor.dispose()
    }

    fun testASlowOlderReadNeverReplacesTheCurrentPreview() {
        val c = controller(manual = true)       // read #1 queued
        queue.removeFirst().run(); uiQueue.removeFirst().run()
        c.setMode(UiEditorMode.PREVIEW)
        val live = c.previewState!!
        setText(source.replace("\"sample\": 0.5", "\"sample\": 0.75"))
        c.reload()                              // read #2 of the newer text
        setText(source)
        c.reload()                              // read #3 of the text back as before
        assertEquals(2, queue.size)
        val second = queue.removeFirst(); val third = queue.removeFirst()
        third.run(); uiQueue.removeLast().run() // the newest finishes first
        second.run(); uiQueue.removeLast().run() // the stale one must be dropped
        assertEquals(0.5, (c.previewState!!.samples["volume"] as UiValue.Num).value, 0.0)
        assertNotNull(live)
    }

    fun testClosingTheTabDropsLateResultsAndTheListeners() {
        val editor = UiFileEditor(project, file, Executor { queue.addLast(it) }) { JPanel() }
        editor.dispose()
        // a worker finishing after the tab closed publishes nothing
        while (queue.isNotEmpty()) queue.removeFirst().run()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertTrue(editor.controller.state is UiEditorState.Unreadable)
    }

    // ---- the panel ----

    private fun panel(c: UiEditorController) = UiPreviewPanel(c) { listOf("Main.scene", "Other.scene") }

    fun testThePanelOffersSizesBackdropsAndRejectsBadCustomSizes() {
        val c = controller()
        val p = panel(c)
        p.widthField.text = "800"; p.heightField.text = "600"
        findButton(p, "preview-apply").doClick()
        assertEquals(UiViewport(800, 600), c.previewViewport)
        p.widthField.text = "0"
        findButton(p, "preview-apply").doClick()
        assertEquals("a bad size is refused and the old one stays", UiViewport(800, 600), c.previewViewport)
        p.widthField.text = "abc"
        findButton(p, "preview-apply").doClick()
        assertEquals(UiViewport(800, 600), c.previewViewport)
        val backdrop = find<javax.swing.JComboBox<String>>(p, "preview-backdrop")
        assertEquals(listOf("(none)", "Main.scene", "Other.scene"), (0 until backdrop.itemCount).map { backdrop.getItemAt(it) })
        backdrop.selectedItem = "Other.scene"
        assertEquals("Other.scene", c.backdropScene)
        backdrop.selectedItem = "(none)"
        assertNull(c.backdropScene)
    }

    fun testSampleEditorsShowRejectionsAndTheLogAndResetWork() {
        val c = controller()
        val p = panel(c)
        assertTrue("without a preview there is only the hint", p.sampleFields.isEmpty())
        c.setMode(UiEditorMode.PREVIEW)
        val volume = p.sampleFields.getValue("volume")
        volume.text = "5"; volume.postActionEvent()
        assertTrue(p.sampleStatus.getValue("volume").text.contains("maximum"))
        assertEquals("the rejected value left the sample", UiValue.Num(0.5), c.previewState!!.samples["volume"])
        volume.text = "0.2"; volume.postActionEvent()
        assertEquals(UiValue.Num(0.2), c.previewState!!.samples["volume"])
        assertEquals(SampleResult.Rejected(SampleProblem.NOT_A_NUMBER), c.previewState!!.setSample("volume", "x"))
        c.previewState!!.actionSink().emit("startGame")
        p.refreshLive()
        assertTrue(p.log.model.size >= 2)
        assertTrue((0 until p.log.model.size).any { p.log.model.getElementAt(it).contains("startGame") })
        findButton(p, "preview-reset").doClick()
        p.refreshLive()
        assertEquals(0, p.log.model.size)
        assertEquals("0.5", p.sampleFields.getValue("volume").text)
    }

    private fun <T> find(root: java.awt.Container, name: String): T {
        fun walk(c: java.awt.Container): java.awt.Component? = c.components.firstNotNullOfOrNull { ch -> if (ch.name == name) ch else (ch as? java.awt.Container)?.let(::walk) }
        @Suppress("UNCHECKED_CAST") return checkNotNull(walk(root)) { name } as T
    }

    private fun findButton(root: java.awt.Container, name: String): javax.swing.JButton = find(root, name)

    fun testThePreviewChoicesSurviveClosingAndReopeningTheTab() {
        val first = UiFileEditor(project, file, Executor { it.run() }) { JPanel() }
        assertTrue(first.controller.setPreviewViewport(1920, 1080))
        first.controller.setBackdropScene("Main.scene")
        first.dispose()
        val second = UiFileEditor(project, file, Executor { it.run() }) { JPanel() }
        assertEquals(UiViewport(1920, 1080), second.controller.previewViewport)
        assertEquals("Main.scene", second.controller.backdropScene)
        // and they are not in the document
        assertFalse(document.text.contains("1920"))
        second.controller.setBackdropScene(null)
        second.controller.setPreviewViewport(1280, 720)
        second.dispose()
    }
}
