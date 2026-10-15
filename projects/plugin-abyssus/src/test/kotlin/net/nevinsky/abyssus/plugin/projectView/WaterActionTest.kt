/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.water.WaterCodec
import net.nevinsky.abyssus.lib.core.editor.water.WaterEdit
import net.nevinsky.abyssus.lib.core.water.WaterField
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.water.WATER_ROW
import net.nevinsky.abyssus.plugin.water.WaterSceneEdits
import java.io.File

class WaterActionTest : BasePlatformTestCase() {
    private lateinit var scene: VirtualFile
    private val original = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""")
        scene = myFixture.addFileToProject("p/scenes/Main Scene.scene", original).virtualFile
    }

    private fun text() = FileDocumentManager.getInstance().getDocument(scene)!!.text
    private fun surfaces() = WaterCodec().read(SceneJson().parse(text()).get("abyssus"))
    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }
    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    private fun sceneRow(): AbstractTreeNode<*> {
        val projectRow = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
        return children(children(projectRow).single { label(it) == "scenes" }).single()
    }

    private fun waterRow(): AbstractTreeNode<*> = children(children(sceneRow()).single { label(it) == WATER_ROW }).first()

    private fun offered(action: AbyssusTreeAction<*>): Boolean {
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        assertEquals(event.presentation.isVisible, event.presentation.isEnabled)
        return event.presentation.isEnabledAndVisible
    }

    fun testAddLakeAtThePlacementPointSelectsItAndKeepsTheRest() {
        val group = AddWaterGroup(project, scene, { Vec3(5f, 1.5f, -2f) })
        group.addWater(WaterPreset.LAKE)
        val lake = surfaces().valid.single()
        assertEquals("Lake", lake.name)
        assertEquals(Triple(5.0, 1.5, -2.0), Triple(lake.x, lake.level, lake.z))
        val selected = waterTargetOf(AbyssusSelection.of(project).current)!!
        assertEquals(lake.id, selected.id)
        assertEquals(scene, selected.file)
        assertEquals(SceneJson().parse(original).get("ecs"), SceneJson().parse(text()).get("ecs"))
    }

    fun testAddSeaFromTheTreeIsCentredAtTheOriginAndBiggerThanALake() {
        AddWaterGroup(project, scene, { Vec3(0f, 0f, 0f) }).addWater(WaterPreset.LAKE)
        AddWaterGroup(project, scene, { Vec3(0f, 0f, 0f) }).addWater(WaterPreset.SEA)
        val (lake, sea) = surfaces().valid
        assertEquals("Sea", sea.name)
        assertEquals(Triple(0.0, 0.0, 0.0), Triple(sea.x, sea.level, sea.z))
        assertTrue(sea.width > lake.width && sea.waveAmplitude > lake.waveAmplitude)
    }

    fun testSecondLakeGetsANumberAndLevelsStayIndependent() {
        val edits = WaterSceneEdits(project)
        val first = edits.add(scene, WaterPreset.SEA, 0.0, 0.0, 0.0).id!!
        val second = edits.add(scene, WaterPreset.LAKE, 0.0, 0.0, 0.0).id!!
        edits.add(scene, WaterPreset.LAKE, 0.0, 0.0, 0.0)
        assertEquals(WaterEdit.Changed, edits.setField(scene, second, WaterField.LEVEL, 9.0))
        val all = surfaces().valid
        assertEquals(listOf("Sea", "Lake", "Lake 2"), all.map { it.name })
        assertEquals(0.0, all.first { it.id == first }.level, 0.0)
        assertEquals(9.0, all.first { it.id == second }.level, 0.0)
    }

    fun testAddIsRefusedWhenTheExtensionIsNotAnObject() {
        val odd = myFixture.addFileToProject("odd.scene", """{"format":"abyssus","formatVersion":1,"abyssus":[]}""").virtualFile
        assertFalse(canAddWater(odd, SceneDocumentCache.of(project)))
        assertTrue(canAddWater(scene, SceneDocumentCache.of(project)))
    }

    fun testActionsAreOfferedOnTheirRowsOnly() {
        WaterSceneEdits(project).add(scene, WaterPreset.LAKE, 0.0, 0.0, 0.0)
        val sceneRow = sceneRow()
        val waterRow = waterRow()
        val make = { node: Any? -> object : AddWaterAction() { override fun selected(e: AnActionEvent) = node } }
        assertTrue(offered(make(sceneRow)))
        assertFalse(offered(make(waterRow)))
        assertFalse(offered(make(null)))
        val rename = { node: Any? -> object : RenameWaterAction() { override fun selected(e: AnActionEvent) = node } }
        val remove = { node: Any? -> object : RemoveWaterAction() { override fun selected(e: AnActionEvent) = node } }
        for (make2 in listOf(rename, remove)) {
            assertTrue(offered(make2(waterRow)))
            assertFalse(offered(make2(sceneRow)))
            assertFalse(offered(make2(null)))
        }
    }

    fun testRemoveActionRemovesOnlyThatSurface() {
        val edits = WaterSceneEdits(project)
        val first = edits.add(scene, WaterPreset.LAKE, 0.0, 0.0, 0.0).id!!
        val second = edits.add(scene, WaterPreset.SEA, 0.0, 0.0, 0.0).id!!
        val target = waterTargetOf(children(children(sceneRow()).single { label(it) == WATER_ROW }.let { it }).first())!!
        assertEquals(first, target.id)
        assertEquals(WaterEdit.Changed, edits.remove(target.file, target.id))
        assertEquals(listOf(second), surfaces().valid.map { it.id })
    }

    fun testBundleKeysResolve() {
        for (key in listOf(
            "addWaterTitle", "addWaterTooltip", "waterPresetLake", "waterPresetSea", "treeWater", "treeWaterAbyssusOnly",
            "treeWaterInvalid", "renameWaterTitle", "renameWaterPrompt", "removeWaterTitle", "waterRejectedTitle",
            "waterErrorIncompatible", "waterErrorBlankName", "waterErrorNotFound", "waterErrorIdCollision", "waterErrorInvalidSurface",
            "waterIssueNotFinite", "waterIssueNotPositive", "waterIssueNegative", "waterIssueUnitRange", "waterIssueNotNumber",
            "commandAddWater", "commandEditWater", "commandMoveWater", "commandRenameWater", "commandToggleWater", "commandRemoveWater",
        ) + WaterField.entries.map { "waterField" + it.name.split('_').joinToString("") { p -> p.lowercase().replaceFirstChar(Char::uppercase) } }) {
            val text = AbyssusBundle.message(key)
            assertTrue("$key -> $text", text.isNotBlank() && !text.startsWith("!"))
        }
    }
}
