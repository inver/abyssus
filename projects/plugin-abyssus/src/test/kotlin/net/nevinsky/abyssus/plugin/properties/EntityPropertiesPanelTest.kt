/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

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
import java.awt.Component
import java.awt.Container
import java.io.File
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JTextArea
import net.nevinsky.abyssus.lib.core.ecs.component.*
import net.nevinsky.abyssus.plugin.editSceneText
import net.nevinsky.abyssus.plugin.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.plugin.projectView.AbyssusRootNode
import net.nevinsky.abyssus.plugin.projectView.AbyssusSelection
import net.nevinsky.abyssus.plugin.projectView.DtoEntryNode
import net.nevinsky.abyssus.plugin.projectView.addComponentGroup
import net.nevinsky.abyssus.plugin.projectView.componentTargetOf
import net.nevinsky.abyssus.plugin.projectView.scene.SceneComponentEdits
import net.nevinsky.abyssus.plugin.testMetaFiles
import net.nevinsky.abyssus.plugin.testPanelServices

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

    /** The `Custom` project: a scene with a plane, the `tree` model and the exported component schema. */
    private fun copyCustom() {
        for (path in
            listOf("Custom.abss", "scenes/Field.scene", "assets/tree/meta.json", "abyssus/components.schema.json")) {
            myFixture.copyFileToProject("Custom/$path", "Custom/$path")
        }
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }

    private fun label(node: AbstractTreeNode<*>): String =
        (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    private fun descend(from: AbstractTreeNode<*>, vararg names: String) =
        names.fold(from) { node, name -> children(node).single { label(it) == name } }

    private fun entity(id: String) =
        descend(
                children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") },
                "scenes",
            )
            .let { scenes -> descend(children(scenes).single(), "ecs", id) }

    private fun component(id: String, kind: String) = descend(entity(id), kind)

    private fun panel() =
        AssetPropertiesPanel(project, testRootDisposable, testPanelServices(project), { it.run() }, { it.run() })

    private fun <T : Component> all(c: Component, type: Class<T>): List<T> = buildList {
        if (type.isInstance(c)) {
            add(type.cast(c))
        }
        if (c is Container) {
            c.components.forEach { addAll(all(it, type)) }
        }
    }

    private fun named(c: Component, name: String): Component? =
        if (c.name == name) {
            c
        } else {
            (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }
        }

    private fun sceneFile(): VirtualFile = componentTargetOf(entity("0"))!!.file

    private fun text(f: VirtualFile) = FileDocumentManager.getInstance().getDocument(f)!!.text

    private fun rawEntries(e: net.nevinsky.abyssus.lib.core.dto.EntityDto) =
        e.components.filterIsInstance<RawComponentsWrapper>().flatMap { it.entries }

    private fun entityDto(f: VirtualFile, id: Long) = net.nevinsky.abyssus.plugin.parsedScene(text(f)).entity(id)!!

    fun testEntityShowsItsComponents() {
        copyProject()
        val p = panel()
        p.show(entity("0"))
        val state = p.state as PanelState.EntityDetails
        assertEquals("Model 0", state.name)
        assertEquals(
            listOf("NameComponent", "PositionComponent", "RenderComponent", "PickableComponent"),
            state.sections.map { it.kind },
        )
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

    fun testProjectRootShowsSettingsWhileScenesGroupShowsAReadOnlyFolder() {
        copyProject()
        val p = panel()
        p.show(children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") })
        assertFalse((p.state as PanelState.Project).settings.physicsEnabled)
        p.show(
            descend(
                children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") },
                "scenes",
            )
        )
        val folder = p.state as PanelState.StructuralDetails
        assertEquals("Folder · 1 item", folder.model.header.subtitle)
        assertTrue(all(p, JBTextField::class.java).isEmpty())
    }

    private fun leave(c: Component) =
        c.focusListeners.forEach { it.focusLost(java.awt.event.FocusEvent(c, java.awt.event.FocusEvent.FOCUS_LOST)) }

    fun testLeavingAFieldConfirmsItsValue() {
        copyProject()
        val p = panel()
        p.show(component("8", "LightComponent"))
        val field = named(p, "field-LightComponent-intensity") as JBTextField
        field.text = "2"
        leave(field)
        assertEquals(2f, entityDto(sceneFile(), 8).component(LightComponent::class.java)!!.light.intensity, 0f)
        assertEquals("2", (named(p, "field-LightComponent-intensity") as JBTextField).text)
        assertEquals("", (named(p, "error-LightComponent-intensity") as JBLabel).text)
    }

    fun testARefusedValueIsExplainedBesideTheFieldAndWritesNothing() {
        copyProject()
        val p = panel()
        p.show(component("8", "LightComponent"))
        val before = text(sceneFile())
        val field = named(p, "field-LightComponent-intensity") as JBTextField
        field.text = "abc"
        field.postActionEvent()
        assertEquals("1", field.text)
        assertTrue((named(p, "error-LightComponent-intensity") as JBLabel).text.contains("abc"))
        assertEquals(before, text(sceneFile()))
    }

    fun testAnEqualValueWritesNothingAndAddsNoUndo() {
        copyProject()
        val p = panel()
        p.show(component("8", "LightComponent"))
        val file = sceneFile()
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val before = text(file)
        val stamp = document.modificationStamp
        val field = named(p, "field-LightComponent-intensity") as JBTextField
        field.text = "1"
        field.postActionEvent()
        assertEquals(before, text(file))
        assertEquals(stamp, document.modificationStamp)
    }

    fun testEditingAValueWritesTheFile() {
        copyProject()
        val p = panel()
        p.show(component("4", "CameraComponent"))
        val field = named(p, "field-CameraComponent-camera.fieldOfView") as JBTextField
        field.text = "50"
        field.postActionEvent()
        assertEquals(50f, entityDto(sceneFile(), 4).component(CameraComponent::class.java)!!.camera.fieldOfView, 0f)
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

    fun testAddFromThePanelShowsTheNewSection() {
        copyProject()
        val p = panel()
        p.show(entity("0"))
        val state = p.state as PanelState.EntityDetails
        val group = addComponentGroup(project, sceneFile(), 0L, state.addable, testMetaFiles())
        val light: AnAction = group.getChildren(null).first { it.templatePresentation.text == "Light" }
        light.actionPerformed(TestActionEvent.createTestEvent(light))
        assertTrue(entityDto(sceneFile(), 0).component(LightComponent::class.java) != null)
        val after = p.state as PanelState.EntityDetails
        assertTrue(after.sections.any { it.kind == "LightComponent" })
        assertNotNull(named(p, "field-LightComponent-intensity"))
        assertFalse("LightComponent" in after.addable)
    }

    fun testCustomComponentIsShownAsReadOnlyJson() {
        copyCustom()
        val before = text(sceneFile())
        val p = panel()
        p.show(component("0", "PlaneComponent"))
        val section = (p.state as PanelState.EntityDetails).sections.single()
        assertEquals("PlaneComponent", section.kind)
        assertEquals(section.raw, rawEntries(entityDto(sceneFile(), 0)).single { it.name == "PlaneComponent" }.json)
        assertTrue(section.fields.isEmpty())
        assertNull(named(p, "field-PlaneComponent-lineLength"))
        assertNull(named(p, "remove-PlaneComponent"))
        assertEquals(before, text(sceneFile()))
    }

    fun testCustomComponentsAreNotOfferedForAdding() {
        copyCustom()
        val before = text(sceneFile())
        val p = panel()
        p.show(entity("1"))
        val state = p.state as PanelState.EntityDetails
        assertFalse("PlaneComponent" in state.addable)
        val group = addComponentGroup(project, sceneFile(), 1L, state.addable, testMetaFiles())
        assertFalse(group.getChildren(null).any { it.templatePresentation.text == "Plane" })
        assertTrue(rawEntries(entityDto(sceneFile(), 1)).none { it.name == "PlaneComponent" })
        assertEquals(before, text(sceneFile()))
    }

    fun testLightRangeEditorWritesThirty() {
        copyProject()
        SceneComponentEdits.add(project, sceneFile(), 0L, "LightComponent", testMetaFiles())
        val p = panel()
        p.show(entity("0"))
        val field = named(p, "field-LightComponent-range") as JBTextField
        assertEquals("100", field.text)
        field.text = "30"
        field.postActionEvent()
        assertEquals(30f, entityDto(sceneFile(), 0).component(LightComponent::class.java)!!.light.range, 0f)
    }

    private fun setEntityType(type: String) {
        val doc = FileDocumentManager.getInstance().getDocument(sceneFile())!!
        editSceneText(project, doc) {
            it.entity(0)!!.header().type = net.nevinsky.abyssus.lib.core.format.EntityType.valueOf(type)
        }
    }

    fun testBeamEditorsAreSpotlightOnlyAndUsePercent() {
        copyProject()
        val f = sceneFile()
        val edits = SceneComponentEdits
        edits.add(project, f, 0L, "LightComponent", testMetaFiles())
        val p = panel()
        for (type in listOf("LIGHT_POINT", "LIGHT_DIRECTIONAL", "LIGHT_SPOT")) {
            setEntityType(type)
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
                assertEquals(0.25f, entityDto(f, 0).component(LightComponent::class.java)!!.light.edgeSoftness, 0f)
                val angle = named(p, "field-LightComponent-coneAngle") as JBTextField
                val before = text(f)
                angle.text = "180"
                angle.postActionEvent()
                assertEquals("45", angle.text)
                assertEquals(before, text(f))
                assertTrue((named(p, "error-LightComponent-coneAngle") as JBLabel).text.contains("180"))
                val doc = FileDocumentManager.getInstance().getDocument(f)!!
                editSceneText(project, doc) {
                    it.entity(0)!!.component(LightComponent::class.java)!!.light.coneAngle = 60f
                }
                assertEquals("60", (named(p, "field-LightComponent-coneAngle") as JBTextField).text)
            }
        }
    }

    fun testRemoveFromThePanelDropsTheSection() {
        copyProject()
        val p = panel()
        p.show(entity("0"))
        (named(p, "remove-PositionComponent") as JButton).doClick()
        assertFalse(entityDto(sceneFile(), 0).component(PositionComponent::class.java) != null)
        assertFalse((p.state as PanelState.EntityDetails).sections.any { it.kind == "PositionComponent" })
        assertNull(named(p, "field-PositionComponent-localPosition.x"))
    }

    fun testPanelFollowsTextEdits() {
        copyProject()
        val p = panel()
        p.show(component("4", "CameraComponent"))
        val f = sceneFile()
        val doc = FileDocumentManager.getInstance().getDocument(f)!!
        WriteCommandAction.runWriteCommandAction(project) {
            doc.setText(doc.text.replace("\"fieldOfView\": 67", "\"fieldOfView\": 42"))
        }
        assertEquals("42", (named(p, "field-CameraComponent-camera.fieldOfView") as JBTextField).text)
    }

    fun testDeletedComponentShowsAMessage() {
        copyProject()
        val p = panel()
        p.show(component("0", "NameComponent"))
        val doc = FileDocumentManager.getInstance().getDocument(sceneFile())!!
        editSceneText(project, doc) { it.entity(0)!!.components.removeAll { c -> c is NameComponent } }
        val state = p.state as PanelState.Empty
        assertTrue(state.message, state.message.contains("no longer has"))
    }
}
