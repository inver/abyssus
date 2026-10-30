/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

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

    private fun groups() = assetsNode().children.map { it as AbstractTreeNode<*> }

    private fun listed() = groups().flatMap { it.children.map { c -> c as AbstractTreeNode<*> } }.map { text(it).substringBefore("  ") }

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

    fun testGroupWithoutUnusedAssetsIsHiddenAndCountsFollow() {
        val scene = myFixture.findFileInTempDir("p/scenes/S.scene")
        com.intellij.openapi.application.runWriteAction {
            com.intellij.openapi.vfs.VfsUtil.saveText(scene, """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"0":{"components":{"RenderComponent":{"renderable":{"asset":{"type":"MODEL","assetName":"used"}}}}},"1":{"components":{"RenderComponent":{"renderable":{"asset":{"type":"TERRAIN","assetName":"hills"}}}}}}}}""")
        }
        myFixture.addFileToProject("p/assets/hills/meta.json", """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"uuid":"${java.util.UUID.nameUUIDFromBytes("h1".toByteArray())}","type":"TERRAIN","additional":{}}""")
        assertEquals(listOf("Models  4", "Terrains  1"), groups().map { text(it) })
        UnusedFilter.set(project, true)
        assertEquals(listOf("Models  3"), groups().map { text(it) })
        assertEquals("Assets  3", text(assetsNode()))
    }

    fun testPressedStateShowsTheAmberIcon() {
        val action = UnusedFilterAction()
        val off = TestActionEvent.createTestEvent(action)
        action.update(off)
        assertSame(net.nevinsky.abyssus.plugin.filetype.TreeActionIcons.UNUSED_FILTER, off.presentation.icon)
        UnusedFilter.set(project, true)
        val on = TestActionEvent.createTestEvent(action)
        action.update(on)
        assertSame(net.nevinsky.abyssus.plugin.filetype.TreeActionIcons.UNUSED_FILTER_SELECTED, on.presentation.icon)
    }
}
