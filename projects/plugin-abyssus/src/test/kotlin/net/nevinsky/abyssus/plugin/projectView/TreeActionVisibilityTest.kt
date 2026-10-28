/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.actions.NonTrivialActionGroup
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.gameui.NewUiAssetAction

/**
 * Where each right-click action of the Abyssus view is offered: on the row it is made for, and nowhere else. The
 * Rename Scene action belongs to a scene row; the import and creation actions belong to the project's Assets row.
 */
class TreeActionVisibilityTest : BasePlatformTestCase() {
    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }
    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    private lateinit var projectRow: AbstractTreeNode<*>
    private lateinit var sceneRow: AbstractTreeNode<*>
    private lateinit var assetsRow: AbstractTreeNode<*>
    private lateinit var assetRow: AbstractTreeNode<*>
    private lateinit var groupRow: AbstractTreeNode<*>
    private lateinit var uiRow: AbstractTreeNode<*>

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""")
        myFixture.addFileToProject("p/scenes/Main.scene", """{"format":"abyssus","formatVersion":1,"name":"Main","ecs":{}}""")
        myFixture.addFileToProject("p/assets/tree/meta.json", """{"format":"abyssus","formatVersion":1,"version":1,"uuid":"u","type":"MODEL","additional":{}}""")
        projectRow = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }
        sceneRow = children(children(projectRow).single { label(it) == "scenes" }).single()
        assetsRow = children(projectRow).single { label(it) == "assets" }
        groupRow = children(assetsRow).single()
        assetRow = children(groupRow).single()
        uiRow = children(projectRow).single { label(it) == "ui" }
    }

    /** Whether [action] is visible and enabled with [node] selected; visible and enabled never differ for these. */
    private fun offered(action: AbyssusTreeAction<*>, node: Any?): Boolean {
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        assertEquals("visible and enabled agree", event.presentation.isVisible, event.presentation.isEnabled)
        return event.presentation.isEnabledAndVisible
    }

    private fun assertOnlyOnAssetsRow(make: (Any?) -> AbyssusTreeAction<*>) {
        assertTrue("Assets row", offered(make(assetsRow), assetsRow))
        assertFalse("scene row", offered(make(sceneRow), sceneRow))
        assertFalse("an asset row", offered(make(assetRow), assetRow))
        assertFalse("a type group", offered(make(groupRow), groupRow))
        assertFalse("the UI folder", offered(make(uiRow), uiRow))
        assertFalse("the project row", offered(make(projectRow), projectRow))
        assertFalse("nothing selected", offered(make(null), null))
    }

    fun testRenameSceneIsOfferedOnASceneRowOnly() {
        val make = { node: Any? -> object : RenameSceneAction() { override fun selected(e: AnActionEvent) = node } }
        assertTrue(offered(make(sceneRow), sceneRow))
        assertFalse(offered(make(assetsRow), assetsRow))
        assertFalse(offered(make(assetRow), assetRow))
        assertFalse("folder rows have no rename", offered(make(groupRow), groupRow))
        assertFalse("folder rows have no rename", offered(make(uiRow), uiRow))
        assertFalse(offered(make(projectRow), projectRow))
        assertFalse(offered(make(null), null))
    }

    fun testImportFlightGearIsOfferedOnTheAssetsRowOnly() =
        assertOnlyOnAssetsRow { node -> object : ImportFlightGearAction() { override fun selected(e: AnActionEvent) = node } }

    fun testImportModelIsOfferedOnTheAssetsRowOnly() =
        assertOnlyOnAssetsRow { node -> object : ImportModelAction() { override fun selected(e: AnActionEvent) = node } }

    fun testNewTerrainIsOfferedOnTheAssetsRowOnly() =
        assertOnlyOnAssetsRow { node -> object : NewTerrainAction() { override fun selected(e: AnActionEvent) = node } }

    fun testNewUiAssetIsOfferedOnTheAssetsRowOnly() =
        assertOnlyOnAssetsRow { node -> object : NewUiAssetAction() { override fun selected(e: AnActionEvent) = node } }

    fun testFolderRowsOfferNoComponentActions() {
        val add = { node: Any? -> object : AddComponentOnEcsAction() { override fun selected(e: AnActionEvent) = node } }
        for (row in listOf(assetsRow, groupRow, uiRow)) assertFalse(label(row), offered(add(row), row))
    }

    private fun groupOf(id: String): NonTrivialActionGroup =
        ActionManager.getInstance().getAction(id) as NonTrivialActionGroup

    private fun childIds(group: NonTrivialActionGroup): List<String> =
        (group as com.intellij.openapi.actionSystem.DefaultActionGroup).getChildren(null)
            .mapNotNull { ActionManager.getInstance().getId(it) }

    fun testImportModelGroupContainsFlightGearThenModel() {
        val group = groupOf("Abyssus.Menu.ImportModel")
        assertEquals(listOf("Abyssus.ImportFlightGear", "Abyssus.ImportModel"), childIds(group))
    }

    fun testNewGroupContainsUiAssetThenTerrainThenFoliage() {
        val group = groupOf("Abyssus.Menu.New")
        assertEquals(listOf("Abyssus.NewUiAsset", "Abyssus.NewTerrain", "Abyssus.NewFoliage"), childIds(group))
    }

    fun testAddGroupContainsAssetThenPathArrangementThenLightThenWater() {
        val group = groupOf("Abyssus.Menu.Add")
        assertEquals(listOf("Abyssus.AddAsset", "Abyssus.AddPathArrangement", "Abyssus.AddLight", "Abyssus.AddWater"), childIds(group))
    }
}
