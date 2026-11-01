/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.plugin.testMetaFiles
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.ecs.component.*
import com.badlogic.gdx.math.Vector3
import java.io.File

/** Add Asset on a scene of a copy of the Untitled project. */
class AddAssetActionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun copyProject() {
        val dir = "Untitled"
        myFixture.copyFileToProject("Tree/Untitled.abss", "$dir/Untitled.abss")
        myFixture.copyFileToProject("Tree/scenes/Main Scene.scene", "$dir/scenes/Main Scene.scene")
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
    private fun scene() = viewableSceneFile(sceneNode())!!
    private fun document() = FileDocumentManager.getInstance().getDocument(scene())!!
    private fun entity(id: Long) = net.nevinsky.abyssus.plugin.parsedScene(document().text).entity(id)!!

    private class AddOn(val node: Any?) : AddAssetAction() {
        override fun selected(e: AnActionEvent) = node
    }

    private fun visible(action: AnAction): Boolean {
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        return event.presentation.isEnabledAndVisible
    }

    private fun group(
        selected: (Long) -> Unit = {}, at: Vector3 = Vector3(0f, 0f, 0f), meshes: (String) -> List<String>? = { null },
    ) = AddAssetGroup(project, scene(), testMetaFiles(), { at }, selected, meshes).getChildren(null)

    fun testTheGroupListsTheProjectsModelsAndTerrainsOnly() {
        copyProject()
        val items = group()
        val sections = items.filterIsInstance<Separator>().map { it.text }
        assertEquals(listOf("Models", "Terrains"), sections)
        val names = items.filter { it !is Separator }.map { it.templatePresentation.text }
        // the fixture's own folders by meta type, so an asset added to the fixture does not break this test
        val byType = File("$testDataPath/Untitled/assets").listFiles { f -> File(f, "meta.json").isFile }!!
            .groupBy({ Regex("\"type\"\\s*:\\s*\"(\\w+)\"").find(File(it, "meta.json").readText())?.groupValues?.get(1) }, { it.name })
        val expected = byType["MODEL"].orEmpty().sorted() + byType["TERRAIN"].orEmpty().sorted()
        assertTrue("tree" in expected && "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b" in expected)
        assertEquals(expected, names)
        assertTrue(names.none { it.startsWith("skybox") })
        assertTrue(ActionManager.getInstance().getAction("Abyssus.AddAsset") is AddAssetAction)
    }

    fun testAddingAModelWritesTheNextEntityAndUndoRestoresTheScene() {
        copyProject()
        myFixture.openFileInEditor(scene())
        val editor = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val before = document().text
        var selected: Long? = null
        val tree = group({ selected = it }, Vector3(10f, 0f, -4f)).first { it.templatePresentation.text == "tree" }
        tree.actionPerformed(TestActionEvent.createTestEvent(tree))
        assertEquals(9L, selected)
        val entity = entity(9)
        assertEquals("Model 9", entity.component(NameComponent::class.java)!!.name)
        assertEquals("tree", entity.component(RenderComponent::class.java)!!.assetName)
        assertEquals(10f, entity.component(PositionComponent::class.java)!!.localPosition.x, 0f)
        UndoManager.getInstance(project).undo(editor)
        assertEquals(before, document().text)
    }

    fun testATerrainFromTheTreeIsCentredOnTheOrigin() {
        copyProject()
        val terrain = group().filter { it !is Separator }.first { it.templatePresentation.text.startsWith("terrain_") }
        terrain.actionPerformed(TestActionEvent.createTestEvent(terrain))
        val position = entity(9).component(PositionComponent::class.java)!!.localPosition
        assertEquals(-800f, position.x, 0f)
        assertEquals(-800f, position.z, 0f)
        assertEquals("Terrain 9", entity(9).component(NameComponent::class.java)!!.name)
    }

    fun testOnlySceneRowsOfferItAndAnUnreadableSceneDisablesIt() {
        copyProject()
        assertTrue(visible(AddOn(sceneNode())))
        assertFalse(visible(AddOn(abss())))
        WriteCommandAction.runWriteCommandAction(project) { document().setText("{ not json") }
        assertFalse(visible(AddOn(sceneNode())))
    }

    fun testASceneOutsideAProjectHasNothingToAdd() {
        val loose = myFixture.addFileToProject("loose/Alone.scene", """{"format":"abyssus","formatVersion":1,"ecs":{}}""").virtualFile
        assertFalse(hasRenderAssets(loose, testMetaFiles()))
        assertTrue(AddAssetGroup(project, loose, testMetaFiles(), { Vector3(0f, 0f, 0f) }).getChildren(null).isEmpty())
    }

    fun testAModelWithSeveralMeshesIsASubmenuOfItsMeshesAndTheWholeModel() {
        copyProject()
        val items = group(meshes = { if (it == "tree") listOf("body", "glass") else listOf("only") })
        val submenu = items.filterIsInstance<com.intellij.openapi.actionSystem.ActionGroup>().single()
        assertEquals("tree", submenu.templatePresentation.text)
        assertTrue(submenu.isPopup)
        val choices = submenu.getChildren(null).filter { it !is Separator }.map { it.templatePresentation.text }
        assertEquals(listOf("Whole model", "body", "glass"), choices)
        // a single-mesh model stays one plain entry
        val flat = items.filter { it !is Separator && it !is com.intellij.openapi.actionSystem.ActionGroup }.map { it.templatePresentation.text }
        assertFalse("tree" in flat)
        assertTrue(flat.any { it.startsWith("model_") })
    }

    fun testChoosingAMeshRecordsItsIdAndTheWholeModelEntryDoesNot() {
        copyProject()
        myFixture.openFileInEditor(scene())
        val editor = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val before = document().text
        var selected: Long? = null
        val submenu = group({ selected = it }, meshes = { if (it == "tree") listOf("body", "glass") else null })
            .filterIsInstance<com.intellij.openapi.actionSystem.ActionGroup>().single()
        val glass = submenu.getChildren(null).first { it.templatePresentation.text == "glass" }
        glass.actionPerformed(TestActionEvent.createTestEvent(glass))
        assertEquals(9L, selected)
        val render = entity(9).component(RenderComponent::class.java)!!
        assertEquals("tree", render.assetName)
        assertEquals("glass", render.meshId)
        UndoManager.getInstance(project).undo(editor)
        assertEquals(before, document().text)

        val whole = submenu.getChildren(null).first { it.templatePresentation.text == "Whole model" }
        whole.actionPerformed(TestActionEvent.createTestEvent(whole))
        assertNull(entity(9).component(RenderComponent::class.java)!!.meshId)
    }

    fun testAModelWhoseMeshesAreUnknownIsOneEntry() {
        copyProject()
        val items = group(meshes = { null })
        assertTrue(items.none { it is com.intellij.openapi.actionSystem.ActionGroup })
    }
}
