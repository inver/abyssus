/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.foliage

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.pick.FoliagePaintMode
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.slf4j.helpers.NOPLogger
import java.io.File

class FoliageStrokeCommandTest : BasePlatformTestCase() {
    private var previousDialog: com.intellij.openapi.ui.TestDialog? = null
    private val dialogs = mutableListOf<String>()
    private lateinit var disk: File
    private lateinit var folder: VirtualFile
    private lateinit var scene: VirtualFile
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val drafts get() = project.service<FoliageDrafts>()
    private val reports = mutableListOf<String>()
    private var mode = FoliagePaintMode("1", 1, 100f, 1f, true)
    override fun setUp() {
        super.setUp()
        drafts.discardAll()
        previousDialog = com.intellij.openapi.ui.TestDialogManager.setTestDialog { message ->
            dialogs += message
            com.intellij.openapi.ui.Messages.YES
        }
        disk = FileUtil.createTempDirectory("abyssus-foliage-stroke", null)
        FileUtil.copyDir(File("src/test/testData/project/Foliage"), disk)
        val root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(disk)!!
        root.refresh(false, true)
        folder = root.findFileByRelativePath("assets/foliage_meadow")!!
        scene = root.findFileByRelativePath("scenes/Main Scene.scene")!!
    }
    override fun tearDown() {
        try {
            drafts.discardAll()
            previousDialog?.let { com.intellij.openapi.ui.TestDialogManager.setTestDialog(it) }
        }
        finally { super.tearDown() }
    }
    private fun source(): FoliageSource.Ready {
        val text = File(folder.path, "meta.json").readText()
        return readFoliageSource(folder.name, disk, json, text, json.readObject(text)["additional"]) as FoliageSource.Ready
    }
    private fun command(background: (Runnable) -> Unit = { it.run() }, ui: (Runnable) -> Unit = { it.run() }) = FoliageStrokeCommand(
        project, scene, folder, source(), drafts, { mode }, background, ui, reports::add,
    )
    private fun target() = TerrainTarget("1", source().terrain, Matrix4())
    private fun bytes(name: String) = File(folder.path, name).readBytes().toList()
    private fun press(c: FoliageStrokeCommand) = c.pressed(target(), Vector3(800f, 0f, 800f))

