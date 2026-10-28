/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.editor.host.*
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import java.io.File

/** The eye on entity and component rows (spec `abyssus-project-view`): which rows have one, what it writes, how a disabled subtree looks. */
class EntityEyeTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun json(text: String) = SceneJson().parse(text)

    // ---- which rows have an eye ----

    fun testEntityRowsHaveAnEyeExceptGroups() {
        val ecs = parseDocument(
            """{
              "0":{"type":"OBJECT","components":{}},
              "1":{"flags":1,"type":"OBJECT","components":{}},
              "2":{"type":"GROUP","components":{}}}""",
        )
        val rows = ecsRows(ecs).associateBy { it.name }
        assertEquals(true, rows.getValue("0").enabled)
        assertEquals(false, rows.getValue("1").enabled)
        assertNull("a GROUP has no eye", rows.getValue("2").enabled)
    }

    fun testComponentRowsFollowTheTogglableMatrix() {
        val entity = parseDocument(
            """{"disabledComponents":["ColliderComponent"],"components":{"NameComponent":{},"PositionComponent":{},"ParentComponent":{},"RenderComponent":{},"RigidBodyComponent":{},"ColliderComponent":{},"LightComponent":{},"FoliageComponent":{},"PlaneComponent":{},"PilotComponent":{}}}""",
        )
        val rows = entityRows(entity).associateBy { it.name }
        for (name in listOf("NameComponent", "PositionComponent", "ParentComponent")) assertNull(name, rows.getValue(name).enabled)
        for (name in listOf("RenderComponent", "RigidBodyComponent", "LightComponent", "FoliageComponent", "PlaneComponent", "PilotComponent"))
            assertEquals(name, true, rows.getValue(name).enabled)
        assertEquals(false, rows.getValue("ColliderComponent").enabled)
    }

    // ---- the click ----

    private fun copyProject() {
        myFixture.copyFileToProject("Untitled/Untitled.abss", "Untitled/Untitled.abss")
        myFixture.copyFileToProject("Untitled/scenes/Main Scene.scene", "Untitled/scenes/Main Scene.scene")
        File("$testDataPath/Untitled/assets").listFiles { f -> f.isDirectory }!!.forEach { d ->
            d.listFiles { f -> f.isFile && f.extension == "json" }!!.forEach {
                myFixture.addFileToProject("Untitled/assets/${d.name}/${it.name}", it.readText().trimEnd() + "\n")
            }
        }
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { (it as AbstractTreeNode<*>).also { c -> c.parent = node } }
    private fun entry(node: AbstractTreeNode<*>) = (node as DtoEntryNode).value
    private fun label(node: AbstractTreeNode<*>) = (node as? DtoEntryNode)?.let { it.label ?: it.value.name } ?: (node as AbyssusAssetNode).virtualFile.name
    private fun root() = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
    private fun ecs() = children(children(children(root()).single { label(it) == "scenes" }).single()).single { label(it) == "ecs" }
    private fun entity(id: String) = children(ecs()).first { entry(it).name == id }
    private fun sceneText() = FileDocumentManager.getInstance().getDocument(myFixture.findFileInTempDir("Untitled/scenes/Main Scene.scene"))!!.text

    fun testClickingTheEyeOnAnEntityWritesOnlyItsFlagAndUndoRestoresTheRow() {
        copyProject()
        myFixture.openFileInEditor(myFixture.findFileInTempDir("Untitled/scenes/Main Scene.scene"))
        val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val before = sceneText()
        val row = entry(entity("0"))
        assertEquals(true, row.enabled)
        assertTrue(toggleRow(project, row))
        val written = json(sceneText())
        assertEquals(1, written["ecs"]["0"]["flags"].intValue())
        assertEquals(json(before)["ecs"]["0"]["components"], written["ecs"]["0"]["components"])
        assertEquals(false, entry(entity("0")).enabled)
        UndoManager.getInstance(project).undo(editor)
        assertEquals(before, sceneText())
        assertEquals(true, entry(entity("0")).enabled)
    }

    fun testClickingTheEyeOnAComponentWritesOnlyThatEntry() {
        copyProject()
        val render = children(entity("0")).single { label(it) == "RenderComponent" }
        assertTrue(toggleRow(project, entry(render)))
        val written = json(sceneText())["ecs"]["0"]
        assertEquals(listOf("RenderComponent"), written["disabledComponents"].map { it.asText() })
        assertNull(json(sceneText())["ecs"]["0"]["flags"])
        // turning it on again removes the key
        val again = children(entity("0")).single { label(it) == "RenderComponent" }
        assertEquals(false, entry(again).enabled)
        assertTrue(toggleRow(project, entry(again)))
        assertNull(json(sceneText())["ecs"]["0"]["disabledComponents"])
    }

    fun testADisabledEntityDimsItsRowAndItsComponentRowsWithGrayIcons() {
        copyProject()
        toggleRow(project, entry(entity("0")))
        fun icon(node: AbstractTreeNode<*>): javax.swing.Icon {
            node.update()
            return node.presentation.getIcon(false)!!
        }
        val disabled = entity("0")
        val component = children(disabled).first { label(it) == "RenderComponent" }
        // the same entity on, for comparison: switching it back on shows the normal icons
        toggleRow(project, entry(disabled))
        val on = entity("0")
        val normalComponent = children(on).first { label(it) == "RenderComponent" }
        val dimmedComponent = icon(component)
        assertNotSame("a component under a disabled entity is gray", icon(normalComponent), dimmedComponent)
        assertNotSame("the disabled entity is gray", icon(on), icon(disabled))
        assertSame(IconLoader.getDisabledIcon(icon(normalComponent)).iconWidth, dimmedComponent.iconWidth)
        // a text row of the subtree is dimmed too
        assertTrue(component.presentation.coloredText.all { it.attributes == com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES || it.attributes == com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES })
    }
}
