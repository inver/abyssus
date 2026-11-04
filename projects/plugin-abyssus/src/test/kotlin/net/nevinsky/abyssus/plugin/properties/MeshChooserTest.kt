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
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import java.io.File
import net.nevinsky.abyssus.plugin.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.plugin.projectView.AbyssusRootNode
import net.nevinsky.abyssus.plugin.projectView.AbyssusSelection
import net.nevinsky.abyssus.plugin.projectView.DtoEntryNode
import net.nevinsky.abyssus.plugin.projectView.componentTargetOf
import net.nevinsky.abyssus.plugin.testPanelServices

/** The mesh field of a model's Render component in the Properties panel. */
class MeshChooserTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private val model = "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb"
    private val asked = mutableListOf<String>()

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

    private fun panel(meshes: List<String>?) =
        AssetPropertiesPanel(
            project,
            testRootDisposable,
            testPanelServices(
                project,
                meshes = { _, name ->
                    asked += name
                    meshes
                },
            ),
            { it.run() },
            { it.run() },
        )

    private fun named(c: Component, name: String): Component? =
        if (c.name == name) {
            c
        } else {
            (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }
        }

    private fun combo(p: Component): ComboBox<*>? = named(p, "field-RenderComponent-meshId") as? ComboBox<*>

    private fun items(c: ComboBox<*>) = (0 until c.itemCount).map { c.getItemAt(it) as String }

    private fun sceneText(): String {
        val file = componentTargetOf(entity("0"))!!.file
        return FileDocumentManager.getInstance().getDocument(file)!!.text
    }

    private fun storeMesh(id: String) {
        val file = componentTargetOf(entity("0"))!!.file
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(
                document.text.replace(
                    "\"assetName\": \"$model\"",
                    "\"assetName\": \"$model\",\n          \"meshId\": \"$id\"",
                )
            )
        }
    }

    fun testTheChooserListsTheMeshesAndAWholeModelEntry() {
        copyProject()
        val p = panel(listOf("body", "glass"))
        p.show(entity("0"))
        val box = combo(p)!!
        assertEquals(listOf("", "body", "glass"), items(box))
        assertEquals("", box.selectedItem)
        assertEquals(listOf(model), asked.distinct())
    }

    fun testChoosingAMeshWritesItAsOneUndoableEditAndTheWholeModelEntryRemovesIt() {
        copyProject()
        myFixture.openFileInEditor(componentTargetOf(entity("0"))!!.file)
        val editor =
            com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        // opening the scene in a text editor pretty-prints it; the baseline is the text as opened
        val before = sceneText()
        val p = panel(listOf("body", "glass"))
        p.show(entity("0"))
        combo(p)!!.selectedItem = "glass"
        assertTrue(sceneText(), sceneText().contains("\"meshId\": \"glass\""))
        assertEquals("glass", combo(p)!!.selectedItem)

        UndoManager.getInstance(project).undo(editor)
        assertEquals(before, sceneText())

        UndoManager.getInstance(project).redo(editor)
        assertTrue(sceneText().contains("\"meshId\": \"glass\""))
        combo(p)!!.selectedItem = ""
        assertFalse(sceneText().contains("meshId"))
    }

    fun testAStoredIdTheModelLacksStaysSelectableInAWarningState() {
        copyProject()
        storeMesh("old")
        val p = panel(listOf("body", "glass"))
        p.show(entity("0"))
        val box = combo(p)!!
        assertEquals(listOf("", "body", "glass", "old"), items(box))
        assertEquals("old", box.selectedItem)
        assertEquals("warning", box.getClientProperty("JComponent.outline"))
        assertTrue(box.toolTipText, box.toolTipText.contains("old"))
        box.selectedItem = "body"
        assertTrue(sceneText().contains("\"meshId\": \"body\""))
        assertFalse(sceneText().contains("old"))
    }

    fun testNoFieldWhenTheModelCannotBeRead() {
        copyProject()
        val p = panel(null)
        p.show(entity("0"))
        assertNull(combo(p))
    }

    fun testNoFieldForATerrain() {
        copyProject()
        val p = panel(listOf("body"))
        p.show(entity("1"))
        assertNull(combo(p))
        assertTrue("a terrain's meshes are never asked for", asked.isEmpty())
    }
}
