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
import java.io.File
import net.nevinsky.abyssus.lib.core.editor.modellights.LampMaterialChoice
import net.nevinsky.abyssus.lib.core.editor.modellights.MaterialGeometry
import net.nevinsky.abyssus.lib.core.editor.modellights.lampsAt
import net.nevinsky.abyssus.lib.core.editor.modellights.materialGeometries
import com.intellij.openapi.project.Project

class CreateLightsFromModelActionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private lateinit var sceneFile: com.intellij.openapi.vfs.VirtualFile

    private fun copyProject() {
        val dir = "Untitled"
        myFixture.copyFileToProject("$dir/Untitled.abss", "$dir/Untitled.abss")
        myFixture.copyFileToProject("$dir/scenes/Main Scene.scene", "$dir/scenes/Main Scene.scene")
        sceneFile = myFixture.findFileInTempDir("$dir/scenes/Main Scene.scene")
        File("$testDataPath/$dir/assets")
            .listFiles { f -> f.isDirectory }!!
            .forEach { d ->
                d.listFiles { f -> f.isFile }!!
                    .filter { it.extension == "json" }
                    .forEach {
                        myFixture.copyFileToProject(
                            "$dir/assets/${d.name}/${it.name}",
                            "$dir/assets/${d.name}/${it.name}",
                        )
                    }
            }
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }

    private fun label(node: AbstractTreeNode<*>): String =
        (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    private fun descend(from: AbstractTreeNode<*>, vararg names: String) =
        names.fold(from) { node, name -> children(node).single { label(it) == name } }

    private fun abss() = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }

    private fun sceneNode() = children(descend(abss(), "scenes")).single()

    private fun entity(id: String) = descend(sceneNode(), "ecs", id)

    private class CreateOn(
        val node: Any?,
        val geometries: List<MaterialGeometry>? = materialGeometries(lampsAt(0f, 5f, 10f)),
        val pick: String? = "lamp",
    ) : CreateLightsFromModelAction() {
        var offered: List<LampMaterialChoice>? = null

        override fun selected(e: AnActionEvent) = node

        override fun readGeometry(dir: File, asset: String, meshId: String?) = geometries

        override fun choose(project: Project, choices: List<LampMaterialChoice>): String? {
            offered = choices
            return pick
        }
    }

    private fun visible(action: AnAction): Boolean {
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        return event.presentation.isEnabledAndVisible
    }

    private fun document() = FileDocumentManager.getInstance().getDocument(sceneFile)!!

    private fun ids() = net.nevinsky.abyssus.plugin.parsedScene(document().text).ecs.keys.toList()

    private fun run(action: CreateOn) = action.actionPerformed(TestActionEvent.createTestEvent(action))

    fun testActionIsRegistered() {
        val action = ActionManager.getInstance().getAction("Abyssus.CreateLightsFromModel")
        assertTrue(action is CreateLightsFromModelAction)
    }

    fun testOfferedOnAModelEntityOnly() {
        copyProject()
        assertTrue(visible(CreateOn(entity("0"))))
        assertFalse("a spot light", visible(CreateOn(entity("8"))))
        assertFalse("a terrain", visible(CreateOn(entity("1"))))
        assertFalse("a scene row", visible(CreateOn(sceneNode())))
        assertFalse(visible(CreateOn(null)))
    }

    fun testDisabledWhenTheModelAssetFolderIsMissing() {
        copyProject()
        // entity 9 names model_extra500, which has no asset folder
        val action = CreateOn(entity("9"))
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        assertTrue(event.presentation.isVisible)
        assertFalse(event.presentation.isEnabled)
    }

    fun testAddsOneLightPerLampAsOneUndoCommand() {
        copyProject()
        myFixture.openFileInEditor(sceneFile)
        val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val before = document().text
        val action = CreateOn(entity("0"))

        run(action)

        assertEquals("emissive material offered with its lamp count", listOf(3), action.offered!!.map { it.lamps })
        assertEquals((0L..13L).toList(), ids())
        UndoManager.getInstance(project).undo(editor)
        assertEquals("one Undo removes all the lights", before, document().text)
    }

    fun testCancelWritesNothing() {
        copyProject()
        val before = document().text
        val stamp = document().modificationStamp
        run(CreateOn(entity("0"), pick = null))
        assertEquals(before, document().text)
        assertEquals(stamp, document().modificationStamp)
    }
}
