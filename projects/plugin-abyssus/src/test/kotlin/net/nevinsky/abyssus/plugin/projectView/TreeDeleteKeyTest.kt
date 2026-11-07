/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.DeleteProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/** Delete (and Backspace) on an entity or component row of the Abyssus tree removes it from the scene. */
class TreeDeleteKeyTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private lateinit var sceneFile: com.intellij.openapi.vfs.VirtualFile

    private fun copyProject() {
        val dir = "Untitled"
        myFixture.copyFileToProject("$dir/Untitled.abss", "$dir/Untitled.abss")
        myFixture.copyFileToProject("$dir/scenes/Main Scene.scene", "$dir/scenes/Main Scene.scene")
        sceneFile = myFixture.findFileInTempDir("$dir/scenes/Main Scene.scene")
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }

    private fun label(node: AbstractTreeNode<*>): String =
        (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name

    private fun descend(from: AbstractTreeNode<*>, vararg names: String) =
        names.fold(from) { node, name -> children(node).single { label(it) == name } }

    private fun abss() = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }

    private fun sceneNode() = children(descend(abss(), "scenes")).single()

    private fun ids() =
        net.nevinsky.abyssus.plugin.parsedScene(FileDocumentManager.getInstance().getDocument(sceneFile)!!.text)
            .ecs.keys.toList()

    /** An Abyssus tree whose only, selected row holds [node]. */
    private fun treeSelecting(node: Any): EyeTree {
        val row = DefaultMutableTreeNode(node)
        val tree = EyeTree(DefaultTreeModel(DefaultMutableTreeNode("root").apply { add(row) }), project)
        tree.selectionPath = TreePath(row.path)
        return tree
    }

    /** The values [tree] puts in the data context; the headless DataManager does not ask components. */
    private class RecordingSink : DataSink {
        val values = mutableMapOf<DataKey<*>, Any?>()

        override fun <T : Any> set(key: DataKey<T>, data: T?) {
            values[key] = data
        }

        override fun <T : Any> setNull(key: DataKey<T>) = Unit

        override fun <T : Any> lazyValue(key: DataKey<T>, data: (DataMap) -> T?) = Unit

        override fun <T : Any> lazyNull(key: DataKey<T>) = Unit

        override fun uiDataSnapshot(provider: UiDataProvider) = Unit

        override fun dataSnapshot(provider: DataSnapshotProvider) = Unit

        override fun uiDataSnapshot(provider: DataProvider) = Unit
    }

    private fun deleteProviderOf(tree: EyeTree): DeleteProvider? =
        RecordingSink().also { tree.uiDataSnapshot(it) }.values[PlatformDataKeys.DELETE_ELEMENT_PROVIDER] as DeleteProvider?

    fun testDeleteRemovesTheSelectedEntity() {
        copyProject()
        val tree = treeSelecting(descend(sceneNode(), "ecs", "0"))
        val context = DataContext.EMPTY_CONTEXT
        val provider = deleteProviderOf(tree)
        assertInstanceOf(provider, RowDeleteProvider::class.java)
        assertTrue(provider!!.canDeleteElement(context))
        provider.deleteElement(context)
        assertEquals((0L..10L).toList() - 0L, ids())
    }

    fun testOtherRowsKeepThePlatformProvider() {
        copyProject()
        assertFalse(deleteProviderOf(treeSelecting(descend(sceneNode(), "ecs"))) is RowDeleteProvider)
        assertFalse(deleteProviderOf(treeSelecting(sceneNode())) is RowDeleteProvider)
    }

    fun testBackspaceTargetsEntitiesAndModeledComponents() {
        copyProject()
        assertEquals(0L, removableSelectionOf(treeSelecting(descend(sceneNode(), "ecs", "0")))?.entityId)
        val name = removableSelectionOf(treeSelecting(descend(sceneNode(), "ecs", "1", "NameComponent")))
        assertEquals("NameComponent", name?.kind)
        assertNull(removableSelectionOf(treeSelecting(descend(sceneNode(), "ecs"))))
    }
}
