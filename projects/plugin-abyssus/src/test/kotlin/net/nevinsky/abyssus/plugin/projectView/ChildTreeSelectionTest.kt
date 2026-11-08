/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.tree.TreeVisitor
import net.nevinsky.abyssus.lib.core.editor.parseScene

/**
 * OpenSpec `abyssus-project-view`, "Selecting a child in the view reveals it": the tree walk to a child's row goes
 * through (and so expands) its parent rows.
 */
class ChildTreeSelectionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private lateinit var scene: VirtualFile

    override fun setUp() {
        super.setUp()
        myFixture.copyFileToProject("Untitled/Untitled.abss", "Untitled/Untitled.abss")
        scene = myFixture.copyFileToProject("Untitled/scenes/Main Scene.scene", "Untitled/scenes/Main Scene.scene")
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }

    private fun label(node: AbstractTreeNode<*>): String =
        (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    fun testTheHandleIsReachedThroughItsCamera() {
        val root = AbyssusRootNode(project, ViewSettings.DEFAULT)
        val abss = children(root).single { label(it).endsWith(".abss") }
        val scenes = children(abss).single { label(it) == "scenes" }
        val sceneNode = children(scenes).single()
        val ecs = children(sceneNode).single { label(it) == "ecs" }
        val camera = children(ecs).single { label(it) == "4" }
        val handle = children(camera).single { label(it) == "3" }
        val ancestors = treeAncestors(parseScene(String(scene.contentsToByteArray())), 3L)
        assertEquals(listOf(4L), ancestors)
        val chain = listOf(root, abss, scenes, sceneNode, ecs, camera, handle)
        // the camera's row is walked into (expanded) on the way to the handle
        assertEquals(TreeVisitor.Action.CONTINUE, entityVisitAction(scene, 3L, chain.dropLast(1), ancestors))
        assertEquals(TreeVisitor.Action.INTERRUPT, entityVisitAction(scene, 3L, chain, ancestors))
        // without the ancestors the handle would be looked for directly under ecs
        assertEquals(TreeVisitor.Action.SKIP_CHILDREN, entityVisitAction(scene, 3L, chain.dropLast(1)))
    }

    fun testARootHasNoAncestors() {
        assertEquals(emptyList<Long>(), treeAncestors(parseScene(String(scene.contentsToByteArray())), 4L))
    }
}
