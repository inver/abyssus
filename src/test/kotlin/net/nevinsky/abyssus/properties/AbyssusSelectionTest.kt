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

package net.nevinsky.abyssus.properties

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.projectView.AbyssusRootNode
import net.nevinsky.abyssus.projectView.AbyssusSelection
import net.nevinsky.abyssus.projectView.AbyssusSelectionListener
import net.nevinsky.abyssus.projectView.DtoEntryNode
import net.nevinsky.abyssus.projectView.assetFolderOf
import net.nevinsky.abyssus.projectView.describeNonAsset
import net.nevinsky.abyssus.projectView.publishSelectionOf
import java.io.File
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class AbyssusSelectionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun copyProject() {
        val dir = "Untitled"
        myFixture.copyFileToProject("$dir/Untitled.abss", "$dir/Untitled.abss")
        myFixture.copyFileToProject("$dir/scenes/Main Scene.scene", "$dir/scenes/Main Scene.scene")
        File("$testDataPath/$dir/assets").listFiles { f -> f.isDirectory }!!.forEach {
            myFixture.copyFileToProject("$dir/assets/${it.name}/meta.json", "$dir/assets/${it.name}/meta.json")
        }
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }

    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    private fun abssNode(): AbyssusAssetNode =
        children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") } as AbyssusAssetNode

    private fun assetNodes() = children(children(abssNode()).single { label(it) == "assets" })

    // 2.1 publishing

    private fun withTree(block: (javax.swing.JTree, DefaultMutableTreeNode, DefaultMutableTreeNode) -> Unit) {
        val root = DefaultMutableTreeNode("root")
        val a = DefaultMutableTreeNode("a").also { root.add(it) }
        val b = DefaultMutableTreeNode("b").also { root.add(it) }
        val tree = javax.swing.JTree(DefaultTreeModel(root))
        publishSelectionOf(tree, project)
        block(tree, a, b)
    }

    fun testSelectingFiresTheTopicOnceAndClearingFiresNull() {
        val seen = mutableListOf<Any?>()
        project.messageBus.connect(testRootDisposable).subscribe(AbyssusSelectionListener.TOPIC, AbyssusSelectionListener { seen += it })
        withTree { tree, a, b ->
            tree.selectionPath = TreePath(a.path)
            assertEquals(listOf<Any?>("a"), seen)
            assertEquals("a", AbyssusSelection.of(project).current)
            tree.selectionPath = TreePath(b.path)
            assertEquals(listOf<Any?>("a", "b"), seen)
            tree.clearSelection()
            assertEquals(listOf("a", "b", null), seen)
            assertNull(AbyssusSelection.of(project).current)
        }
    }

    // 2.2 resolving

    fun testAnAssetRowResolvesToItsFolder() {
        copyProject()
        val sky = assetNodes().single { (it as DtoEntryNode).label == "skybox_default" }
        val folder = assetFolderOf(sky)!!
        assertEquals("skybox_default", folder.name)
        assertNotNull(folder.findChild("meta.json"))
    }

    fun testEveryAssetRowResolves() {
        copyProject()
        assertEquals(8, assetNodes().size)
        assertTrue(assetNodes().all { assetFolderOf(it) != null })
    }

    fun testSceneProjectFilePropertyRowAndNothingDoNotResolve() {
        copyProject()
        val abss = abssNode()
        assertNull(assetFolderOf(abss))
        val scenes = children(abss).single { label(it) == "scenes" }
        val scene = children(scenes).single()
        assertNull(assetFolderOf(scene))
        assertNull(assetFolderOf(children(abss).first { label(it) == "assets" }))
        assertNull(assetFolderOf(children(scene).first()))
        assertNull(assetFolderOf(null))
    }

    fun testNonAssetsAreDescribedByNameAndKind() {
        copyProject()
        val abss = abssNode()
        assertEquals("Untitled.abss" to "the project file", describeNonAsset(abss))
        val scene = children(children(abss).single { label(it) == "scenes" }).single()
        assertEquals("a scene", describeNonAsset(scene)!!.second)
        val ecs = children(scene).single { label(it) == "ecs" }
        assertEquals("ecs" to "an entity or setting", describeNonAsset(ecs))
        assertNull(describeNonAsset(null))
    }
}
