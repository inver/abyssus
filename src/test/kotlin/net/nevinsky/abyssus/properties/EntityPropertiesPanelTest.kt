/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.projectView.AbyssusRootNode
import net.nevinsky.abyssus.projectView.AbyssusSelection
import net.nevinsky.abyssus.projectView.DtoEntryNode
import net.nevinsky.abyssus.projectView.addComponentGroup
import net.nevinsky.abyssus.projectView.componentTargetOf
import java.awt.Component
import java.awt.Container
import java.io.File
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JTextArea
import net.nevinsky.abyssus.testMetaFiles
import net.nevinsky.abyssus.testPanelServices

class EntityPropertiesPanelTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    override fun setUp() {
        super.setUp()
        AbyssusSelection.of(project).select(null)
    }

    override fun tearDown() {
        try {
            AbyssusSelection.of(project).select(null)
        } finally {
            super.tearDown()
        }
    }

    private fun copyProject() {
        val dir = "Untitled"
        myFixture.copyFileToProject("$dir/Untitled.abss", "$dir/Untitled.abss")
        myFixture.copyFileToProject("$dir/scenes/Main Scene.scene", "$dir/scenes/Main Scene.scene")
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

    private fun entity(id: String) = descend(
        children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") },
        "scenes",
    ).let { scenes -> descend(children(scenes).single(), "ecs", id) }

    private fun component(id: String, kind: String) = descend(entity(id), kind)

    private fun panel() = AssetPropertiesPanel(project, testRootDisposable, testPanelServices(project), { it.run() }, { it.run() })

    private fun <T : Component> all(c: Component, type: Class<T>): List<T> = buildList {
        if (type.isInstance(c)) add(type.cast(c))
        if (c is Container) c.components.forEach { addAll(all(it, type)) }
    }

    private fun named(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }

    private fun sceneFile(): VirtualFile = componentTargetOf(entity("0"))!!.file

    private fun text(f: VirtualFile) = FileDocumentManager.getInstance().getDocument(f)!!.text

    private fun components(f: VirtualFile, id: String) = SceneJson.parse(text(f))["ecs"]["entities"][id]["components"]

    fun testEntityShowsItsComponents() {
        copyProject()
        val p = panel()
        p.show(entity("0"))
        val state = p.state as PanelState.EntityDetails
        assertEquals("Model 0", state.name)
        assertEquals(listOf("NameComponent", "PickableComponent", "PositionComponent", "RenderComponent", "TypeComponent"), state.sections.map { it.kind })
        assertEquals("Model 0", all(p, JBLabel::class.java).first { it.name == "entity-name" }.text)
        assertEquals("-3.035308", (named(p, "field-PositionComponent-localPosition.x") as JBTextField).text)
        assertTrue("LightComponent" in state.addable)
        assertFalse("PositionComponent" in state.addable)
    }

    fun testComponentShowsOnlyItself() {
        copyProject()
        val p = panel()
        p.show(component("4", "CameraComponent"))
        val state = p.state as PanelState.EntityDetails
        assertEquals(listOf("CameraComponent"), state.sections.map { it.kind })
        assertTrue(state.addable.isEmpty())
        assertNull(named(p, "add-component"))
        assertNotNull(named(p, "field-CameraComponent-camera.fieldOfView"))
    }

    fun testUnmodeledComponentIsReadOnlyJson() {
        copyProject()
        val p = panel()
        p.show(component("0", "PickableComponent"))
        val section = (p.state as PanelState.EntityDetails).sections.single()
        assertNotNull(section.raw)
        assertTrue(section.raw!!.contains("pickerIdAttribute"))
        assertFalse((named(p, "raw-PickableComponent") as JTextArea).isEditable)
        assertNull(named(p, "remove-PickableComponent"))
        assertTrue(all(p, JBTextField::class.java).isEmpty())
    }

    fun testSceneAndSettingsStillShowNothing() {
        copyProject()
        val p = panel()
        p.show(children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") })
        assertTrue(p.state is PanelState.Empty)
        p.show(descend(children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }, "scenes"))
        assertTrue(p.state is PanelState.Empty)
    }

    fun testEditingAValueWritesTheFile() {
        copyProject()
        val p = panel()
        p.show(component("4", "CameraComponent"))
        val field = named(p, "field-CameraComponent-camera.fieldOfView") as JBTextField
        field.text = "50"
        field.postActionEvent()
        assertEquals(50, components(sceneFile(), "4")["CameraComponent"]["camera"]["fieldOfView"].asInt())
        val shown = named(p, "field-CameraComponent-camera.fieldOfView") as JBTextField
        assertEquals("50", shown.text)
    }

    fun testInvalidValueIsRevertedWithAMessage() {
        copyProject()
        val p = panel()
        p.show(component("4", "CameraComponent"))
        val before = text(sceneFile())
        val field = named(p, "field-CameraComponent-camera.fieldOfView") as JBTextField
        field.text = "abc"
        field.postActionEvent()
        assertEquals("67", field.text)
        assertTrue((named(p, "error-CameraComponent-camera.fieldOfView") as JBLabel).text.contains("abc"))
        assertEquals(before, text(sceneFile()))
    }

    fun testChoiceFieldEdits() {
        copyProject()
        val p = panel()
        p.show(component("0", "TypeComponent"))
        val combo = named(p, "field-TypeComponent-type") as JComboBox<*>
        combo.selectedItem = "GROUP"
        assertEquals("GROUP", components(sceneFile(), "0")["TypeComponent"]["type"].asText())
    }

    fun testAddFromThePanelShowsTheNewSection() {
        copyProject()
        val p = panel()
        p.show(entity("0"))
        val state = p.state as PanelState.EntityDetails
        val group = addComponentGroup(project, sceneFile(), "0", state.addable, testMetaFiles())
        val light: AnAction = group.getChildren(null).first { it.templatePresentation.text == "Light" }
        light.actionPerformed(TestActionEvent.createTestEvent(light))
        assertTrue(components(sceneFile(), "0").has("LightComponent"))
        val after = p.state as PanelState.EntityDetails
        assertTrue(after.sections.any { it.kind == "LightComponent" })
        assertNotNull(named(p, "field-LightComponent-intensity"))
        assertFalse("LightComponent" in after.addable)
    }

    fun testLightRangeEditorWritesThirty() {
        copyProject()
        net.nevinsky.abyssus.projectView.SceneComponentEdits.add(project, sceneFile(), "0", "LightComponent", testMetaFiles())
        val p = panel()
        p.show(entity("0"))
        val field = named(p, "field-LightComponent-range") as JBTextField
        assertEquals("100", field.text)
        field.text = "30"
        field.postActionEvent()
        assertEquals(30, components(sceneFile(), "0")["LightComponent"]["light"]["range"].asInt())
    }

    fun testBeamEditorsAreSpotlightOnlyAndUsePercent() {
        copyProject()
        val f = sceneFile()
        val edits = net.nevinsky.abyssus.projectView.SceneComponentEdits
        edits.add(project, f, "0", "LightComponent", testMetaFiles())
        val p = panel()
        for (type in listOf("LIGHT_POINT", "LIGHT_DIRECTIONAL", "LIGHT_SPOT")) {
            edits.update(project, f, "0", "TypeComponent", "type", type, testMetaFiles())
            p.show(component("0", "LightComponent"))
            if (type != "LIGHT_SPOT") {
                assertNull(named(p, "field-LightComponent-coneAngle"))
                assertNull(named(p, "field-LightComponent-edgeSoftness"))
            } else {
                assertEquals("45", (named(p, "field-LightComponent-coneAngle") as JBTextField).text)
                assertEquals("20", (named(p, "field-LightComponent-edgeSoftness") as JBTextField).text)
                assertTrue(all(p, JBLabel::class.java).any { it.text == "Cone angle (degrees)" })
                assertTrue(all(p, JBLabel::class.java).any { it.text == "Edge softness (%)" })
                val field = named(p, "field-LightComponent-edgeSoftness") as JBTextField
                field.text = "25"
                field.postActionEvent()
                assertEquals(0.25f, components(f, "0")["LightComponent"]["light"]["edgeSoftness"].floatValue())
                val angle = named(p, "field-LightComponent-coneAngle") as JBTextField
                val before = text(f)
                angle.text = "180"
                angle.postActionEvent()
                assertEquals("45", angle.text)
                assertEquals(before, text(f))
                assertTrue((named(p, "error-LightComponent-coneAngle") as JBLabel).text.contains("180"))
                val doc = FileDocumentManager.getInstance().getDocument(f)!!
                val root = SceneJson.parse(doc.text)
                (root["ecs"]["entities"]["0"]["components"]["LightComponent"]["light"] as com.fasterxml.jackson.databind.node.ObjectNode).put("coneAngle", 60)
                WriteCommandAction.runWriteCommandAction(project) { doc.setText(SceneJson.inStyleOf(doc.text, root)) }
                assertEquals("60", (named(p, "field-LightComponent-coneAngle") as JBTextField).text)
            }
        }
    }

    fun testRemoveFromThePanelDropsTheSection() {
        copyProject()
        val p = panel()
        p.show(entity("0"))
        (named(p, "remove-TypeComponent") as JButton).doClick()
        assertFalse(components(sceneFile(), "0").has("TypeComponent"))
        assertFalse((p.state as PanelState.EntityDetails).sections.any { it.kind == "TypeComponent" })
        assertNull(named(p, "field-TypeComponent-type"))
    }

    fun testPanelFollowsTextEdits() {
        copyProject()
        val p = panel()
        p.show(component("4", "CameraComponent"))
        val f = sceneFile()
        val doc = FileDocumentManager.getInstance().getDocument(f)!!
        WriteCommandAction.runWriteCommandAction(project) { doc.setText(doc.text.replace("\"fieldOfView\": 67", "\"fieldOfView\": 42")) }
        assertEquals("42", (named(p, "field-CameraComponent-camera.fieldOfView") as JBTextField).text)
    }

    fun testDeletedComponentShowsAMessage() {
        copyProject()
        val p = panel()
        p.show(component("0", "TypeComponent"))
        val doc = FileDocumentManager.getInstance().getDocument(sceneFile())!!
        val root = SceneJson.parse(doc.text)
        (root["ecs"]["entities"]["0"]["components"] as com.fasterxml.jackson.databind.node.ObjectNode).remove("TypeComponent")
        WriteCommandAction.runWriteCommandAction(project) { doc.setText(SceneJson.inStyleOf(doc.text, root)) }
        val state = p.state as PanelState.Empty
        assertTrue(state.message, state.message.contains("no longer has"))
    }
}
