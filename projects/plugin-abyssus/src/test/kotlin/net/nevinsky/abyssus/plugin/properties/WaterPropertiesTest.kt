/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.water.WaterCodec
import net.nevinsky.abyssus.lib.core.editor.water.WaterEdit
import net.nevinsky.abyssus.lib.core.water.WaterField
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.plugin.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.plugin.projectView.AbyssusRootNode
import net.nevinsky.abyssus.plugin.projectView.AbyssusSelection
import net.nevinsky.abyssus.plugin.projectView.DtoEntryNode
import net.nevinsky.abyssus.plugin.testPanelServices
import net.nevinsky.abyssus.plugin.water.WATER_ROW
import net.nevinsky.abyssus.plugin.water.WaterSceneEdits
import java.awt.Component
import java.awt.Container
import java.io.File

class WaterPropertiesTest : BasePlatformTestCase() {
    private lateinit var scene: VirtualFile
    private lateinit var id: String
    private val original = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()

    override fun setUp() {
        super.setUp()
        AbyssusSelection.of(project).select(null)
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""")
        scene = myFixture.addFileToProject("p/scenes/Main Scene.scene", original).virtualFile
        id = WaterSceneEdits(project).add(scene, WaterPreset.LAKE, 1.0, 2.0, 3.0).id!!
        myFixture.openFileInEditor(scene)
    }

    override fun tearDown() {
        try {
            AbyssusSelection.of(project).select(null)
        } finally {
            super.tearDown()
        }
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }
    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    private fun waterRow(): AbstractTreeNode<*> {
        val projectRow = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
        val sceneRow = children(children(projectRow).single { label(it) == "scenes" }).single()
        return children(children(sceneRow).single { label(it) == WATER_ROW }).first { label(it) == id }
    }

    private fun panel() = AssetPropertiesPanel(project, testRootDisposable, testPanelServices(project), { it.run() }, { it.run() })

    private fun named(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }

    private fun field(p: Component, f: WaterField) = named(p, "water-field-${f.name}") as JBTextField
    private fun error(p: Component, f: WaterField) = named(p, "water-error-${f.name}") as JBLabel
    private fun text() = FileDocumentManager.getInstance().getDocument(scene)!!.text
    private fun surface() = WaterCodec().read(SceneJson().parse(text()).get("abyssus")).valid.single { it.id == id }

    /** Types [value] into the editor of [f] and commits it the way Enter does. */
    private fun type(p: Component, f: WaterField, value: String) {
        val editor = field(p, f)
        editor.text = value
        editor.postActionEvent()
    }

    fun testSelectedSurfaceShowsNameAbyssusOnlyAndEveryValue() {
        val p = panel()
        p.show(waterRow())
        val state = p.state as PanelState.WaterDetails
        assertEquals("Lake", (named(p, "water-name") as JBTextField).text)
        assertNotNull(named(p, "water-abyssus-only"))
        assertEquals("1", field(p, WaterField.X).text)
        assertEquals("2", field(p, WaterField.LEVEL).text)
        assertEquals("3", field(p, WaterField.Z).text)
        for (f in WaterField.entries) assertEquals(f.name, state.texts.getValue(f), field(p, f).text)
        assertEquals(true, (named(p, "water-enabled") as JBCheckBox).isSelected)
    }

    fun testEachSettingEditsOnlyItsOwnNumber() {
        val p = panel()
        p.show(waterRow())
        val expected = mapOf(
            WaterField.X to 5.5, WaterField.LEVEL to 3.0, WaterField.Z to -4.0, WaterField.WIDTH to 50.0, WaterField.LENGTH to 60.0,
            WaterField.TINT_R to 0.5, WaterField.TINT_G to 0.4, WaterField.TINT_B to 0.3, WaterField.CLARITY to 12.0,
            WaterField.WAVE_AMPLITUDE to 0.2, WaterField.WAVE_LENGTH to 7.0, WaterField.WAVE_SPEED to 1.5,
            WaterField.FOAM_AMOUNT to 0.9, WaterField.FOAM_WIDTH to 2.0,
        )
        for ((f, v) in expected) {
            val before = surface()
            val panel = panel().also { it.show(waterRow()) }
            type(panel, f, v.toString())
            val after = surface()
            assertEquals(f.name, v, after.value(f), 0.0)
            for (other in WaterField.entries.filter { it != f }) assertEquals("${f.name}/${other.name}", before.value(other), after.value(other), 0.0)
        }
        assertTrue(p.state is PanelState.WaterDetails)
    }

    fun testWaterLevelSetToThreeChangesOnlyPositionY() {
        val before = text()
        val water = before.indexOf("\"waterSurfaces\"")
        type(panel().also { it.show(waterRow()) }, WaterField.LEVEL, "3")
        assertEquals(before.substring(0, water) + before.substring(water).replaceFirst("\"y\": 2", "\"y\": 3"), text())
        assertEquals(3.0, surface().level, 0.0)
    }

    fun testInvalidValuesRestoreThePreviousValueAndLeaveTheFileAlone() {
        val p = panel().also { it.show(waterRow()) }
        val before = text()
        for (bad in listOf("0", "-4", "NaN", "Infinity", "abc", "")) {
            type(p, WaterField.WIDTH, bad)
            assertEquals(bad, "100", field(p, WaterField.WIDTH).text)
            assertTrue(bad, error(p, WaterField.WIDTH).text.isNotBlank())
        }
        type(p, WaterField.FOAM_AMOUNT, "2")
        assertEquals("0.5", field(p, WaterField.FOAM_AMOUNT).text)
        assertEquals(before, text())
    }

    fun testRenameAndVisibilityAreEditedHere() {
        val p = panel().also { it.show(waterRow()) }
        val name = named(p, "water-name") as JBTextField
        name.text = "Pond"
        name.postActionEvent()
        assertEquals("Pond", WaterCodec().read(SceneJson().parse(text()).get("abyssus")).surfaces.single { it.id == id }.name)
        val blank = panel().also { it.show(waterRow()) }
        (named(blank, "water-name") as JBTextField).apply { text = " "; postActionEvent() }
        assertTrue((named(blank, "water-name-error") as JBLabel).text.isNotBlank())
        val box = named(panel().also { it.show(waterRow()) }, "water-enabled") as JBCheckBox
        box.doClick()
        assertFalse(WaterCodec().read(SceneJson().parse(text()).get("abyssus")).valid.single().enabled)
    }

    fun testEditsAreUndoableThroughThePanelsEditor() {
        val before = text()
        val p = panel().also { it.show(waterRow()) }
        type(p, WaterField.CLARITY, "30")
        val after = text()
        val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        UndoManager.getInstance(project).undo(editor)
        assertEquals(before, text())
        UndoManager.getInstance(project).redo(editor)
        assertEquals(after, text())
    }

    fun testTextEditsToTheSceneRefreshTheShownValue() {
        val p = panel().also { it.show(waterRow()) }
        assertEquals("8", field(p, WaterField.CLARITY).text)
        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(scene)!!
            document.setText(document.text.replace("\"clarity\": 8", "\"clarity\": 21"))
        }
        assertEquals("21", field(p, WaterField.CLARITY).text)
    }

    fun testRemovedSurfaceShowsThatItIsGone() {
        val p = panel().also { it.show(waterRow()) }
        assertEquals(WaterEdit.Changed, WaterSceneEdits(project).remove(scene, id))
        val empty = p.state as PanelState.Empty
        assertEquals("This water surface no longer exists.", empty.message)
        assertNull(named(p, "water-name")?.takeIf { it.isShowing })
    }

    fun testInvalidSurfaceShowsANoteAndCanBeFixed() {
        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(scene)!!
            document.setText(document.text.replace("\"width\": 100", "\"width\": -1"))
        }
        val p = panel().also { it.show(waterRow()) }
        assertNotNull(named(p, "water-invalid-note"))
        assertEquals("-1", field(p, WaterField.WIDTH).text)
        type(p, WaterField.WIDTH, "40")
        assertEquals(40.0, surface().width, 0.0)
        assertNull(named(panel().also { it.show(waterRow()) }, "water-invalid-note"))
    }
}
