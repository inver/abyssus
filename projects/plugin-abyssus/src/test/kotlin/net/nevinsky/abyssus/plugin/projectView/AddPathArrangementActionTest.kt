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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import java.io.File

/** Add Path Arrangement on a scene of a copy of the Untitled project. */
class AddPathArrangementActionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun copyProject() {
        myFixture.copyFileToProject("Untitled/Untitled.abss", "Untitled/Untitled.abss")
        myFixture.copyFileToProject("Untitled/scenes/Main Scene.scene", "Untitled/scenes/Main Scene.scene")
        File("$testDataPath/Untitled/assets").listFiles { f -> f.isDirectory }!!.forEach { d ->
            d.listFiles { f -> f.isFile && f.extension == "json" }!!.forEach {
                myFixture.copyFileToProject(
                    "Untitled/assets/${d.name}/${it.name}",
                    "Untitled/assets/${d.name}/${it.name}"
                )
            }
        }
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }
    private fun label(node: AbstractTreeNode<*>): String =
        (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    private fun abss() = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
    private fun sceneNode() = children(children(abss()).single { label(it) == "scenes" }).single()
    private fun scene() = viewableSceneFile(sceneNode())!!

    private class AddOn(val node: Any?) : AddPathArrangementAction() {
        override fun selected(e: AnActionEvent) = node
    }

    private fun visible(action: AnAction): Boolean {
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        return event.presentation.isEnabledAndVisible
    }

    fun testTheGroupListsTheProjectsModelsOnly() {
        copyProject()
        val chosen = mutableListOf<BaseMeta>()
        val items = AddPathArrangementGroup(project, scene()) { chosen += it }.getChildren(null)
        val names = items.map { it.templatePresentation.text }
        val expected = File("$testDataPath/Untitled/assets").listFiles { f -> File(f, "meta.json").isFile }!!
            .filter { Regex("\"type\"\\s*:\\s*\"MODEL\"").containsMatchIn(File(it, "meta.json").readText()) }
            .map { it.name }.sorted()
        assertEquals(expected, names)
        for (model in listOf(
            "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb", "model_828d51e4-8427-4769-bcb6-13f8f21f23e9",
            "model_900f6f61-6384-434a-be81-56ce303fbb56", "model_fc33e1f1-015b-4524-9b10-aa417acd273c", "tree",
        )) assertTrue(model, model in names)
        assertTrue(names.none { it.startsWith("terrain_") || it.startsWith("skybox") })
        val tree = items.first { it.templatePresentation.text == "tree" }
        tree.actionPerformed(TestActionEvent.createTestEvent(tree))
        assertEquals(listOf(BaseMeta(MetaType.MODEL, "tree")), chosen)
    }

    fun testItIsRegisteredBesideAddAssetAndOnlySceneRowsOfferIt() {
        copyProject()
        assertTrue(ActionManager.getInstance().getAction("Abyssus.AddPathArrangement") is AddPathArrangementAction)
        assertTrue(visible(AddOn(sceneNode())))
        assertFalse(visible(AddOn(abss())))
    }

    fun testAnUnreadableSceneDisablesIt() {
        copyProject()
        val file = scene()
        WriteCommandAction.runWriteCommandAction(project) {
            FileDocumentManager.getInstance().getDocument(file)!!.setText("{ not json")
        }
        assertFalse(visible(AddOn(sceneNode())))
        assertFalse(canAddPathArrangement(file, net.nevinsky.abyssus.plugin.dto.SceneDocumentCache.of(project)))
    }

    fun testASceneOutsideAProjectHasNoModels() {
        val loose = myFixture.addFileToProject(
            "loose/Alone.scene",
            """{"format":"abyssus","formatVersion":1,"ecs":{}}"""
        ).virtualFile
        assertTrue(AddPathArrangementGroup(project, loose) {}.getChildren(null).isEmpty())
        assertFalse(canAddPathArrangement(loose, net.nevinsky.abyssus.plugin.dto.SceneDocumentCache.of(project)))
    }
}
