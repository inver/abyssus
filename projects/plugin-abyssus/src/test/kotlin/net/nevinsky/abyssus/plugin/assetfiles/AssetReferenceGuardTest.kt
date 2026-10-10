/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.assetfiles

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UnexpectedUndoException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/** What blocks Undo of an asset's creation: the scenes and metadata that name it. */
class AssetReferenceGuardTest : BasePlatformTestCase() {
    private lateinit var disk: File
    private lateinit var root: VirtualFile
    private lateinit var store: LocalAssetFileStore

    private val meadowMeta =
        """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"uuid":"meadow-uuid","type":"FOLIAGE","additional":{"terrain":"t"}}"""

    override fun setUp() {
        super.setUp()
        disk = FileUtil.createTempDirectory("abyssus-refguard", null)
        File(disk, "assets").mkdirs()
        root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(disk)!!
        root.refresh(false, true)
        store = LocalAssetFileStore(disk)
    }

    override fun tearDown() {
        try {
            disk.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    /** Creates the foliage asset folder, as New Foliage would, with the given scene already on disk. */
    private fun createFoliage(scene: String? = null): AssetTransaction {
        val txn = AssetTransaction(
            "New Foliage",
            changes = listOf(FileChange("assets/meadow/meta.json", FileSnapshot.Absent, FileSnapshot.Bytes(meadowMeta.toByteArray()))),
            createdDirs = listOf("assets/meadow"),
            guard = { AssetReferenceGuard(disk).blocker("meadow", "meadow-uuid") },
        )
        AssetFileCommand(project, store).execute(txn)
        if (scene != null) writeScene(scene)
        return txn
    }

    private fun writeScene(text: String) {
        File(disk, "scenes").mkdirs()
        File(disk, "scenes/Main.scene").writeText(text)
        root.refresh(false, true)
    }

    private fun undoOf(txn: AssetTransaction) = AssetFileUndoAction(txn, AssetTransactionEngine(store), emptyList())

    private fun assertUndoRefused(txn: AssetTransaction) {
        try {
            WriteCommandAction.runWriteCommandAction(project) { undoOf(txn).undo() }
            fail("expected a refused undo")
        } catch (e: UnexpectedUndoException) {
            assertTrue(e.message, e.message!!.contains("Main.scene uses meadow"))
        }
        assertTrue("the folder stays", File(disk, "assets/meadow/meta.json").exists())
    }

    private val withFoliage =
        """{"format":"abyssus","formatVersion":1,"name":"Main","ecs":{"entities":{"1":{"components":{"FoliageComponent":{"assetName":"meadow"}}}}}}"""

    fun testAFoliageComponentNamingTheAssetBlocksUndo() {
        assertUndoRefused(createFoliage(withFoliage))
    }

    fun testAnUnsavedFoliageComponentNamingTheAssetBlocksUndo() {
        val txn = createFoliage("""{"format":"abyssus","formatVersion":1,"name":"Main","ecs":{"entities":{}}}""")
        val document = FileDocumentManager.getInstance().getDocument(file("scenes/Main.scene"))!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(withFoliage) }
        assertUndoRefused(txn)
    }

    fun testAFoliageComponentNamingAnotherAssetDoesNotBlock() {
        val txn = createFoliage(
            """{"format":"abyssus","formatVersion":1,"name":"Main","ecs":{"entities":{"1":{"components":{"FoliageComponent":{"assetName":"other"}}}}}}""",
        )
        WriteCommandAction.runWriteCommandAction(project) { undoOf(txn).undo() }
        assertFalse(File(disk, "assets/meadow").exists())
    }

    private fun file(path: String) = root.findFileByRelativePath(path)!!
}
