/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.water.InvalidWater
import net.nevinsky.abyssus.lib.core.editor.water.ValidWater
import net.nevinsky.abyssus.lib.core.editor.water.WaterEdit
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.plugin.water.WATER_ROW
import net.nevinsky.abyssus.plugin.water.WaterSceneEdits
import java.io.File

/** The scene's Water group: rows for saved surfaces, nothing for a scene without them, ECS rows untouched. */
class WaterTreeTest : BasePlatformTestCase() {
    private lateinit var scene: VirtualFile

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""")
        scene = myFixture.addFileToProject(
            "p/scenes/Main Scene.scene",
            File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText(),
        ).virtualFile
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }
    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    private fun sceneRow(): AbstractTreeNode<*> {
        val projectRow = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
        return children(children(projectRow).single { label(it) == "scenes" }).single()
    }

    private fun waterGroup(): AbstractTreeNode<*>? = children(sceneRow()).firstOrNull { label(it) == WATER_ROW }
    private fun entry(node: AbstractTreeNode<*>) = (node as DtoEntryNode).value

    private fun addLake(): String = WaterSceneEdits(project).add(scene, WaterPreset.LAKE, 1.0, 2.0, 3.0).id!!

    fun testSceneWithoutWaterHasNoWaterOrExtensionRow() {
        val rows = children(sceneRow()).map(::label)
        assertFalse(WATER_ROW in rows)
        assertFalse("abyssus" in rows)
        assertTrue("ecs" in rows)
    }

    fun testReadingASceneWritesNothing() {
        val before = FileDocumentManager.getInstance().getDocument(scene)!!.text
        waterGroup()
        assertEquals(before, FileDocumentManager.getInstance().getDocument(scene)!!.text)
    }

    fun testSavedSurfaceShowsUnderWaterAndEcsRowsRemain() {
        val id = addLake()
        val group = waterGroup()!!
        assertEquals("1", rowText(entry(group)).secondary)
        val row = children(group).single()
        assertEquals(id, entry(row).name)
        assertEquals("Lake", rowText(entry(row)).label)
        assertEquals("Abyssus only", rowText(entry(row)).secondary)
        assertEquals(true, entry(row).enabled)
        val ecs = children(sceneRow()).single { label(it) == "ecs" }
        val names = children(ecs).map { rowText(entry(it)).label }
        assertTrue(names.containsAll(listOf("Model 0", "Terrain", "Camera 4")))
    }

    fun testSurfacesAreListedInSavedOrder() {
        val first = addLake()
        val second = WaterSceneEdits(project).add(scene, WaterPreset.SEA, 0.0, 0.0, 0.0).id!!
        assertEquals(listOf(first, second), children(waterGroup()!!).map(::label))
        assertEquals(listOf("Lake", "Sea"), children(waterGroup()!!).map { rowText(entry(it)).label })
    }

    fun testHidingChangesOnlyTheEyeOfThatRow() {
        val id = addLake()
        val other = WaterSceneEdits(project).add(scene, WaterPreset.SEA, 0.0, 0.0, 0.0).id!!
        assertTrue(toggleRow(project, entry(children(waterGroup()!!).first { label(it) == id })))
        val rows = children(waterGroup()!!)
        assertEquals(false, entry(rows.first { label(it) == id }).enabled)
        assertEquals(true, entry(rows.first { label(it) == other }).enabled)
        assertEquals("Lake", rowText(entry(rows.first { label(it) == id })).label)
        assertTrue(toggleRow(project, entry(rows.first { label(it) == id })))
        assertEquals(true, entry(children(waterGroup()!!).first { label(it) == id }).enabled)
    }

    fun testInvalidSurfaceIsARemovableRowWithoutAnEye() {
        val document = FileDocumentManager.getInstance().getDocument(scene)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            val root = net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parse(document.text) as com.fasterxml.jackson.databind.node.ObjectNode
            root.putObject("abyssus").putObject("waterSurfaces").putObject("bad").put("preset", "LAKE").put("width", -1)
            document.setText(net.nevinsky.abyssus.lib.core.editor.document.SceneJson().pretty(root))
            FileDocumentManager.getInstance().saveDocument(document)
        }
        val row = children(waterGroup()!!).single()
        assertTrue(entry(row).value is InvalidWater)
        assertNull(entry(row).enabled)
        assertEquals("invalid", rowText(entry(row)).secondary)
        assertEquals(WaterEdit.Changed, WaterSceneEdits(project).remove(scene, "bad"))
        // the emptied container stays in the file, but a group is only shown for saved surfaces
        assertNull(waterGroup())
    }

    fun testWaterTargetNeedsAWaterRow() {
        addLake()
        val row = children(waterGroup()!!).single()
        assertNotNull(waterTargetOf(row))
        assertNull(waterTargetOf(waterGroup()))
        assertNull(waterTargetOf(sceneRow()))
        assertTrue(entry(row).value is ValidWater)
    }
}
