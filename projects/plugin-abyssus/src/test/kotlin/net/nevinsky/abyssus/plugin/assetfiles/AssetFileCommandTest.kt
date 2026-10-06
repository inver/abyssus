/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.assetfiles

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.command.undo.UnexpectedUndoException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class AssetFileCommandTest : BasePlatformTestCase() {
    private lateinit var disk: File
    private lateinit var root: VirtualFile
    private lateinit var store: LocalAssetFileStore

    private val oldHeights = byteArrayOf(1, 2, 3, 4)
    private val newHeights = byteArrayOf(9, 8, 7, 6)
    private val metaText = """{"format":"abyssus","formatVersion":1,"type":"TERRAIN","uuid":"t","additional":{"terrainFile":"terrain.data","size":100}}"""

    override fun setUp() {
        super.setUp()
        disk = FileUtil.createTempDirectory("abyssus-assetfiles", null)
        File(disk, "assets/t").mkdirs()
        File(disk, "assets/t/terrain.data").writeBytes(oldHeights)
        File(disk, "assets/t/meta.json").writeText(metaText)
        root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(disk)!!
        root.refresh(false, true)
        store = LocalAssetFileStore(disk)
    }

    private fun bytes(b: ByteArray) = FileSnapshot.Bytes(b)
    private fun bytesOf(path: String): ByteArray? = File(disk, path).takeIf { it.exists() }?.readBytes()
    private fun file(path: String) = root.findFileByRelativePath(path)!!

    private fun regenerate() = AssetTransaction(
        "Regenerate Terrain",
        changes = listOf(
            FileChange("assets/t/terrain.data", bytes(oldHeights), bytes(newHeights)),
            FileChange("assets/t/abyssus-terrain.recipe.json", FileSnapshot.Absent, bytes("{}\n".toByteArray())),
        ),
        expectedFiles = mapOf("assets/t/meta.json" to bytes(metaText.toByteArray())),
    )

    private fun metaEditor(): TextEditor {
        val meta = file("assets/t/meta.json")
        return TextEditorProvider.getInstance().createEditor(project, meta) as TextEditor
    }

    private fun run(txn: AssetTransaction, cancelled: () -> Boolean = { false }, store: AssetFileStore = this.store): Pair<AssetCommandResult, Int> {
        var done = 0
        val result = AssetFileCommand(project, store).execute(
            txn, affected = { listOf(file("assets/t/meta.json"), file("assets/t/terrain.data")) }, isCancelled = cancelled, onDone = { done++ },
        )
        return result to done
    }

    fun testExecuteWritesTheFilesAndReportsOnce() {
        val (result, done) = run(regenerate())
        assertEquals(AssetCommandResult.Done, result)
        assertEquals(1, done)
        assertEquals(newHeights.toList(), bytesOf("assets/t/terrain.data")!!.toList())
        assertEquals("{}\n", String(bytesOf("assets/t/abyssus-terrain.recipe.json")!!))
        assertEquals("the expected meta is untouched", metaText, String(bytesOf("assets/t/meta.json")!!))
    }

    fun testUndoAndRedoFromTheMetaEditorRestoreExactBytesAndTheMissingRecipe() {
        val editor = metaEditor()
        try {
            val undo = UndoManager.getInstance(project)
            run(regenerate())
            assertTrue(undo.isUndoAvailable(editor))
            undo.undo(editor)
            assertEquals(oldHeights.toList(), bytesOf("assets/t/terrain.data")!!.toList())
            assertNull("the recipe the command introduced is removed", bytesOf("assets/t/abyssus-terrain.recipe.json"))
            assertTrue(undo.isRedoAvailable(editor))
            undo.redo(editor)
            assertEquals(newHeights.toList(), bytesOf("assets/t/terrain.data")!!.toList())
            assertEquals("{}\n", String(bytesOf("assets/t/abyssus-terrain.recipe.json")!!))
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun testAStaleSourceIsAConflictAndNothingIsWritten() {
        File(disk, "assets/t/terrain.data").writeBytes(byteArrayOf(5, 5, 5, 5))
        root.refresh(false, true)
        val (result, done) = run(regenerate())
        assertEquals(AssetCommandResult.Conflict("assets/t/terrain.data"), result)
        assertEquals(0, done)
        assertEquals(listOf<Byte>(5, 5, 5, 5), bytesOf("assets/t/terrain.data")!!.toList())
        assertNull(bytesOf("assets/t/abyssus-terrain.recipe.json"))
    }

    fun testUnsavedMetadataTextThatDiffersFromThePreviewIsAConflict() {
        val document = FileDocumentManager.getInstance().getDocument(file("assets/t/meta.json"))!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(metaText.replace("100", "300")) }
        val (result, done) = run(regenerate())
        assertEquals(AssetCommandResult.Conflict("assets/t/meta.json"), result)
        assertEquals(0, done)
        assertEquals(oldHeights.toList(), bytesOf("assets/t/terrain.data")!!.toList())
    }

    private class FailingStore(private val real: AssetFileStore, private val failOnWrite: Int, private val after: Boolean) : AssetFileStore by real {
        var writes = 0
        override fun write(path: String, bytes: ByteArray) {
            val n = writes++
            if (n == failOnWrite && !after) throw java.io.IOException("injected before $n")
            real.write(path, bytes)
            if (n == failOnWrite && after) throw java.io.IOException("injected after $n")
        }
    }

    fun testAFailureBeforeOrAfterAnyWriteRestoresTheFilesAndReportsNoSuccess() {
        for (after in listOf(false, true)) for (n in 0..1) {
            val (result, done) = run(regenerate(), store = FailingStore(store, n, after))
            assertTrue("$after $n: $result", result is AssetCommandResult.Failed && result.rolledBack)
            assertEquals(0, done)
            assertEquals(oldHeights.toList(), bytesOf("assets/t/terrain.data")!!.toList())
            assertNull(bytesOf("assets/t/abyssus-terrain.recipe.json"))
            root.refresh(false, true)
        }
    }

    fun testCancellationBeforeTheFirstWriteWritesNothingAndLateCancellationDoesNotInterrupt() {
        val (cancelled, doneA) = run(regenerate(), cancelled = { true })
        assertEquals(AssetCommandResult.Cancelled, cancelled)
        assertEquals(0, doneA)
        assertEquals(oldHeights.toList(), bytesOf("assets/t/terrain.data")!!.toList())

        var flag = false
        val late = object : AssetFileStore by store {
            override fun write(path: String, bytes: ByteArray) {
                store.write(path, bytes)
                flag = true
            }
        }
        val (result, done) = run(regenerate(), cancelled = { flag }, store = late)
        assertEquals(AssetCommandResult.Done, result)
        assertEquals(1, done)
        assertEquals(newHeights.toList(), bytesOf("assets/t/terrain.data")!!.toList())
    }

    fun testUndoRefusesToOverwriteAnExternalChange() {
        val engine = AssetTransactionEngine(store)
        val txn = regenerate()
        run(txn)
        File(disk, "assets/t/terrain.data").writeBytes(byteArrayOf(0, 0, 0, 0))
        root.refresh(false, true)
        val action = AssetFileUndoAction(txn, engine, emptyList())
        try {
            WriteCommandAction.runWriteCommandAction(project) { action.undo() }
            fail("expected a refused undo")
        } catch (e: UnexpectedUndoException) {
            assertTrue(e.message, e.message!!.contains("terrain.data"))
        } catch (e: RuntimeException) {
            assertTrue(e.toString(), e.cause is UnexpectedUndoException)
        }
        assertEquals(listOf<Byte>(0, 0, 0, 0), bytesOf("assets/t/terrain.data")!!.toList())
    }

    // creation (5.2): a new asset, undone and redone, and what blocks its undo

    private val hillsMeta = """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"uuid":"hills-uuid","type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":100}}"""
    private val hillsHeights = byteArrayOf(0, 0, 0, 0)

    private fun creation(guard: () -> String? = AssetReferenceGuard(disk).let { g -> { g.blocker("hills", "hills-uuid") } }) = AssetTransaction(
        "New Terrain",
        changes = listOf(
            FileChange("assets/hills/meta.json", FileSnapshot.Absent, bytes(hillsMeta.toByteArray())),
            FileChange("assets/hills/terrain.data", FileSnapshot.Absent, bytes(hillsHeights)),
        ),
        createdDirs = listOf("assets/hills"),
        guard = guard,
    )

    private fun create(txn: AssetTransaction = creation()): Pair<AssetCommandResult, Int> {
        var done = 0
        val result = AssetFileCommand(project, store).execute(txn, onDone = { done++ })
        return result to done
    }

    fun testCreationWritesTheAssetAndGlobalUndoAndRedoRestoreTheSameBytes() {
        val (result, done) = create()
        assertEquals(AssetCommandResult.Done, result)
        assertEquals(1, done)
        assertEquals(hillsMeta, String(bytesOf("assets/hills/meta.json")!!))
        val undo = UndoManager.getInstance(project)
        assertTrue(undo.isUndoAvailable(null))
        undo.undo(null)
        assertFalse(File(disk, "assets/hills").exists())
        assertTrue(undo.isRedoAvailable(null))
        undo.redo(null)
        assertEquals("the same uuid and bytes come back", hillsMeta, String(bytesOf("assets/hills/meta.json")!!))
        assertEquals(hillsHeights.toList(), bytesOf("assets/hills/terrain.data")!!.toList())
    }

    fun testCreationOnAFolderThatExistsIsACollisionAndChangesNothing() {
        File(disk, "assets/hills").mkdirs()
        File(disk, "assets/hills/mine.txt").writeText("keep")
        val (result, done) = create()
        assertEquals(AssetCommandResult.Collision("assets/hills"), result)
        assertEquals(0, done)
        assertEquals("keep", File(disk, "assets/hills/mine.txt").readText())
    }

    fun testCreatingTheMissingAssetsFolderToo() {
        File(disk, "assets").deleteRecursively()
        root.refresh(false, true)
        val txn = AssetTransaction(
            "New Terrain",
            changes = listOf(FileChange("assets/hills/meta.json", FileSnapshot.Absent, bytes(hillsMeta.toByteArray()))),
            createdDirs = listOf("assets", "assets/hills"),
        )
        assertEquals(AssetCommandResult.Done, create(txn).first)
        UndoManager.getInstance(project).undo(null)
        assertFalse("created and empty again, so removed with its parent", File(disk, "assets").exists())
    }

    private fun undoOf(txn: AssetTransaction) = AssetFileUndoAction(txn, AssetTransactionEngine(store), emptyList())

    private fun assertUndoRefused(txn: AssetTransaction, mentions: String) {
        try {
            WriteCommandAction.runWriteCommandAction(project) { undoOf(txn).undo() }
            fail("expected a refused undo")
        } catch (e: UnexpectedUndoException) {
            assertTrue(e.message, e.message!!.contains(mentions))
        }
        assertTrue("nothing was removed", File(disk, "assets/hills/meta.json").exists())
    }

    fun testASavedSceneReferenceBlocksUndoOfTheCreation() {
        val txn = creation()
        create(txn)
        File(disk, "scenes").mkdirs()
        File(disk, "scenes/Main.scene").writeText("""{"format":"abyssus","formatVersion":1,"name":"Main","ecs":{"entities":{"0":{"components":{"RenderComponent":{"renderable":{"asset":{"assetName":"hills","type":"TERRAIN"}}}}}}}}""")
        assertUndoRefused(txn, "Main.scene uses hills")
    }

    fun testAnUnsavedSceneReferenceBlocksUndoOfTheCreation() {
        val txn = creation()
        create(txn)
        File(disk, "scenes").mkdirs()
        File(disk, "scenes/Main.scene").writeText("""{"format":"abyssus","formatVersion":1,"name":"Main","ecs":{"entities":{}}}""")
        root.refresh(false, true)
        val document = FileDocumentManager.getInstance().getDocument(root.findFileByRelativePath("scenes/Main.scene")!!)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText("""{"format":"abyssus","formatVersion":1,"name":"Main","ecs":{"entities":{"0":{"components":{"RenderComponent":{"renderable":{"asset":{"assetName":"hills"}}}}}}}}""")
        }
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
        assertUndoRefused(txn, "Main.scene uses hills")
    }

    fun testASkyboxNameOrAnAssetReferenceByUuidBlocksUndoOfTheCreation() {
        val txn = creation()
        create(txn)
        File(disk, "scenes").mkdirs()
        File(disk, "scenes/Main.scene").writeText("""{"format":"abyssus","formatVersion":1,"name":"Main","skyboxName":"hills","ecs":{"entities":{}}}""")
        assertUndoRefused(txn, "Main.scene")
        File(disk, "scenes/Main.scene").delete()
        File(disk, "assets/other").mkdirs()
        File(disk, "assets/other/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{"materials":["hills-uuid"]}}""")
        assertUndoRefused(txn, "other uses hills")
    }

    fun testAnUnrelatedSceneDoesNotBlockUndo() {
        val txn = creation()
        create(txn)
        File(disk, "scenes").mkdirs()
        File(disk, "scenes/Main.scene").writeText("""{"format":"abyssus","formatVersion":1,"name":"Main","ecs":{"entities":{"0":{"components":{"RenderComponent":{"renderable":{"asset":{"assetName":"t"}}}}}}}}""")
        WriteCommandAction.runWriteCommandAction(project) { undoOf(txn).undo() }
        assertFalse(File(disk, "assets/hills").exists())
    }

    fun testAFileAddedToTheNewFolderAfterwardsBlocksUndoAndIsKept() {
        val txn = creation()
        create(txn)
        File(disk, "assets/hills/notes.txt").writeText("mine")
        assertUndoRefused(txn, "hills")
        assertEquals("mine", File(disk, "assets/hills/notes.txt").readText())
    }
}
