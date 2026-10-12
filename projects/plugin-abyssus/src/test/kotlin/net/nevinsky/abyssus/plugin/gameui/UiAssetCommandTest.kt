/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.ui.UiEditError
import net.nevinsky.abyssus.lib.core.editor.ui.UiEditResult
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import net.nevinsky.abyssus.plugin.AbyssusCore
import net.nevinsky.abyssus.plugin.assetfiles.*
import net.nevinsky.abyssus.plugin.dto.ProjectReader
import java.io.File
import java.util.UUID

class UiAssetCommandTest : BasePlatformTestCase() {
    private lateinit var dir: File
    private lateinit var abss: VirtualFile
    private val projectText = """{"format":"abyssus","formatVersion":1,"name":"P"}"""
    private val sceneText = """{"format":"abyssus","formatVersion":1,"name":"Main","ecs":{"entities":{}}}"""
    private val json = SceneJson()

    override fun setUp() {
        super.setUp()
        dir = FileUtil.createTempDirectory("abyssus-uiasset", null)
        File(dir, "P.abss").writeText(projectText)
        File(dir, "scenes").mkdirs()
        File(dir, "scenes/Main.scene").writeText(sceneText)
        File(dir, "assets/tree").mkdirs()
        File(dir, "assets/tree/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"uuid":"${UUID.nameUUIDFromBytes("tree".toByteArray())}","type":"MODEL","additional":{}}""")
        abss = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(dir, "P.abss"))!!
        abss.parent.refresh(false, true)
    }

    private fun factory() = NewUiAssetFactory(service<AbyssusCore>().documents.json)

    private fun create(name: String = "menus", store: AssetFileStore = LocalAssetFileStore(dir)): AssetCommandResult =
        AssetFileCommand(project, store).execute(factory().stage(abss, name).transaction)

    private fun text(path: String) = File(dir, path).readText()

    private fun undo() = UndoManager.getInstance(project).undo(null)
    private fun redo() = UndoManager.getInstance(project).redo(null)

    /** A store that delegates but fails the first write of [path] (so the rollback itself can write), to see what rolls back. */
    private class FailingStore(private val inner: AssetFileStore, private val path: String) : AssetFileStore by inner {
        private var failed = false

        override fun write(path: String, bytes: ByteArray) {
            if (path == this.path && !failed) { failed = true; throw java.io.IOException("disk full") }
            inner.write(path, bytes)
        }
    }

    fun testCreationWritesAnAdmittedAssetRegistersItAndTouchesNoScene() {
        assertEquals(AssetCommandResult.Done, create())
        abss.parent.refresh(false, true)
        val meta = json.parse(text("assets/menus/meta.json"))
        assertTrue(UiCollectionDecoder().decode(meta) is UiAdmission.Admitted)
        assertEquals(listOf("menus"), json.parse(text("P.abss"))["uiAssetNames"].map { it.asText() })
        assertEquals(sceneText, text("scenes/Main.scene"))
        val listed = project.service<ProjectReader>().read(abss).obj!!.assets.single { it.name == "menus" }
        assertEquals("UI", listed.type.name)
        assertTrue("registered UI assets are roots", !listed.unused)
    }

    fun testUndoRemovesTheAssetAndRestoresTheProjectFileExactlyAndRedoBringsBackTheSameBytes() {
        assertEquals(AssetCommandResult.Done, create())
        val meta = text("assets/menus/meta.json")
        val registered = text("P.abss")
        undo()
        assertFalse(File(dir, "assets/menus").exists())
        assertEquals(projectText, text("P.abss"))
        assertTrue(File(dir, "assets/tree/meta.json").isFile)
        redo()
        assertEquals(meta, text("assets/menus/meta.json"))
        assertEquals(registered, text("P.abss"))
    }

    fun testUndoIsRefusedWhileAnotherUiAssetNavigatesToTheNewOne() {
        assertEquals(AssetCommandResult.Done, create())
        val other = """{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"screens":[{"id":"s","root":{"id":"r","type":"panel","children":[
            {"id":"b","type":"button","actions":{"click":{"type":"openScreen","assetName":"menus","screenId":"main"}}}]}}]}}}"""
        File(dir, "assets/hud").mkdirs()
        File(dir, "assets/hud/meta.json").writeText(other)
        assertEquals("hud navigates to menus.", uiNavigationBlocker(dir, "menus"))
        assertNotNull(uiNavigationBlocker(dir, "menus"))
        assertNull(uiNavigationBlocker(dir, "hud"))
        val staged = factory().stage(abss, "extra")
        assertNull("nothing navigates to extra", staged.transaction.guard())
    }

    fun testNameCollisionsAreRefusedAtStagingAndRacesAreCollisionsNotOverwrites() {
        for (bad in listOf("tree", "", "..", "a/b", " ")) {
            val failure = runCatching { factory().stage(abss, bad) }.exceptionOrNull()
            assertTrue(bad, failure is IllegalArgumentException)
        }
        // the folder appears between staging and the command
        val staged = factory().stage(abss, "menus")
        File(dir, "assets/menus").mkdirs()
        File(dir, "assets/menus/meta.json").writeText("mine")
        val result = AssetFileCommand(project, LocalAssetFileStore(dir)).execute(staged.transaction)
        assertTrue(result is AssetCommandResult.Collision)
        assertEquals("mine", text("assets/menus/meta.json"))
        assertEquals(projectText, text("P.abss"))
    }

    fun testReadOnlyTargetsAreRefusedAndNothingIsWritten() {
        File(dir, "P.abss").setWritable(false)
        abss.refresh(false, false)
        try {
            val failure = runCatching { factory().stage(abss, "menus") }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertFalse(registerUiAsset(project, abss, "menus"))
            assertFalse(File(dir, "assets/menus").exists())
        } finally {
            File(dir, "P.abss").setWritable(true)
            abss.refresh(false, false)
        }
        // an existing UI meta that is read-only is not edited either
        assertEquals(AssetCommandResult.Done, create())
        val meta = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(dir, "assets/menus/meta.json"))!!
        File(dir, "assets/menus/meta.json").setWritable(false)
        meta.refresh(false, false)
        try {
            assertEquals(UiEditOutcome.ReadOnly, editUiMeta(project, meta) { e, m -> e.addWidget(m, "root", "button") })
        } finally {
            File(dir, "assets/menus/meta.json").setWritable(true)
        }
    }

    fun testAFailureMidWayRollsEverythingBack() {
        // the project file is the second change: the asset folder and meta are written first and must go again
        val result = create(store = FailingStore(LocalAssetFileStore(dir), "P.abss"))
        assertTrue(result is AssetCommandResult.Failed)
        assertFalse("the half-written asset is removed", File(dir, "assets/menus").exists())
        assertEquals(projectText, text("P.abss"))
        assertEquals((result as AssetCommandResult.Failed).leftover, emptyList<String>())
    }

    fun testUnsavedProjectTextIsRefusedRatherThanOverwritten() {
        val document = FileDocumentManager.getInstance().getDocument(abss)!!
        ApplicationManager.getApplication().runWriteAction { document.setText(projectText.replace("\"P\"", "\"Edited\"")) }
        val failure = runCatching { factory().stage(abss, "menus") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(projectText, text("P.abss"))
        assertFalse(File(dir, "assets/menus").exists())
    }

    fun testAConcurrentEditOfTheProjectFileIsAConflictNotAnOverwrite() {
        val staged = factory().stage(abss, "menus")
        File(dir, "P.abss").writeText(projectText.replace("\"P\"", "\"Changed\""))
        val result = AssetFileCommand(project, LocalAssetFileStore(dir)).execute(staged.transaction)
        assertTrue(result.toString(), result is AssetCommandResult.Conflict)
        assertTrue(text("P.abss").contains("Changed"))
        assertFalse(File(dir, "assets/menus").exists())
    }

    // ---- existing metadata: always through editSceneJson ----

    private fun uiMeta(): VirtualFile {
        assertEquals(AssetCommandResult.Done, create())
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(dir, "assets/menus/meta.json"))!!
    }

    fun testAnEditIsOneUndoableCommandOnTheMetaDocumentAndKeepsUnrelatedText() {
        val meta = uiMeta()
        val document = FileDocumentManager.getInstance().getDocument(meta)!!
        val before = document.text
        val outcome = editUiMeta(project, meta) { e, m -> e.addWidget(m, "root", "button") }
        assertEquals(UiEditOutcome.Edited(UiEditResult.Added("button")), outcome)
        assertTrue(document.text.contains("\"button\""))
        assertTrue(document.text.contains("\"uuid\""))
        UndoManager.getInstance(project).undo(com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(meta, false).firstOrNull())
        assertEquals("one undo restores the exact previous text", before, document.text)
    }

    fun testEditsReadUnsavedTextAndRefuseStaleOrInvalidDocuments() {
        val meta = uiMeta()
        val document = FileDocumentManager.getInstance().getDocument(meta)!!
        val start = document.text
        ApplicationManager.getApplication().runWriteAction { document.setText(start.replace("Hello", "Unsaved")) }
        val newer = document.text
        assertEquals("a gesture that began on the older text is refused", UiEditOutcome.Stale,
            editUiMeta(project, meta, baseText = start) { e, m -> e.addWidget(m, "root", "button") })
        assertEquals(newer, document.text)
        assertTrue(editUiMeta(project, meta, baseText = newer) { e, m -> e.addWidget(m, "root", "button") } is UiEditOutcome.Edited)
        assertTrue("the unsaved edit survived", document.text.contains("Unsaved"))
        // an invalid document is never edited
        ApplicationManager.getApplication().runWriteAction { document.setText(document.text.replace("\"schemaVersion\": 1", "\"schemaVersion\": 9")) }
        val rejected = editUiMeta(project, meta) { e, m -> e.addWidget(m, "root", "button") } as UiEditOutcome.Rejected
        assertEquals(UiEditError.NOT_EDITABLE, rejected.error)
        assertTrue(rejected.issues.isNotEmpty())
        // and an edit that would leave invalid UI is not written
        ApplicationManager.getApplication().runWriteAction { document.setText(start) }
        val before = document.text
        assertTrue(editUiMeta(project, meta) { e, m -> e.addWidget(m, "title", "button") } is UiEditOutcome.Rejected)
        assertEquals(before, document.text)
    }

    fun testRegisteringAndUnregisteringAreUndoableProjectEdits() {
        assertTrue(registerUiAsset(project, abss, "menus"))
        assertEquals(listOf("menus"), json.parse(text("P.abss"))["uiAssetNames"].map { it.asText() })
        assertFalse("registering twice changes nothing", registerUiAsset(project, abss, "menus"))
        assertTrue(unregisterUiAsset(project, abss, "menus"))
        assertEquals(projectText, text("P.abss"))
    }
}
