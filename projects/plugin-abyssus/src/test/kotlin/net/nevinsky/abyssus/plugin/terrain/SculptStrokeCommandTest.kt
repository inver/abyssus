/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.terrain

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.pick.SculptMode
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.RaiseLowerTool
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SmoothTool
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileStore
import net.nevinsky.abyssus.plugin.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.plugin.testCore
import java.io.File
import java.util.UUID

class SculptStrokeCommandTest : BasePlatformTestCase() {
    private val resolution = 17
    private val size = 160
    private lateinit var disk: File
    private lateinit var folder: VirtualFile
    private val drafts = SculptDrafts()
    private val reports = mutableListOf<String>()
    private lateinit var original: ByteArray

    override fun setUp() {
        super.setUp()
        disk = FileUtil.createTempDirectory("abyssus-sculpt", null)
        val dir = File(disk, "assets/terrain_flat").apply { mkdirs() }
        val files = testCore.terrainWriter.create(UUID.randomUUID(), 0L, size, FloatArray(resolution * resolution))
        File(dir, "meta.json").writeText(files.metaText)
        File(dir, "terrain.data").writeBytes(files.heightBytes)
        original = files.heightBytes
        folder = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir)!!
        folder.refresh(false, true)
    }

    private fun bytes(name: String) = File(folder.path, name).takeIf { it.exists() }?.readBytes()
    private fun sculptFiles() = listOf("abyssus-terrain.base", "abyssus-terrain.sculpt", "abyssus-terrain.sculpt.json").filter { File(folder.path, it).exists() }

    private val target get() = TerrainTarget("1", TerrainData(resolution, FloatArray(resolution * resolution), size, 1f), Matrix4())

    private fun command(
        store: AssetFileStore = LocalAssetFileStore(disk),
        tool: net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptTool = RaiseLowerTool(),
    ) = SculptStrokeCommand(
        project, folder, drafts, testCore.terrainSculpt, store,
        { readTerrainFolder(folder, testCore.json, testCore.terrainRecipes, testCore.terrainSculpt) },
        { SculptMode("1", 30f, 1f) }, { tool }, { reports += it },
    )

    private fun stroke(c: SculptStrokeCommand) {
        assertTrue(c.pressed(target, Vector3(80f, 0f, 80f)))
        c.dragged(Vector3(100f, 0f, 80f), false)
        c.released()
    }

    fun testAStrokeWritesTheHeightsTheDeltaTheManifestAndTheStoredBase() {
        stroke(command())
        assertFalse(bytes("terrain.data")!!.contentEquals(original))
        assertEquals(3, sculptFiles().size)
        assertEquals("the base holds the heights from before the stroke", original.toList(), bytes("abyssus-terrain.base")!!.toList())
        assertNull("the draft is dropped so every view loads the files", drafts.of(folder.name))
        assertTrue(reports.isEmpty())
    }

    fun testNothingIsWrittenBeforeTheRelease() {
        val c = command()
        assertTrue(c.pressed(target, Vector3(80f, 0f, 80f)))
        c.dragged(Vector3(100f, 0f, 80f), false)
        assertNotNull(drafts.of(folder.name))
        assertTrue(drafts.of(folder.name)!!.changed.let { !it.isEmpty })
        assertEquals(original.toList(), bytes("terrain.data")!!.toList())
        assertTrue(sculptFiles().isEmpty())
        c.released()
    }

    fun testAnEmptyStrokeWritesNothingAndAddsNoUndoStep() {
        val c = command(tool = SmoothTool())
        stroke(c)
        assertEquals(original.toList(), bytes("terrain.data")!!.toList())
        assertTrue(sculptFiles().isEmpty())
        val editor = TextEditorProvider.getInstance().createEditor(project, folder.findChild("meta.json")!!)
        try {
            assertFalse(UndoManager.getInstance(project).isUndoAvailable(editor))
        } finally {
            Disposer.dispose(editor)
        }
    }

    fun testUndoAndRedoRestoreTheExactBytesAndRemoveTheIntroducedFiles() {
        stroke(command())
        val sculpted = bytes("terrain.data")!!
        val delta = bytes("abyssus-terrain.sculpt")!!
        val editor = TextEditorProvider.getInstance().createEditor(project, folder.findChild("meta.json")!!)
        try {
            val undo = UndoManager.getInstance(project)
            assertTrue(undo.isUndoAvailable(editor))
            undo.undo(editor)
            assertEquals(original.toList(), bytes("terrain.data")!!.toList())
            assertTrue(sculptFiles().isEmpty())
            undo.redo(editor)
            assertEquals(sculpted.toList(), bytes("terrain.data")!!.toList())
            assertEquals(delta.toList(), bytes("abyssus-terrain.sculpt")!!.toList())
            assertEquals(3, sculptFiles().size)
        } finally {
            Disposer.dispose(editor)
        }
    }

    fun testAFailedWriteRestoresEveryFileAndReportsTheReason() {
        val real = LocalAssetFileStore(disk)
        var writes = 0
        val failing = object : AssetFileStore by real {
            override fun write(path: String, bytes: ByteArray) {
                if (++writes == 2) throw java.io.IOException("disk full")
                real.write(path, bytes)
            }
        }
        stroke(command(failing))
        assertEquals(original.toList(), bytes("terrain.data")!!.toList())
        assertTrue(sculptFiles().isEmpty())
        assertTrue(reports.single().contains("disk full"))
        assertNull(drafts.of(folder.name))
    }

    fun testAnExternalEditDuringTheStrokeIsNotOverwritten() {
        val c = command()
        assertTrue(c.pressed(target, Vector3(80f, 0f, 80f)))
        c.dragged(Vector3(100f, 0f, 80f), false)
        val external = ByteArray(resolution * resolution * 4) { 1 }
        File(folder.path, "terrain.data").writeBytes(external)
        c.released()
        assertEquals(external.toList(), bytes("terrain.data")!!.toList())
        assertTrue(sculptFiles().isEmpty())
        assertEquals(1, reports.size)
        assertNull(drafts.of(folder.name))
    }

    fun testEscapeDiscardsTheStrokeAndWritesNothing() {
        val c = command()
        assertTrue(c.pressed(target, Vector3(80f, 0f, 80f)))
        c.dragged(Vector3(100f, 0f, 80f), false)
        c.cancelled()
        c.released()
        assertEquals(original.toList(), bytes("terrain.data")!!.toList())
        assertTrue(sculptFiles().isEmpty())
        assertNull(drafts.of(folder.name))
    }

    fun testASecondStrokeKeepsTheStoredBaseAndAddsToTheDelta() {
        stroke(command())
        folder.refresh(false, true)
        val base = bytes("abyssus-terrain.base")!!
        val firstDelta = bytes("abyssus-terrain.sculpt")!!
        stroke(command())
        assertEquals(base.toList(), bytes("abyssus-terrain.base")!!.toList())
        assertFalse(firstDelta.contentEquals(bytes("abyssus-terrain.sculpt")!!))
    }

    fun testAnUnsafeTerrainRefusesTheStroke() {
        stroke(command())
        File(folder.path, "terrain.data").writeBytes(ByteArray(resolution * resolution * 4) { 3 })
        folder.refresh(false, true)
        val c = command()
        assertFalse(c.pressed(target, Vector3(80f, 0f, 80f)))
        assertEquals(1, reports.size)
    }
}
