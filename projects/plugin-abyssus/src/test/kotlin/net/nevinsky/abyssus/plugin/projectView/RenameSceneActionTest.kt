/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.projectView.sceneNameOf

class RenameSceneActionTest : BasePlatformTestCase() {
    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }
    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    private lateinit var standaloneRow: AbstractTreeNode<*>
    private lateinit var projectRow: AbstractTreeNode<*>
    private lateinit var sceneRow: AbstractTreeNode<*>
    private lateinit var assetsRow: AbstractTreeNode<*>

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("Standalone.scene", """{"format":"abyssus","formatVersion":1,"name":"Standalone","ecs":{"entities":{}}}""")
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""")
        myFixture.addFileToProject("p/scenes/Main.scene", """{"format":"abyssus","formatVersion":1,"name":"Main","ecs":{"entities":{}}}""")
        val root = AbyssusRootNode(project, ViewSettings.DEFAULT)
        standaloneRow = children(root).single { label(it) == "Standalone.scene" }
        projectRow = children(root).single { label(it).endsWith(".abss") }
        sceneRow = children(children(projectRow).single { label(it) == "scenes" }).single()
        assetsRow = children(projectRow).single { label(it) == "assets" }
    }

    private fun offered(action: AbyssusTreeAction<*>, node: Any?): Boolean {
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        return event.presentation.isEnabledAndVisible
    }

    private val make = { node: Any? -> object : RenameSceneAction() { override fun selected(e: AnActionEvent) = node } }

    fun testRenameSceneIsOfferedOnStandaloneAndProjectScenes() {
        assertTrue("standalone scene", offered(make(standaloneRow), standaloneRow))
        assertTrue("scene under a project", offered(make(sceneRow), sceneRow))
        assertFalse("assets row", offered(make(assetsRow), assetsRow))
        assertFalse("nothing selected", offered(make(null), null))
    }

    fun testInitialNameComesFromTheSceneFile() {
        val standaloneFile = (standaloneRow as AbyssusAssetNode).virtualFile
        assertEquals("Standalone", sceneNameOf(project, standaloneFile))
        val projectFile = sceneFileOf(sceneRow.value as DtoEntry)!!
        assertEquals("Main", sceneNameOf(project, projectFile))
    }

    fun testRenamingWritesOnlyName() {
        val standaloneFile = (standaloneRow as AbyssusAssetNode).virtualFile
        val original = String(standaloneFile.contentsToByteArray())
        assertTrue(renameScene(project, standaloneFile, "Renamed"))
        val updated = String(standaloneFile.contentsToByteArray())
        assertEquals(original.replace("\"name\":\"Standalone\"", "\"name\":\"Renamed\""), updated)
    }
}
