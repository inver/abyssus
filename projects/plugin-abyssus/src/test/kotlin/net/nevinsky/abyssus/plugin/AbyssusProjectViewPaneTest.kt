/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.plugin.filetype.editSceneJson
import net.nevinsky.abyssus.plugin.projectView.AbyssusProjectViewPane
import net.nevinsky.abyssus.plugin.projectView.AbyssusTreeRefresh
import net.nevinsky.abyssus.plugin.projectView.DtoEntryNode
import net.nevinsky.abyssus.plugin.projectView.isEntityEntry
import net.nevinsky.abyssus.plugin.projectView.renameScene
import javax.swing.JTree
import javax.swing.UIManager
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class AbyssusProjectViewPaneTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    /** ProjectViewImpl.addProjectPane logs a SEVERE error unless the pane's select-in target reports the pane's own id. */
    fun testSelectInTargetMatchesPaneId() {
        val pane = AbyssusProjectViewPane(project)
        try {
            val target = pane.createSelectInTarget()
            assertEquals(pane.id, target.minorViewId)
            assertFalse(target.javaClass.name, target.javaClass.name.endsWith("ProjectPaneSelectInTarget"))
        } finally {
            Disposer.dispose(pane)
        }
    }

    // the project is a copy of the Tree fixture (the Untitled shape): the project file, one scene, no binary assets

    private fun fixture(): VirtualFile {
        myFixture.copyFileToProject("Tree/Untitled.abss", "Untitled/Untitled.abss")
        val sceneText = java.io.File("$testDataPath/Tree/scenes/Main Scene.scene").readText()
        myFixture.addFileToProject("Untitled/scenes/Main Scene.scene", SceneJson().pretty(SceneJson().parse(sceneText)))
        return myFixture.findFileInTempDir("Untitled")
    }

    private val originalName: String by lazy {
        SceneJson().parse(java.io.File("$testDataPath/Tree/scenes/Main Scene.scene").readText()).get("name").asText()
    }

    private val panes = mutableListOf<AbyssusProjectViewPane>()

    private fun open(): AbyssusProjectViewPane {
        // Headless runs keep Swing's Metal look and feel, whose expand/collapse icons throw when painted without a parent.
        UIManager.put("Tree.expandedIcon", AllIcons.General.ArrowDown)
        UIManager.put("Tree.collapsedIcon", AllIcons.General.ArrowRight)
        val pane = AbyssusProjectViewPane(project)
        panes += pane
        pane.createComponent()
        pane.updateFromRoot(true)
        expand(pane)
        return pane
    }

    override fun tearDown() {
        try {
            panes.forEach { if (!Disposer.isDisposed(it)) Disposer.dispose(it) }
        } finally {
            super.tearDown()
        }
    }

    private fun expand(pane: AbyssusProjectViewPane) {
        PlatformTestUtil.waitForPromise(TreeUtil.promiseExpand(pane.tree, 8), 30_000)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    /** Waits until the pane has rebuilt [times] times in all and nothing is scheduled, then lets the tree finish loading. */
    private fun settle(pane: AbyssusProjectViewPane, times: Int) {
        PlatformTestUtil.waitWithEventsDispatching("tree refresh $times", { pane.refresh.rebuilds >= times && pane.refresh.idle }, 30)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        PlatformTestUtil.waitWhileBusy(pane.tree)
    }

    /** The platform loads children in the background after a rebuild: wait for [condition] while the tree catches up. */
    private fun await(message: String, condition: () -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching(message, condition, 30)
    }

    private fun walk(model: TreeModel, node: Any = model.root, path: TreePath = TreePath(model.root), out: MutableList<TreePath> = ArrayList()): List<TreePath> {
        out += path
        for (i in 0 until model.getChildCount(node)) {
            val child = model.getChild(node, i)
            walk(model, child, path.pathByAddingChild(child), out)
        }
        return out
    }

    private fun paths(tree: JTree): List<TreePath> = walk(tree.model)

    private fun textOf(node: AbstractTreeNode<*>): String {
        node.update()
        return node.presentation.presentableText ?: node.presentation.coloredText.joinToString("") { it.text }
    }

    private fun texts(pane: AbyssusProjectViewPane): List<String> =
        paths(pane.tree).mapNotNull { TreeUtil.getUserObject(it.lastPathComponent) as? AbstractTreeNode<*> }.map(::textOf)

    private fun entries(pane: AbyssusProjectViewPane) =
        paths(pane.tree).mapNotNull { (TreeUtil.getUserObject(it.lastPathComponent) as? DtoEntryNode)?.value }

    private fun entityPath(pane: AbyssusProjectViewPane, id: String): TreePath? =
        paths(pane.tree).firstOrNull { (TreeUtil.getUserObject(it.lastPathComponent) as? DtoEntryNode)?.value?.let { e -> isEntityEntry(e) && e.name == id } == true }

    private fun sceneFile(dir: VirtualFile) = dir.findFileByRelativePath("scenes/Main Scene.scene")!!

    private fun scenePart(text: String) = text.substringBefore(" (")

    private fun hasSceneLabeled(pane: AbyssusProjectViewPane, name: String) = texts(pane).any { scenePart(it) == name && it.endsWith(")") }

    private fun unsaved(file: VirtualFile): Boolean = FileDocumentManager.getInstance().isFileModified(file)

    private fun editorOf(file: VirtualFile) = run {
        myFixture.openFileInEditor(file)
        TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
    }

    // 5.4 the spec scenarios

    fun testRenameUndoRedoFollowsLabelsWithNoExtraUndoEntry() {
        val dir = fixture()
        val file = sceneFile(dir)
        val editor = editorOf(file)
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val originalText = document.text
        val pane = open()
        assertTrue(hasSceneLabeled(pane, originalName))
        assertTrue(renameScene(project, file, "Forest"))
        settle(pane, 1)
        await("Forest label") { hasSceneLabeled(pane, "Forest") }
        UndoManager.getInstance(project).undo(editor)
        settle(pane, 2)
        await("original label") { hasSceneLabeled(pane, originalName) }
        assertEquals("one Undo undid the rename: the refresh added no undo entry of its own", originalText, document.text)
        UndoManager.getInstance(project).redo(editor)
        settle(pane, 3)
        await("Forest label again") { hasSceneLabeled(pane, "Forest") }
        assertFalse("nothing but the rename is left to redo", UndoManager.getInstance(project).isRedoAvailable(editor))
    }

    fun testUnsavedFogEditAndItsUndoReachTheFogEye() {
        val dir = fixture()
        val file = sceneFile(dir)
        val editor = editorOf(file)
        val diskBefore = file.contentsToByteArray().toList()
        val pane = open()
        fun fogEnabled() = entries(pane).first { it.name == "fog" && it.source == file }.enabled
        assertEquals(true, fogEnabled())
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) {
            val text = document.text
            val at = Regex("\"fogEnabled\"\\s*:\\s*true").find(text)!!.range
            document.replaceString(at.first, at.last + 1, "\"fogEnabled\": false")
        }
        settle(pane, 1)
        await("fog eye off") { fogEnabled() == false }
        assertTrue("the edit stays unsaved", unsaved(file))
        UndoManager.getInstance(project).undo(editor)
        settle(pane, 2)
        await("fog eye on") { fogEnabled() == true }
        assertEquals("nothing was saved", diskBefore, file.contentsToByteArray().toList())
    }

    fun testExternalSceneReplacementIsShownWithoutReopening() {
        val dir = fixture()
        val file = sceneFile(dir)
        val pane = open()
        val replaced = SceneJson().parse(String(file.contentsToByteArray())).also { (it as ObjectNode).put("name", "Replaced") }
        WriteAction.runAndWait<Exception> { file.setBinaryContent(SceneJson().pretty(replaced).toByteArray()) }
        settle(pane, 1)
        await("replaced label") { hasSceneLabeled(pane, "Replaced") }
    }

    fun testAssetFoldersAndMetadataChangesReachTheTree() {
        val dir = fixture()
        val pane = open()
        fun assetNames() = entries(pane).mapNotNull { (it.value as? Asset<*>)?.name }
        assertFalse("rock" in assetNames())
        val meta = """{"format":"abyssus","formatVersion":1,"type":"MODEL","uuid":"5e6c2c36-0a32-4a7f-9b5d-0d2f6a0b7a01","additional":{}}"""
        val rock = WriteAction.computeAndWait<VirtualFile, Exception> {
            dir.findChild("assets")?.createChildDirectory(this, "rock") ?: dir.createChildDirectory(this, "assets").createChildDirectory(this, "rock")
        }
        WriteAction.runAndWait<Exception> { rock.createChildData(this, "meta.json").setBinaryContent(meta.toByteArray()) }
        settle(pane, 2.coerceAtMost(pane.refresh.rebuilds.coerceAtLeast(1)))
        expand(pane)
        await("rock listed") { "rock" in assetNames() }
        await("rock unused") { entries(pane).any { (it.value as? Asset<*>)?.name == "rock" } }
        assertEquals("not referenced by any scene", true, entries(pane).firstNotNullOf { (it.value as? Asset<*>)?.takeIf { a -> a.name == "rock" } }.unused)
        val afterAdd = pane.refresh.rebuilds
        WriteAction.runAndWait<Exception> { rock.findChild("meta.json")!!.setBinaryContent(meta.replace("MODEL", "TEXTURE").toByteArray()) }
        settle(pane, afterAdd + 1)
        val afterMeta = pane.refresh.rebuilds
        WriteAction.runAndWait<Exception> { rock.rename(this, "stone") }
        settle(pane, afterMeta + 1)
        expand(pane)
        await("stone listed") { "stone" in assetNames() && "rock" !in assetNames() }
        val afterRename = pane.refresh.rebuilds
        WriteAction.runAndWait<Exception> { rock.delete(this) }
        settle(pane, afterRename + 1)
        await("stone gone") { "stone" !in assetNames() }
    }

    fun testMalformedAndUnsupportedReplacementKeepsTheUnreadablePresentationAndIsNotRewritten() {
        val dir = fixture()
        val file = sceneFile(dir)
        val pane = open()
        for ((index, bad) in listOf("{ not json", """{"format":"abyssus","formatVersion":2}""").withIndex()) {
            WriteAction.runAndWait<Exception> { file.setBinaryContent(bad.toByteArray()) }
            settle(pane, index + 1)
            await("unreadable presentation") { "Main Scene.scene" in texts(pane).map { it.substringBefore(" (") } && !hasSceneLabeled(pane, originalName) }
            assertEquals("the replacement is not repaired", bad, String(file.contentsToByteArray()))
        }
        assertTrue("the tree is still usable", texts(pane).any { it.startsWith("Assets") || it.startsWith("Scenes") })
    }

    fun testUnrelatedFileEventsDoNotRebuildTheTree() {
        val dir = fixture()
        val pane = open()
        val before = pane.refresh.rebuilds
        val notes = WriteAction.computeAndWait<VirtualFile, Exception> { dir.createChildData(this, "notes.txt") }
        WriteAction.runAndWait<Exception> { notes.setBinaryContent("hello".toByteArray()) }
        WriteAction.runAndWait<Exception> { dir.createChildDirectory(this, "src") }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertTrue(pane.refresh.idle)
        assertEquals(before, pane.refresh.rebuilds)
        assertFalse(AbyssusTreeRefresh.isTreePath("/p/src/Main.kt", false))
        assertFalse(AbyssusTreeRefresh.isTreePath("/p/src", true))
        assertTrue(AbyssusTreeRefresh.isTreePath("/p/scenes/A.scene", false))
        assertTrue(AbyssusTreeRefresh.isTreePath("/p/assets/rock/meta.json", false))
        assertTrue(AbyssusTreeRefresh.isTreePath("/p/assets/rock", true))
    }

    // 5.5 navigation

    fun testSelectedExpandedEntitySurvivesAnUnrelatedEdit() {
        val dir = fixture()
        val file = sceneFile(dir)
        val pane = open()
        val entity = entityPath(pane, "0")!!
        pane.tree.selectionPath = entity
        pane.tree.expandPath(entity)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertTrue(editSceneJson(project, file, "Rename entity 1") { root ->
            ((root.get("ecs").get("1").get("components").get("NameComponent")) as ObjectNode).put("name", "Renamed Terrain")
            true
        })
        settle(pane, 1)
        PlatformTestUtil.waitWithEventsDispatching("selection restored", { pane.tree.selectionPath?.let { keyOfEntity(it) } == "0" }, 30)
        await("entity 1 shows its new name") { texts(pane).any { it.startsWith("Renamed Terrain") } }
        val restored = entityPath(pane, "0")!!
        assertEquals(restored.lastPathComponent.toString(), pane.tree.selectionPath!!.lastPathComponent.toString())
        assertTrue("entity 0 is expanded again", pane.tree.isExpanded(restored))
    }

    private fun keyOfEntity(path: TreePath): String? =
        (TreeUtil.getUserObject(path.lastPathComponent) as? DtoEntryNode)?.value?.takeIf { isEntityEntry(it) }?.name

    fun testRemovedSelectedEntityDisappearsAndLeavesNoStaleSelection() {
        val dir = fixture()
        val file = sceneFile(dir)
        val pane = open()
        pane.tree.selectionPath = entityPath(pane, "0")!!
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertTrue(editSceneJson(project, file, "Remove entity 0") { root ->
            (root.get("ecs") as ObjectNode).remove("0")
            true
        })
        settle(pane, 1)
        PlatformTestUtil.waitWithEventsDispatching("selection cleared", { pane.tree.selectionPath?.let { keyOfEntity(it) } != "0" }, 30)
        assertNull("entity 0 is gone", entityPath(pane, "0"))
        assertEquals("no duplicate nodes", entries(pane).map { it.path }.size, entries(pane).map { it.path }.toSet().size)
        assertNull("the removed entity is no longer the selection", pane.tree.selectionPath?.let { keyOfEntity(it) })
    }

    fun testClosingWithAPendingRefreshDoesNotTouchTheTree() {
        fixture()
        val pane = open()
        val before = pane.refresh.rebuilds
        pane.refresh.changed("pending")
        Disposer.dispose(pane)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertEquals("the pending rebuild was cancelled", before, pane.refresh.rebuilds)
        pane.refresh.changed("after disposal")
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertEquals(before, pane.refresh.rebuilds)
    }
}
