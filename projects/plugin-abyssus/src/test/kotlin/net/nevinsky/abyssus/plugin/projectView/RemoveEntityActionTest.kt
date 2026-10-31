/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import java.io.File

class RemoveEntityActionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private lateinit var sceneFile: com.intellij.openapi.vfs.VirtualFile

    private fun copyProject() {
        val dir = "Untitled"
        myFixture.copyFileToProject("$dir/Untitled.abss", "$dir/Untitled.abss")
        myFixture.copyFileToProject("$dir/scenes/Main Scene.scene", "$dir/scenes/Main Scene.scene")
        sceneFile = myFixture.findFileInTempDir("$dir/scenes/Main Scene.scene")
        File("$testDataPath/$dir/assets").listFiles { f -> f.isDirectory }!!.forEach { d ->
            d.listFiles { f -> f.isFile }!!.filter { it.extension == "json" }.forEach {
                myFixture.copyFileToProject("$dir/assets/${d.name}/${it.name}", "$dir/assets/${d.name}/${it.name}")
            }
        }
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }
    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name
    private fun descend(from: AbstractTreeNode<*>, vararg names: String) =
        names.fold(from) { node, name -> children(node).single { label(it) == name } }

    private fun abss() = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
    private fun sceneNode() = children(descend(abss(), "scenes")).single()
    private fun entity(id: String) = descend(sceneNode(), "ecs", id)

    private class RemoveOn(val node: Any?) : RemoveEntityAction() {
        override fun selected(e: AnActionEvent) = node
    }

    private fun visible(action: AnAction): Boolean {
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        return event.presentation.isEnabledAndVisible
    }

    private fun scene() = sceneFile
    private fun document() = FileDocumentManager.getInstance().getDocument(scene())!!
    private fun ids() = SceneJson().parse(document().text)["ecs"].fieldNames().asSequence().toList()

    /** Performs Remove Entity on [node]; returns the refusal messages it showed. */
    private fun run(node: Any?): List<String> {
        val dialogs = mutableListOf<String>()
        val previous = com.intellij.openapi.ui.TestDialogManager.setTestDialog { message -> dialogs += message; 0 }
        try {
            val action = RemoveOn(node)
            action.actionPerformed(TestActionEvent.createTestEvent(action))
        } finally {
            com.intellij.openapi.ui.TestDialogManager.setTestDialog(previous)
        }
        return dialogs
    }

    fun testActionIsRegistered() {
        assertTrue(ActionManager.getInstance().getAction("Abyssus.RemoveEntity") is RemoveEntityAction)
    }

    fun testOfferedOnEntityRowsOnly() {
        copyProject()
        assertTrue(visible(RemoveOn(entity("0"))))
        assertFalse(visible(RemoveOn(descend(entity("0"), "NameComponent"))))
        assertFalse(visible(RemoveOn(descend(sceneNode(), "ecs"))))
        assertFalse(visible(RemoveOn(sceneNode())))
        assertFalse(visible(RemoveOn(abss())))
        assertFalse(visible(RemoveOn(descend(abss(), "assets"))))
        assertFalse(visible(RemoveOn(null)))
    }

    fun testRemovesOnlyTheEntityAsOneUndoCommandAndKeepsAssets() {
        copyProject()
        val file = scene()
        myFixture.openFileInEditor(file)
        val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val before = document().text
        val assetsBefore = myFixture.findFileInTempDir("Untitled/assets").children.map { it.name }.sorted()

        run(entity("0"))
        assertFalse("0" in ids())
        assertEquals((0..10).map(Int::toString) - "0", ids())
        assertEquals(assetsBefore, myFixture.findFileInTempDir("Untitled/assets").children.map { it.name }.sorted())

        UndoManager.getInstance(project).undo(editor)
        assertEquals("one Undo restores the exact text", before, document().text)
        UndoManager.getInstance(project).redo(editor)
        assertFalse("0" in ids())
        assertEquals((0..10).map(Int::toString) - "0", ids())
    }

    /** The tree has focus, not a text editor: Undo and Redo of the removal must still reach it (EntityLifecycleUiTest). */
    fun testUndoAndRedoReachTheRemovalWithoutAnEditor() {
        copyProject()
        val before = document().text
        run(entity("0"))
        val removed = document().text
        val undo = UndoManager.getInstance(project)
        assertTrue("Undo is available with no editor focused", undo.isUndoAvailable(null))
        undo.undo(null)
        assertEquals(before, document().text)
        assertTrue("Redo is available with no editor focused", undo.isRedoAvailable(null))
        undo.redo(null)
        assertEquals(removed, document().text)
    }

    fun testReferencedEntityIsRefusedWithoutAnEdit() {
        copyProject()
        val before = document().text
        val modCount = document().modificationStamp
        val dialogs = run(entity("3"))
        assertEquals(before, document().text)
        assertEquals(modCount, document().modificationStamp)
        assertEquals(1, dialogs.size)
        assertTrue(dialogs.single(), dialogs.single().contains("entity 4") && dialogs.single().contains("lookAtId"))
    }

    fun testStaleTargetWritesNothing() {
        copyProject()
        val node = entity("0")
        val file = scene()
        runWriteAction {
            document().setText(document().text.replaceFirst("\"0\":", "\"x0\":"))
            FileDocumentManager.getInstance().saveDocument(document())
        }
        val text = document().text
        assertEquals(1, run(node).size)
        assertEquals(text, document().text)
        assertTrue(file.isValid)
    }

    fun testUnsavedTextIsRespectedAndRestoredByUndo() {
        copyProject()
        myFixture.openFileInEditor(scene())
        val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        runWriteAction { document().setText(document().text.replaceFirst("\"formatVersion\": 1", "\"formatVersion\": 1,\n  \"unrelated\": 1.000")) }
        val unsaved = document().text
        run(entity("0"))
        assertTrue(document().text.contains("\"unrelated\": 1.000"))
        UndoManager.getInstance(project).undo(editor)
        assertEquals(unsaved, document().text)
    }

    fun testUnsupportedNativeMarkersAreNotEdited() {
        copyProject()
        val node = entity("0")
        runWriteAction {
            document().setText(document().text.replaceFirst("\"formatVersion\": 1", "\"formatVersion\": 2"))
        }
        val text = document().text
        assertEquals(1, run(node).size)
        assertEquals(text, document().text)
        assertFalse("no undo step was created", UndoManager.getInstance(project).isUndoAvailable(null))
    }
}
