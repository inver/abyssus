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
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.format.EntityType

/** OpenSpec `scene-dummies`, "Add Dummy", on a copy of `Untitled`. */
class AddDummyActionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private lateinit var sceneFile: com.intellij.openapi.vfs.VirtualFile

    private fun copyProject() {
        myFixture.copyFileToProject("Untitled/Untitled.abss", "Untitled/Untitled.abss")
        myFixture.copyFileToProject("Untitled/scenes/Main Scene.scene", "Untitled/scenes/Main Scene.scene")
        sceneFile = myFixture.findFileInTempDir("Untitled/scenes/Main Scene.scene")
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }

    private fun label(node: AbstractTreeNode<*>): String =
        (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    private fun descend(from: AbstractTreeNode<*>, vararg names: String) =
        names.fold(from) { node, name -> children(node).single { label(it) == name } }

    private fun sceneNode() =
        children(descend(children(AbyssusRootNode(project,
                ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }, "scenes"))
            .single()

    private fun entity(id: String) = descend(sceneNode(), "ecs", id)

    private class AddOn(val node: Any?) : AddDummyAction() {
        override fun selected(e: AnActionEvent) = node
    }

    private fun visible(action: AnAction): Boolean {
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        return event.presentation.isEnabledAndVisible
    }

    private fun document() = FileDocumentManager.getInstance().getDocument(sceneFile)!!

    private fun scene() = net.nevinsky.abyssus.plugin.parsedScene(document().text)

    private fun run(node: Any?) {
        val action = AddOn(node)
        action.actionPerformed(TestActionEvent.createTestEvent(action))
    }

    fun testActionIsRegisteredInTheAddGroup() {
        assertTrue(ActionManager.getInstance().getAction("Abyssus.AddDummy") is AddDummyAction)
    }

    fun testAddThenUndo() {
        copyProject()
        myFixture.openFileInEditor(sceneFile)
        val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val before = document().text
        run(entity("9"))
        val scene = scene()
        val id = scene.ecs.keys.max()
        assertEquals(11L, id)
        val dummy = scene.entity(id)!!
        assertEquals(EntityType.DUMMY, dummy.header().type)
        assertEquals("Dummy", dummy.component(NameComponent::class.java)!!.name)
        assertEquals(9, dummy.component(ParentComponent::class.java)!!.parentEntityId)
        // it is listed under Model 9
        assertEquals(listOf("11"), children(entity("9")).map(::label))
        UndoManager.getInstance(project).undo(editor)
        assertEquals(before, document().text)
    }

    fun testASecondDummyIsDummyTwo() {
        copyProject()
        run(entity("9"))
        run(entity("9"))
        val names = scene().ecs.values
            .filter { it.component(ParentComponent::class.java)?.parentEntityId == 9 }
            .map { it.component(NameComponent::class.java)!!.name }
        assertEquals(listOf("Dummy", "Dummy 2"), names)
    }

    fun testOfferedOnEntityRowsOnly() {
        copyProject()
        assertTrue(visible(AddOn(entity("9"))))
        assertFalse(visible(AddOn(descend(sceneNode(), "ecs"))))
        assertFalse(visible(AddOn(sceneNode())))
        assertFalse(visible(AddOn(null)))
    }
}
