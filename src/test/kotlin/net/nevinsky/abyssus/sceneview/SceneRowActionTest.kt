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

package net.nevinsky.abyssus.sceneview

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.projectView.AbyssusRootNode
import net.nevinsky.abyssus.projectView.viewableSceneFile

class SceneRowActionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun text(node: AbstractTreeNode<*>): String {
        node.update()
        val p: PresentationData = node.presentation
        return p.presentableText ?: p.coloredText.joinToString("") { it.text }
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }

    private fun projectFixture(): VirtualFile {
        myFixture.copyFileToProject("Untitled/Untitled.abss", "Untitled/Untitled.abss")
        myFixture.copyFileToProject("Untitled/scenes/Main Scene.scene", "Untitled/scenes/Main Scene.scene")
        return myFixture.findFileInTempDir("Untitled")
    }

    private fun projectNode() =
        children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { text(it).startsWith("Untitled") }

    fun testStandaloneSceneNodeHasViewTarget() {
        val file = myFixture.addFileToProject("Loose/a.scene", """{"name":"a"}""").virtualFile
        assertEquals(file, viewableSceneFile(AbyssusAssetNode(project, file, ViewSettings.DEFAULT)))
    }

    fun testProjectNodeAndScalarEntriesHaveNoViewTarget() {
        projectFixture()
        val projectNode = projectNode()
        assertNull(viewableSceneFile(projectNode))
        val scenes = children(projectNode).single { text(it).startsWith("Scenes") }
        assertNull(viewableSceneFile(scenes))
        assertNull(viewableSceneFile(children(projectNode).single { text(it).startsWith("name") }))
    }

    fun testSceneEntryInsideProjectHasViewTarget() {
        val dir = projectFixture()
        val scenes = children(projectNode()).single { text(it).startsWith("Scenes") }
        val sceneNode = children(scenes).single()
        assertEquals(dir.findFileByRelativePath("scenes/Main Scene.scene"), viewableSceneFile(sceneNode))
    }

    fun testSceneInternalsAndNonNodesHaveNoViewTarget() {
        val file = myFixture.addFileToProject("Loose/b.scene", """{"name":"b","fogEnabled":true,"fog":{"density":0.1}}""").virtualFile
        val sceneNode = AbyssusAssetNode(project, file, ViewSettings.DEFAULT)
        for (child in children(sceneNode)) assertNull(text(child), viewableSceneFile(child))
        assertNull(viewableSceneFile(null))
        assertNull(viewableSceneFile("x"))
    }

    fun testUppercaseExtensionIsNotAScene() {
        val file = myFixture.addFileToProject("Loose/c.SCENE", "{}").virtualFile
        assertNull(viewableSceneFile(AbyssusAssetNode(project, file, ViewSettings.DEFAULT)))
    }
}
