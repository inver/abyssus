/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class UnusedFilterTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        UnusedFilter.set(project, false)
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""")
        myFixture.addFileToProject("p/scenes/S.scene", """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"0":{"components":{"RenderComponent":{"renderable":{"asset":{"type":"MODEL","assetName":"used"}}}}}}}}""")
        for ((name, uuid) in listOf("used" to "u1", "dead1" to "u2", "dead2" to "u3", "dead3" to "u4")) {
            myFixture.addFileToProject("p/assets/$name/meta.json", """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"uuid":"${java.util.UUID.nameUUIDFromBytes(uuid.toByteArray())}","type":"MODEL","additional":{}}""")
        }
    }

    override fun tearDown() {
        try {
            UnusedFilter.set(project, false)
        } finally {
            super.tearDown()
        }
    }

    private fun text(node: AbstractTreeNode<*>): String {
        node.update()
        return node.presentation.coloredText.joinToString("") { it.text }
    }

    private fun assetsNode(): AbstractTreeNode<*> {
        val abss = AbyssusRootNode(project, ViewSettings.DEFAULT).children.single { it is AbyssusAssetNode } as AbstractTreeNode<*>
        return abss.children.map { it as AbstractTreeNode<*> }.single { text(it).startsWith("Assets") }
    }

    private fun listed() = assetsNode().children.map { text(it as AbstractTreeNode<*>).substringBefore("  ") }

    fun testOffByDefaultListsEveryAsset() {
        assertEquals(listOf("dead1", "dead2", "dead3", "used"), listed())
        assertEquals("Assets  4", text(assetsNode()))
    }

    fun testOnListsOnlyUnusedAssetsAndTheCountFollows() {
        UnusedFilter.set(project, true)
        assertEquals(listOf("dead1", "dead2", "dead3"), listed())
        assertEquals("Assets  3", text(assetsNode()))
    }

    fun testTurningItOffRestoresAllAssets() {
        UnusedFilter.set(project, true)
        UnusedFilter.set(project, false)
        assertEquals(4, listed().size)
    }

    fun testScenesAreNotAffected() {
        UnusedFilter.set(project, true)
        val abss = AbyssusRootNode(project, ViewSettings.DEFAULT).children.single { it is AbyssusAssetNode } as AbstractTreeNode<*>
        val scenes = abss.children.map { it as AbstractTreeNode<*> }.single { text(it).startsWith("Scenes") }
        assertEquals("Scenes  1", text(scenes))
    }

    fun testToggleActionFlipsTheStateAndShowsInTheToolbarGroup() {
        val action = UnusedFilterAction()
        val dataContext = com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext(project)
        fun event(): AnActionEvent = TestActionEvent.createTestEvent(action, dataContext)
        assertFalse(action.isSelected(event()))
        action.setSelected(event(), true)
        assertTrue(UnusedFilter.isOn(project))
        assertTrue(action.isSelected(event()))
        action.setSelected(event(), false)
        assertFalse(UnusedFilter.isOn(project))
        assertEquals("Show Only Unused Assets", action.templatePresentation.text)
    }
}
