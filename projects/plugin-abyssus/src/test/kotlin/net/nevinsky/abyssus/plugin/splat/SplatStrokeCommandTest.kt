/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.splat

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
import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.lib.core.editor.pick.SPLAT_BRUSH
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.ImageIoSplatCodec
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatPixels
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatStateReader
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileStore
import net.nevinsky.abyssus.plugin.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.plugin.testCore
import java.io.File

class SplatStrokeCommandTest : BasePlatformTestCase() {
    private val side = 64
    private val size = 63
    private val uuid = "aaaaaaaa-0000-0000-0000-000000000001"
    private val codec = ImageIoSplatCodec()
    private lateinit var disk: File
    private lateinit var terrainDir: VirtualFile
    private lateinit var png: File
    private lateinit var original: ByteArray
    private val drafts = SplatDrafts()
    private val reports = mutableListOf<String>()
    private val queue = ArrayDeque<Runnable>()

    override fun setUp() {
        super.setUp()
        disk = FileUtil.createTempDirectory("abyssus-splat", null)
        val terrain = File(disk, "assets/terrain_a").apply { mkdirs() }
        File(terrain, "meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"version":1,"uuid":"t","type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":$size,"uv":8.0,"splatMap":"$uuid"}}""",
        )
        val texture = File(disk, "assets/texture_splat").apply { mkdirs() }
        File(texture, "meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"version":1,"uuid":"$uuid","type":"TEXTURE","additional":{"file":"splat.png"}}""",
        )
        png = File(texture, "splat.png")
        original = codec.encode(SplatPixels(side, ByteArray(side * side * 4)))
        png.writeBytes(original)
        terrainDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(terrain)!!
        terrainDir.refresh(false, true)
    }

    private val target get() = TerrainTarget("1", TerrainData(3, FloatArray(9), size, 1f), Matrix4())

    private fun mode(options: SplatBrushOptions = SplatBrushOptions(2, SplatMode.PAINT, true), reversed: Boolean = false) =
        BrushMode(SPLAT_BRUSH, "1", 8f, 1f, reversed, options)

    /** [inline] runs the encode and the apply at once; otherwise they wait in [queue]. */
    private fun command(
        store: AssetFileStore = LocalAssetFileStore(disk),
        inline: Boolean = true,
        current: () -> BrushMode? = { mode() },
    ) = SplatStrokeCommand(
        project, terrainDir, drafts, SplatTransactions(store), store,
        { SplatStateReader(testCore.json, ImageIoSplatCodec()).read(File(disk, "assets"), File(terrainDir.path)) },
        current,
        background = { if (inline) it.run() else queue += it },
        ui = { if (inline) it.run() else queue += it },
        report = { reports += it },
    )

    private fun stroke(c: SplatStrokeCommand, shift: Boolean = false) {
        assertTrue(c.pressed(target, Vector3(30f, 0f, 30f)))
        c.dragged(Vector3(40f, 0f, 30f), shift)
        c.released()
    }

    private fun weights() = codec.decode("splat.png", png.readBytes())

    fun testAStrokeWritesTheImageOnceAndDropsTheDraft() {
        stroke(command())
        val image = weights()
        assertTrue((image.rgba[(30 * side + 30) * 4 + 1].toInt() and 0xFF) > 0)
        assertEquals(0, image.rgba[(30 * side + 30) * 4].toInt())
        assertNull(drafts.of("texture_splat"))
        assertTrue(reports.isEmpty())
    }

    fun testNothingIsWrittenBeforeTheRelease() {
        val c = command()
        assertTrue(c.pressed(target, Vector3(30f, 0f, 30f)))
        c.dragged(Vector3(40f, 0f, 30f), false)
        assertNotNull(drafts.of("texture_splat"))
        assertEquals(original.toList(), png.readBytes().toList())
        c.released()
    }

    fun testAnEmptyStrokeWritesNothingAndAddsNoUndoStep() {
        stroke(command(current = { mode(SplatBrushOptions(1, SplatMode.ERASE, true)) }))
        assertEquals(original.toList(), png.readBytes().toList())
        val editor = TextEditorProvider.getInstance().createEditor(project, terrainDir.findChild("meta.json")!!)
        try {
            assertFalse(UndoManager.getInstance(project).isUndoAvailable(editor))
        } finally {
            Disposer.dispose(editor)
        }
    }

