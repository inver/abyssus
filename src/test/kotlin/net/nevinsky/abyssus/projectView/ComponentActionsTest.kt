/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import net.nevinsky.abyssus.editor.content.Vec3

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.editor.document.SceneJson
import java.io.File
import net.nevinsky.abyssus.testMetaFiles

class ComponentActionsTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun copyProject(sceneFixture: String = "Untitled/scenes/Main Scene.scene") {
        val dir = "Untitled"
        myFixture.copyFileToProject("$dir/Untitled.abss", "$dir/Untitled.abss")
        myFixture.copyFileToProject(sceneFixture, "$dir/scenes/Main Scene.scene")
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
    private fun entity(id: String) = descend(children(descend(abss(), "scenes")).single(), "ecs", id)
    private fun component(id: String, kind: String) = descend(entity(id), kind)

    private class AddOn(val node: Any?) : AddComponentAction() {
        override fun selected(e: AnActionEvent) = node
    }

    private class RemoveOn(val node: Any?) : RemoveComponentAction() {
        override fun selected(e: AnActionEvent) = node
    }

    private fun visible(action: AnAction): Boolean {
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        return event.presentation.isEnabledAndVisible
    }

    private fun scene() = componentTargetOf(entity("0"))!!.file
    private fun components(id: String) = net.nevinsky.abyssus.SceneEcsPaths().components(
        SceneJson.parse(FileDocumentManager.getInstance().getDocument(scene())!!.text), id)!!

    private class LightOn(val node: Any?) : AddLightAction() {
        override fun selected(e: AnActionEvent) = node
    }

    fun testLightChoicesOnSceneOnlyAndSpotAtOrigin() {
        copyProject("Lights/scenes/Creation Baseline.scene")
        val sceneNode = children(descend(abss(), "scenes")).single()
        val action = LightOn(sceneNode)
        assertTrue(visible(action))
        assertFalse(visible(LightOn(entity("0"))))
        assertFalse(visible(LightOn(abss())))
        assertTrue(ActionManager.getInstance().getAction("Abyssus.AddLight") is AddLightAction)
        var selected: String? = null
        val choices = AddLightGroup(project, scene(), { net.nevinsky.abyssus.editor.content.Vec3(0f, 0f, 0f) }, { selected = it }).getChildren(null)
        assertEquals(listOf("Directional", "Sun", "Spot"), choices.map { it.templatePresentation.text })
        choices[2].actionPerformed(TestActionEvent.createTestEvent(choices[2]))
        assertEquals("7", selected)
        val position = components("7")["PositionComponent"]["localPosition"]
        assertEquals(5, position["y"].asInt())
        assertEquals(0, position.path("x").asInt())
        assertEquals(0, position.path("z").asInt())
    }

    fun testActionsAreRegisteredInTheProjectViewMenu() {
        val manager = ActionManager.getInstance()
        assertTrue(manager.getAction("Abyssus.AddComponent") is AddComponentAction)
        assertTrue(manager.getAction("Abyssus.RemoveComponent") is RemoveComponentAction)
    }

    fun testAddIsOfferedOnEntityRowsOnly() {
        copyProject()
        assertTrue(visible(AddOn(entity("0"))))
        assertFalse(visible(AddOn(component("0", "TypeComponent"))))
        assertFalse(visible(AddOn(abss())))
        assertFalse(visible(AddOn(descend(abss(), "scenes"))))
        assertFalse(visible(AddOn(descend(abss(), "assets"))))
        assertFalse(visible(AddOn(null)))
    }

    fun testAddListsOnlyMissingModeledKindsAndAddsOne() {
        copyProject()
        val add = AddOn(entity("0"))
        val target = componentTargetOf(entity("0"))!!
        val names = add.choices(project, target).getChildren(null).map { it.templatePresentation.text }
        assertFalse("Position" in names)
        assertFalse("Pickable" in names)
        assertTrue("Light" in names)
        assertTrue("Render" !in names)
        val light = add.choices(project, target).getChildren(null).first { it.templatePresentation.text == "Light" }
        light.actionPerformed(TestActionEvent.createTestEvent(light))
        assertTrue(components("0").has("LightComponent"))
        assertFalse(add.choices(project, target).getChildren(null).any { it.templatePresentation.text == "Light" })
    }

    fun testRenderIsOfferedPerProjectAsset() {
        copyProject()
        SceneComponentEdits.remove(project, scene(), "6", "RenderComponent")
        val target = componentTargetOf(entity("6"))!!
        val render = AddOn(null).choices(project, target).getChildren(null)
            .filterIsInstance<com.intellij.openapi.actionSystem.ActionGroup>().single { it.templatePresentation.text == "Render" }
        val assets = SceneComponentEdits.renderAssets(scene(), testMetaFiles())
        assertTrue(assets.isNotEmpty())
        assertTrue(assets.all { it.type == "MODEL" || it.type == "TERRAIN" })
        val items = render.getChildren(null)
        assertEquals(assets.map { "${it.type.lowercase()} ${it.name}" }, items.map { it.templatePresentation.text })
        items.first().actionPerformed(TestActionEvent.createTestEvent(items.first()))
        val asset = components("6")["RenderComponent"]["renderable"]["asset"]
        assertEquals(assets.first().name, asset["assetName"].asText())
        assertEquals(assets.first().type, asset["type"].asText())
    }

    fun testRemoveIsOfferedOnModeledComponentsOnly() {
        copyProject()
        assertTrue(visible(RemoveOn(component("0", "TypeComponent"))))
        assertFalse(visible(RemoveOn(component("0", "PickableComponent"))))
        assertFalse(visible(RemoveOn(entity("0"))))
        assertFalse(visible(RemoveOn(abss())))
    }

    fun testRemoveWritesTheFile() {
        copyProject()
        val remove = RemoveOn(component("0", "TypeComponent"))
        remove.actionPerformed(TestActionEvent.createTestEvent(remove))
        assertFalse(components("0").has("TypeComponent"))
        assertTrue(components("0").has("PickableComponent"))
    }

    fun testTreeShowsTheNewComponentAndCount() {
        copyProject()
        val add = AddOn(entity("0"))
        val light = add.choices(project, componentTargetOf(entity("0"))!!).getChildren(null).first { it.templatePresentation.text == "Light" }
        light.actionPerformed(TestActionEvent.createTestEvent(light))
        val node = entity("0") as DtoEntryNode
        assertTrue(children(node).any { label(it) == "LightComponent" })
        assertEquals(6, children(node).size)
    }
}
