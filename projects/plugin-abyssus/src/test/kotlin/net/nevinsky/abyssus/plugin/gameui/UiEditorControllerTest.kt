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
import net.nevinsky.abyssus.lib.core.editor.ui.UiEditError
import net.nevinsky.abyssus.lib.core.editor.ui.UiGesturePreview
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutEngine
import net.nevinsky.abyssus.lib.core.ui.layout.UiMeasurer
import net.nevinsky.abyssus.lib.core.ui.layout.UiSizeF
import java.util.concurrent.Executor
import javax.swing.JPanel

class UiEditorControllerTest : BasePlatformTestCase() {
    private val source = SceneJson().pretty("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"entryScreen":"main","screens":[
        {"id":"main","name":"Main","root":{"id":"root","type":"panel","children":[
          {"id":"free","type":"button","layout":{"x":50,"y":40,"width":100,"height":40}},
          {"id":"list","type":"column","layout":{"x":400,"y":100,"width":200,"height":300,"spacing":10},"children":[
            {"id":"a","type":"label","layout":{"height":30}},{"id":"b","type":"label","layout":{"height":30}}]}]}},
        {"id":"other","name":"Other","root":{"id":"o-root","type":"panel"}}]}}}""")!!

    private lateinit var file: VirtualFile
    private val queue = ArrayDeque<Runnable>()
    private val uiQueue = ArrayDeque<Runnable>()

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1}""")
        file = myFixture.addFileToProject("p/assets/menus/meta.json", source).virtualFile
    }

    private fun controller(manual: Boolean = false): UiEditorController {
        val c = if (manual) UiEditorController(project, file, { queue.addLast(it) }, { uiQueue.addLast(it) })
        else UiEditorController(project, file, Executor { it.run() }, { it.run() })
        c.layoutProvider = { id ->
            c.collection?.screen(id)?.let { UiLayoutEngine(UiMeasurer { UiSizeF(100f, 20f) }).resolve(it, 1280f, 720f) }
        }
        c.reload()
        return c
    }

    private fun text() = FileDocumentManager.getInstance().getDocument(file)!!.text

    private fun ids(): List<String> {
        val out = mutableListOf<String>()
        fun walk(n: com.fasterxml.jackson.databind.JsonNode) { n.get("id")?.takeIf { it.isTextual }?.let { out += it.asText() }; n.get("children")?.forEach(::walk) }
        SceneJson().parse(text()).path("additional").path("ui").path("screens").get(0).get("root").let(::walk)
        return out
    }

    private fun setDocument(newText: String) = ApplicationManager.getApplication().runWriteAction { FileDocumentManager.getInstance().getDocument(file)!!.setText(newText) }

    fun testTheTreeCanvasAndPanelsShareOneSelection() {
        val c = controller()
        var changes = 0
        c.addListener { changes++ }
        assertEquals("main", c.screenId)
        c.select("a")
        assertEquals("a", c.selection.widgetId)
        assertEquals("a", c.selectedWidget!!.id)
        c.select("nope")
        assertEquals("selecting an unknown widget keeps the selection", "a", c.selection.widgetId)
        c.selectScreen("other")
        assertEquals("other", c.screenId)
        assertEquals("o-root", c.selection.widgetId)
        c.select("a")
        assertEquals("selecting a widget of another screen switches to that screen", "main", c.screenId)
        c.select(null)
        assertNull(c.selection.widgetId)
        assertTrue(changes >= 4)
    }

    fun testAddingDuplicatingAndDeletingKeepTheSelectionOnTheRightWidget() {
        val c = controller()
        c.select("list")
        assertTrue(c.addWidget("button"))
        assertEquals("button", c.selection.widgetId)
        assertEquals(listOf("root", "free", "list", "a", "b", "button"), ids())
        assertTrue(c.duplicateSelected())
        assertEquals("button-2", c.selection.widgetId)
        assertTrue(c.deleteSelected())
        assertEquals("the parent is selected after a delete", "list", c.selection.widgetId)
        c.select("free")
        assertTrue("adding next to a leaf goes into its parent", c.addWidget("label"))
        assertEquals(listOf("root", "free", "list", "a", "b", "button", "label"), ids())
        c.select("free"); assertFalse("the root cannot be deleted", c.select("root").let { c.deleteSelected() })
        assertEquals(UiEditError.ROOT_WIDGET, c.lastError)
    }

    fun testMovingAWidgetBetweenContainersIsOneCommandAndUndoRestoresTheExactText() {
        val c = controller()
        val before = text()
        c.select("free")
        assertTrue(c.moveSelected("list", 1))
        assertEquals(listOf("root", "list", "a", "free", "b"), ids())
        val editor = UiFileEditor(project, file, Executor { it.run() }) { JPanel() }
        UndoManager.getInstance(project).undo(editor)
        assertEquals("a single undo restores the whole move", before, text())
        editor.dispose()
    }

    fun testADragIsOneCommitOnReleaseAndNothingIsWrittenWhileDragging() {
        val c = controller()
        val before = text()
        val layout = c.layoutProvider("main")!!
        val fit = layout.fit!!
        val x = fit.toViewportX(60f); val y = fit.toViewportY(50f)
        c.beginGesture("free", x, y)
        assertTrue(c.gestureActive)
        repeat(10) { c.updateGesture(x + it * 5, y + it * 2) }
        assertEquals("a drag in progress writes nothing", before, text())
        assertTrue(c.updateGesture(x + 50, y + 30) is UiGesturePreview.Move)
        assertTrue(c.endGesture(x + 50, y + 30))
        assertFalse(c.gestureActive)
        val layoutNode = SceneJson().parse(text()).path("additional").path("ui").path("screens").get(0).path("root").path("children").get(0).path("layout")
        assertEquals(100, layoutNode.path("x").asInt()); assertEquals(70, layoutNode.path("y").asInt())
        val editor = UiFileEditor(project, file, Executor { it.run() }) { JPanel() }
        UndoManager.getInstance(project).undo(editor)
        assertEquals(before, text())
        editor.dispose()
    }

    fun testAClickIsNotADragAndAStaleDragIsRefused() {
        val c = controller()
        val layout = c.layoutProvider("main")!!
        val fit = layout.fit!!
        val x = fit.toViewportX(60f); val y = fit.toViewportY(50f)
        c.beginGesture("free", x, y)
        assertFalse("no movement, no command", c.endGesture(x + 1, y))
        assertEquals("free", c.selection.widgetId)
        c.beginGesture("free", x, y)
        c.updateGesture(x + 40, y + 40)
        setDocument(text().replace("\"spacing\": 10", "\"spacing\": 12")) // someone edits the text meanwhile
        assertFalse(c.endGesture(x + 40, y + 40))
        assertEquals(UiEditError.STALE, c.lastError)
        assertTrue(text().contains("\"spacing\": 12"))
        c.beginGesture("free", x, y); c.updateGesture(x + 40, y + 40); c.cancelGesture()
        assertFalse(c.endGesture(x + 40, y + 40))
    }

    fun testExternalTextEditsRefreshTheModelAndKeepOrRepairTheSelection() {
        val c = controller()
        c.select("b")
        setDocument(text().replace("\"id\": \"a\"", "\"id\": \"a2\""))
        c.reload()
        assertEquals("b", c.selection.widgetId)
        assertEquals("a2", c.collection!!.screens[0].root.children[1].children[0].id)
        setDocument(text().replace("\"id\": \"b\"", "\"id\": \"b2\""))
        c.reload()
        assertEquals("a deleted selection falls back to its screen's root", "root", c.selection.widgetId)
        setDocument("{ half typed")
        c.reload()
        assertTrue(c.state is UiEditorState.Unreadable)
        assertNull(c.selection.widgetId)
        assertFalse(c.addWidget("button"))
        setDocument(source)
        c.reload()
        assertTrue(c.state is UiEditorState.Editable)
    }

    fun testASlowOlderReadNeverOverwritesANewerOne() {
        val c = controller(manual = true) // the helper issues read #1 of the original text
        setDocument(text().replace("\"name\": \"Main\"", "\"name\": \"Renamed\""))
        c.reload()                       // read #2 of the edited text
        assertEquals(2, queue.size)
        queue.removeLast().run()         // the newer worker finishes first
        uiQueue.removeFirst().run()
        assertEquals("Renamed", c.collection!!.screens[0].name)
        queue.removeFirst().run()        // then the old one, which must be dropped
        uiQueue.removeFirst().run()
        assertEquals("Renamed", c.collection!!.screens[0].name)
    }

    fun testAResultAfterDisposalIsIgnored() {
        val c = controller(manual = true)
        c.dispose()
        queue.removeFirst().run()
        uiQueue.removeFirst().run()
        assertTrue("nothing was published", c.state is UiEditorState.Unreadable)
    }

    fun testScreensAreAddedRenamedAndDeleted() {
        val c = controller()
        assertTrue(c.addScreen("Pause", "overlay"))
        assertEquals("pause", c.screenId)
        assertTrue(c.renameScreen("pause", "Paused"))
        assertEquals("Paused", c.collection!!.screen("pause")!!.name)
        assertTrue(c.deleteScreen("pause"))
        assertEquals("the remaining screen is shown again", "main", c.screenId)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    fun testModeSwitchKeepsTheSelectionAndTheDocument() {
        val c = controller()
        c.select("a")
        val before = text()
        c.setMode(UiEditorMode.PREVIEW)
        assertEquals(UiEditorMode.PREVIEW, c.mode)
        c.setMode(UiEditorMode.EDIT)
        assertEquals("a", c.selection.widgetId)
        assertEquals(before, text())
    }
}
