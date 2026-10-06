/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import java.io.File

/** Add Component... on a scene's `ecs` row: a new entity holding the chosen component. */
class AddComponentOnEcsTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun copyProject(sceneFixture: String = "Tree/scenes/Main Scene.scene") {
        val dir = "Untitled"
        myFixture.copyFileToProject("Tree/Untitled.abss", "$dir/Untitled.abss")
        myFixture.copyFileToProject(sceneFixture, "$dir/scenes/Main Scene.scene")
        File("$testDataPath/Tree/assets").listFiles { f -> f.isDirectory }!!.forEach { d ->
            d.listFiles { f -> f.isFile && f.extension == "json" }!!.forEach {
                myFixture.copyFileToProject("Tree/assets/${d.name}/${it.name}", "$dir/assets/${d.name}/${it.name}")
            }
        }
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }
    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name
    private fun abss() = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
    private fun sceneNode() = children(children(abss()).single { label(it) == "scenes" }).single()
    private fun ecsRow() = children(sceneNode()).single { label(it) == "ecs" }
    private fun scene() = viewableSceneFile(sceneNode())!!
    private fun document() = FileDocumentManager.getInstance().getDocument(scene())!!
    private fun root() = SceneJson().parse(document().text)

    private class AddOn(val node: Any?) : AddComponentOnEcsAction() {
        override fun selected(e: AnActionEvent) = node
    }

    private fun visible(action: AnAction): Boolean {
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        return event.presentation.isEnabledAndVisible
    }

    private fun choices() = AddOn(null).choices(project, scene()).getChildren(null)

    fun testOfferedOnTheEcsRowOnly() {
        copyProject()
        assertTrue(visible(AddOn(ecsRow())))
        assertFalse(visible(AddOn(sceneNode())))
        assertFalse(visible(AddOn(children(ecsRow()).first())))
        assertTrue(ActionManager.getInstance().getAction("Abyssus.AddComponentOnEcs") is AddComponentOnEcsAction)
        val names = choices().map { it.templatePresentation.text }
        assertFalse("Name" in names)
        assertTrue("Camera" in names)
        assertTrue("Render" in names)
    }

    fun testCameraCreatesANamedEntityAndUndoRestoresTheFile() {
        copyProject()
        myFixture.openFileInEditor(scene())
        val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val before = document().text
        val beforeEntities = root()["ecs"].deepCopy<com.fasterxml.jackson.databind.JsonNode>()
        val camera = choices().first { it.templatePresentation.text == "Camera" }
        camera.actionPerformed(TestActionEvent.createTestEvent(camera))
        val ecs = root()["ecs"]
        val entity = ecs["9"]["components"]
        assertEquals("Entity 9", entity["NameComponent"]["name"].asText())
        assertTrue(entity.has("CameraComponent"))
        for ((id, old) in beforeEntities.properties()) assertEquals(old.toString(), ecs[id].toString())
        UndoManager.getInstance(project).undo(editor)
        assertEquals(before, document().text)
    }

    fun testRenderOffersTheProjectsAssets() {
        copyProject()
        val render = choices().filterIsInstance<ActionGroup>().single { it.templatePresentation.text == "Render" }
        val tree = render.getChildren(null).first { it.templatePresentation.text == "model tree" }
        tree.actionPerformed(TestActionEvent.createTestEvent(tree))
        val asset = root()["ecs"]["9"]["components"]["RenderComponent"]["renderable"]["asset"]
        assertEquals("tree", asset["assetName"].asText())
        assertEquals("MODEL", asset["type"].asText())
    }

    fun testAnEntityRowStillAddsToThatEntity() {
        copyProject()
        val entityRow = children(ecsRow()).first { componentTargetOf(it)?.entityId == "0" }
        val target = componentTargetOf(entityRow)!!
        val light = AddComponentAction().choices(project, target).getChildren(null).first { it.templatePresentation.text == "Light" }
        light.actionPerformed(TestActionEvent.createTestEvent(light))
        val ecs = root()["ecs"]
        assertTrue(ecs["0"]["components"].has("LightComponent"))
        assertNull("no new entity", ecs["9"])
    }

    fun testAnUnreadableSceneHidesIt() {
        copyProject()
        val row = ecsRow()
        WriteCommandAction.runWriteCommandAction(project) { document().setText("{ not json") }
        assertFalse(visible(AddOn(row)))
    }

    fun testAWrappedSceneGetsAMatchingArchetype() {
        copyProject("Lights/scenes/Creation Baseline.scene")
        assertTrue(visible(AddOn(ecsRow())))
        val camera = choices().first { it.templatePresentation.text == "Camera" }
        camera.actionPerformed(TestActionEvent.createTestEvent(camera))
        val ecs = root()["ecs"]
        val entity = ecs["entities"]["7"]
        val archetype = ecs["archetypes"][entity["archetype"].asText()].map { it.asText() }.toSet()
        assertEquals(setOf("NameComponent", "CameraComponent"), archetype)
    }
}