    fun testUndoAndRedoRestoreTheImage() {
        stroke(command())
        val painted = png.readBytes()
        val editor = TextEditorProvider.getInstance().createEditor(project, terrainDir.findChild("meta.json")!!)
        try {
            val undo = UndoManager.getInstance(project)
            assertTrue(undo.isUndoAvailable(editor))
            undo.undo(editor)
            assertEquals(original.toList(), png.readBytes().toList())
            undo.redo(editor)
            assertEquals(painted.toList(), png.readBytes().toList())
        } finally {
            Disposer.dispose(editor)
        }
    }

    fun testAFailedWriteRestoresTheFilesAndReportsTheReason() {
        val real = LocalAssetFileStore(disk)
        val failing = object : AssetFileStore by real {
            override fun write(path: String, bytes: ByteArray) = throw java.io.IOException("disk full")
        }
        stroke(command(failing))
        assertEquals(original.toList(), png.readBytes().toList())
        assertTrue(reports.single().contains("disk full"))
        assertNull(drafts.of("texture_splat"))
    }

    fun testAnExternalChangeDuringTheStrokeIsRefused() {
        val c = command()
        assertTrue(c.pressed(target, Vector3(30f, 0f, 30f)))
        c.dragged(Vector3(40f, 0f, 30f), false)
        val external = codec.encode(SplatPixels(side, ByteArray(side * side * 4) { 3 }))
        png.writeBytes(external)
        c.released()
        assertEquals(external.toList(), png.readBytes().toList())
        assertEquals(1, reports.size)
        assertNull(drafts.of("texture_splat"))
    }

    fun testEscapeDiscardsTheStrokeAndWritesNothing() {
        val c = command()
        assertTrue(c.pressed(target, Vector3(30f, 0f, 30f)))
        c.dragged(Vector3(40f, 0f, 30f), false)
        c.cancelled()
        c.released()
        assertEquals(original.toList(), png.readBytes().toList())
        assertNull(drafts.of("texture_splat"))
    }

    fun testAPressDuringACommitIsRefusedAndAStaleReleaseIsDropped() {
        val c = command(inline = false)
        stroke(c)
        assertNotNull("the draft stays until the write is applied", drafts.of("texture_splat"))
        assertFalse(c.pressed(target, Vector3(30f, 0f, 30f)))
        c.dispose()
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertEquals("a disposed view writes nothing", original.toList(), png.readBytes().toList())
        assertNull(drafts.of("texture_splat"))
    }

    fun testACommitThatIsAppliedWritesAndAllowsTheNextStroke() {
        val c = command(inline = false)
        stroke(c)
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertFalse(original.contentEquals(png.readBytes()))
        assertNull(drafts.of("texture_splat"))
        assertTrue(c.pressed(target, Vector3(10f, 0f, 10f)))
        c.cancelled()
    }

    fun testFillSetsTheWholeMapOnPress() {
        val c = command(current = { mode(SplatBrushOptions(3, SplatMode.FILL, true)) })
        assertTrue(c.pressed(target, Vector3(1f, 0f, 1f)))
        c.released()
        val image = weights()
        for (t in 0 until side * side) {
            assertEquals(0, image.rgba[t * 4].toInt())
            assertEquals(0, image.rgba[t * 4 + 1].toInt())
            assertEquals(255, image.rgba[t * 4 + 2].toInt() and 0xFF)
        }
    }

    fun testShiftLowersOnlyForPaint() {
        stroke(command())
        val before = weights().rgba[(30 * side + 30) * 4 + 1].toInt() and 0xFF
        stroke(command(current = { mode(reversed = true) }), shift = true)
        val after = weights().rgba[(30 * side + 30) * 4 + 1].toInt() and 0xFF
        assertTrue("before $before after $after reports $reports", after < before)
    }

    fun testAnUnusableMapRefusesThePress() {
        png.writeBytes(byteArrayOf(1, 2, 3))
        val c = command()
        assertFalse(c.pressed(target, Vector3(30f, 0f, 30f)))
        assertEquals(byteArrayOf(1, 2, 3).toList(), png.readBytes().toList())
    }
}
