/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.core.io.JsonProcessor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import javax.swing.JPanel

/** The scene editor wires asset changes (files and unsaved metadata text) to its view, on a project on the real file system. */
class SceneAssetRefreshEditorTest : BasePlatformTestCase() {
    private class FakeView : SceneView {
        val revisions = mutableListOf<AssetRevisionBatch>()
        override val view = JPanel()
        override var onFailure: ((Throwable) -> Unit)? = null
        override var onPick: ((String) -> Unit)? = null
        override var onTransform: ((String, TransformEdit) -> Boolean)? = null
        override fun setParams(params: SceneRenderParams) {}
        override fun refreshAssets(revision: AssetRevisionBatch) { revisions += revision }
        override fun dispose() {}
    }

    private lateinit var root: File
    private lateinit var scene: VirtualFile
    private lateinit var meta: VirtualFile

    override fun setUp() {
        super.setUp()
        root = FileUtil.createTempDirectory("abyssus-refresh", null)
        File(root, "Refresh.abss").writeText("""{"format":"abyssus","formatVersion":1}""")
        File(root, "scenes").mkdirs()
        File(root, "scenes/Main.scene").writeText("""{"format":"abyssus","formatVersion":1,"id":0,"name":"Main","ecs":{"entities":{}}}""")
        File(root, "assets/hills").mkdirs()
        File(root, "assets/hills/terrain.data").writeBytes(ByteArray(16))
        File(root, "assets/hills/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"uuid":"t","type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":100,"uv":1.0}}""",
        )
        val lfs = LocalFileSystem.getInstance()
        scene = lfs.refreshAndFindFileByIoFile(File(root, "scenes/Main.scene"))!!
        meta = lfs.refreshAndFindFileByIoFile(File(root, "assets/hills/meta.json"))!!
    }

    private fun editor(view: FakeView): SceneFileEditor =
        newSceneEditor(project, scene, SceneParamsSource { SceneRenderParams.DEFAULT }, viewFactory = { view }).also { Disposer.register(testRootDisposable, it) }

    /** Lets the background read and its UI continuation run. */
    private fun settle() {
        repeat(40) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            Thread.sleep(25)
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    fun testUnsavedMetadataTextReloadsTheViewAndSavingDoesNotRepeatIt() {
        val view = FakeView()
        editor(view)
        settle()
        assertTrue(view.revisions.isEmpty())
        val document = FileDocumentManager.getInstance().getDocument(meta)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace("\"size\":100", "\"size\":250")) }
        settle()
        assertEquals(setOf("hills"), view.revisions.single().names)
        val text = view.revisions.single().unsaved[File(meta.path).absoluteFile]!!
        assertEquals(250, net.nevinsky.abyssus.core.io.JsonProcessor().readObject(text).get("additional").get("size").asInt())
        FileDocumentManager.getInstance().saveDocument(document)
        settle()
        assertEquals("saving the shown text is not a new revision", 1, view.revisions.size)
    }

    fun testAnExternalHeightsChangeReloadsTheView() {
        val view = FakeView()
        editor(view)
        settle()
        val data = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(root, "assets/hills/terrain.data"))!!
        WriteCommandAction.runWriteCommandAction(project) { data.setBinaryContent(ByteArray(64)) }
        settle()
        assertEquals(setOf("hills"), view.revisions.last().names)
    }

    fun testADisposedEditorStopsReloading() {
        val view = FakeView()
        val e = editor(view)
        settle()
        Disposer.dispose(e)
        val document = FileDocumentManager.getInstance().getDocument(meta)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace("\"size\":100", "\"size\":999")) }
        settle()
        assertTrue(view.revisions.isEmpty())
    }
}
