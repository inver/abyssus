/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.tree.TreeVisitor
import net.nevinsky.abyssus.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.projectView.AbyssusRootNode
import net.nevinsky.abyssus.projectView.DtoEntryNode
import net.nevinsky.abyssus.projectView.entityVisitAction

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class EntitySelectionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private lateinit var scene: VirtualFile

    override fun setUp() {
        super.setUp()
        myFixture.copyFileToProject("Untitled/Untitled.abss", "Untitled/Untitled.abss")
        scene = myFixture.copyFileToProject("Untitled/scenes/Main Scene.scene", "Untitled/scenes/Main Scene.scene")
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }

    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    /** The node chain from the view root down to the entity row, found by name the way the tree would be walked. */
    private fun chainTo(entityId: String): List<AbstractTreeNode<*>> {
        val root = AbyssusRootNode(project, ViewSettings.DEFAULT)
        val abss = children(root).single { label(it).endsWith(".abss") }
        val scenes = children(abss).single { label(it) == "scenes" }
        val sceneNode = children(scenes).single()
        val ecs = children(sceneNode).single { label(it) == "ecs" }
        val entities = children(ecs).single { label(it) == "entities" }
        val entity = children(entities).single { label(it) == entityId }
        return listOf(root, abss, scenes, sceneNode, ecs, entities, entity)
    }

    fun testWalkingDownFindsTheEntityRow() {
        val chain = chainTo("0")
        for (end in 1 until chain.size - 1) {
            assertEquals("at ${label(chain[end])}", TreeVisitor.Action.CONTINUE, entityVisitAction(scene, "0", chain.subList(0, end + 1)))
        }
        assertEquals(TreeVisitor.Action.INTERRUPT, entityVisitAction(scene, "0", chain))
    }

    fun testOtherEntitiesAndSiblingsAreSkipped() {
        val chain = chainTo("0")
        // the row of entity 2 is not the row of entity 0
        assertEquals(TreeVisitor.Action.SKIP_CHILDREN, entityVisitAction(scene, "2", chain))
        // a sibling of `ecs` inside the scene leads nowhere
        val sceneNode = chain[3]
        val other = children(sceneNode).first { label(it) != "ecs" }
        assertEquals(TreeVisitor.Action.SKIP_CHILDREN, entityVisitAction(scene, "0", chain.subList(0, 4) + other))
    }

    fun testOtherProjectsAreNotEntered() {
        val root = AbyssusRootNode(project, ViewSettings.DEFAULT)
        val other = myFixture.addFileToProject("Other/Other.abss", "{}").virtualFile
        val node = AbyssusAssetNode(project, other, ViewSettings.DEFAULT)
        assertEquals(TreeVisitor.Action.SKIP_CHILDREN, entityVisitAction(scene, "0", listOf(root, node)))
    }

    fun testNonScenesEntriesOfTheProjectAreSkipped() {
        val root = AbyssusRootNode(project, ViewSettings.DEFAULT)
        val abss = children(root).single { label(it).endsWith(".abss") }
        val notScenes = children(abss).first { label(it) != "scenes" }
        assertEquals(TreeVisitor.Action.SKIP_CHILDREN, entityVisitAction(scene, "0", listOf(root, abss, notScenes)))
    }
}