    fun testStrokeLiveDraftReleaseUndoAndRedoFromSceneContext() {
        val beforeMask = bytes("layer-1.mask")
        val beforeBake = bytes("foliage.data")
        val beforeMeta = bytes("meta.json")
        val editor = TextEditorProvider.getInstance().createEditor(project, scene)
        try {
            val c = command()
            assertTrue(press(c))
            assertTrue(drafts.of(folder.name)!!.revision > 0)
            assertEquals(beforeMask, bytes("layer-1.mask"))
            c.released()
            val afterMask = bytes("layer-1.mask")
            val afterBake = bytes("foliage.data")
            assertFalse(beforeMask == afterMask)
            assertEquals(beforeMeta, bytes("meta.json"))
            assertNull(drafts.of(folder.name))
            UndoManager.getInstance(project).undo(editor)
            assertEquals(beforeMask, bytes("layer-1.mask"))
            assertEquals(beforeBake, bytes("foliage.data"))
            UndoManager.getInstance(project).redo(editor)
            assertEquals(afterMask, bytes("layer-1.mask"))
            assertEquals(afterBake, bytes("foliage.data"))
            assertTrue(reports.isEmpty())
        } finally { Disposer.dispose(editor) }
    }
    fun testEscapeAndNoopWriteNothing() {
        val beforeMask = bytes("layer-1.mask")
        val beforeBake = bytes("foliage.data")
        val c = command()
        assertTrue(press(c))
        c.cancelled()
        assertEquals(beforeMask, bytes("layer-1.mask"))
        assertEquals(beforeBake, bytes("foliage.data"))
        mode = mode.copy(strength = 0f)
        assertTrue(press(c))
        c.released()
        assertEquals(beforeMask, bytes("layer-1.mask"))
        assertEquals(beforeBake, bytes("foliage.data"))
    }
    fun testPendingSettingsAreKeptAndPaintingIsRefused() {
        val s = source()
        val draft = drafts.open(folder.name, s.terrain, s.settings.maskResolution)
        val pending = s.settings.copy(layers = s.settings.layers.map { it.copy(density = it.density * 2) })
        draft.editSettings(pending)
        assertFalse(press(command()))
        assertEquals(pending, drafts.of(folder.name)!!.settings)
        assertFalse(reports.isEmpty())
    }
    fun testNewerPanelDraftIsNotClearedByQueuedRelease() {
        val tasks = mutableListOf<Runnable>()
        val before = bytes("layer-1.mask")
        val c = command(tasks::add)
        assertTrue(press(c))
        c.released()
        val pending = source().settings.copy(layers = source().settings.layers.map { it.copy(density = 0.002f) })
        drafts.of(folder.name)!!.editSettings(pending)
        tasks.forEach(Runnable::run)
        assertEquals(before, bytes("layer-1.mask"))
        assertEquals(pending, drafts.of(folder.name)!!.settings)
    }
    fun testQueuedReleaseIsCancelledBeforeItWrites() {
        val tasks = mutableListOf<Runnable>()
        val before = bytes("layer-1.mask")
        val c = command(tasks::add)
        assertTrue(press(c))
        c.released()
        c.cancelled()
        tasks.forEach(Runnable::run)
        assertEquals(before, bytes("layer-1.mask"))
    }
    fun testUndoRefusesAnExternallyReplacedMask() {
        val editor = TextEditorProvider.getInstance().createEditor(project, scene)
        try {
            val c = command()
            assertTrue(press(c))
            c.released()
            val changed = ByteArray(64 * 64) { 19 }
            File(folder.path, "layer-1.mask").writeBytes(changed)
            val bake = bytes("foliage.data")
            val refusal = com.intellij.testFramework.LoggedErrorProcessor.executeAndReturnLoggedError {
                UndoManager.getInstance(project).undo(editor)
            }
            assertTrue(refusal.message!!.contains("layer-1.mask"))
            assertEquals(changed.toList(), bytes("layer-1.mask"))
            assertEquals(bake, bytes("foliage.data"))
        } finally { Disposer.dispose(editor) }
    }
    fun testMaskChangedDuringGenerationIsNotOverwritten() {
        val tasks = mutableListOf<Runnable>()
        val c = command(tasks::add)
        assertTrue(press(c))
        c.released()
        val changed = ByteArray(64 * 64) { 17 }
        File(folder.path, "layer-1.mask").writeBytes(changed)
        tasks.forEach(Runnable::run)
        assertEquals(changed.toList(), bytes("layer-1.mask"))
        assertFalse(reports.isEmpty())
    }
    fun testOverLimitStrokeIsDiscardedAndWritesNothing() {
        val meta = File(folder.path, "meta.json")
        meta.writeText(meta.readText().replace("0.001", "100.0"))
        val before = bytes("layer-1.mask")
        val beforeBake = bytes("foliage.data")
        val c = command()
        assertTrue(press(c))
        c.released()
        assertEquals(before, bytes("layer-1.mask"))
        assertEquals(beforeBake, bytes("foliage.data"))
        assertNull(drafts.of(folder.name))
        assertFalse(reports.isEmpty())
    }
    fun testDisposalReleasesStagedBackupWhenUiNeverRuns() {
        fun backups(): Set<java.nio.file.Path> = java.nio.file.Files.list(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"))).use { paths ->
            paths.filter { it.fileName.toString().startsWith("abyssus-foliage-undo-") }.toList().toSet()
        }
        val before = backups()
        File(folder.path, "foliage.data").writeBytes(byteArrayOf(9, 8, 7))
        val pendingUi = mutableListOf<Runnable>()
        val c = command(ui = pendingUi::add)
        assertTrue(press(c))
        c.released()
        val created = backups() - before
        assertEquals(1, created.size)
        c.dispose()
        assertTrue(created.none { java.nio.file.Files.exists(it) })
    }
    fun testStaleBakeUndoPreservesItsExactBytes() {
        File(folder.path, "foliage.data").writeBytes(byteArrayOf(9, 8, 7))
        val editor = TextEditorProvider.getInstance().createEditor(project, scene)
        try {
            val c = command()
            assertTrue(press(c))
            c.released()
            UndoManager.getInstance(project).undo(editor)
            assertEquals(listOf<Byte>(9, 8, 7), bytes("foliage.data"))
        } finally { Disposer.dispose(editor) }
    }
}
