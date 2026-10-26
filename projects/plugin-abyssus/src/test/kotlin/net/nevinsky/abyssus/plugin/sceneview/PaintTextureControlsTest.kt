/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

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
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.pick.SPLAT_BRUSH
import net.nevinsky.abyssus.lib.core.editor.pick.SceneInteraction
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.ImageIoSplatCodec
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatPixels
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatStateReader
import net.nevinsky.abyssus.plugin.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.plugin.splat.SplatBrushOptions
import net.nevinsky.abyssus.plugin.splat.SplatDrafts
import net.nevinsky.abyssus.plugin.splat.SplatMode
import net.nevinsky.abyssus.plugin.splat.SplatSession
import net.nevinsky.abyssus.plugin.splat.SplatStateCache
import net.nevinsky.abyssus.plugin.splat.SplatStrokeCommand
import net.nevinsky.abyssus.plugin.splat.SplatTransactions
import net.nevinsky.abyssus.plugin.testCore
import java.awt.Component
import java.awt.Container
import java.io.File
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel

class PaintTextureControlsTest : BasePlatformTestCase() {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private val side = 64
    private val uuid = "aaaaaaaa-0000-0000-0000-000000000001"
    private val codec = ImageIoSplatCodec()
    private lateinit var disk: File
    private lateinit var terrainDir: VirtualFile
    private lateinit var png: File
    private val reports = mutableListOf<String>()

    override fun setUp() {
        super.setUp()
        disk = FileUtil.createTempDirectory("abyssus-paint", null)
    }

    private fun project(splatMap: String?, image: ByteArray? = codec.encode(SplatPixels(side, ByteArray(side * side * 4)))) {
        val terrain = File(disk, "assets/terrain_a").apply { mkdirs() }
        val ref = splatMap?.let { ""","splatMap":"$it"""" }.orEmpty()
        File(terrain, "meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"version":1,"uuid":"t","type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":63,"uv":8.0$ref}}""",
        )
        val texture = File(disk, "assets/texture_splat").apply { mkdirs() }
        File(texture, "meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"version":1,"uuid":"$uuid","type":"TEXTURE","additional":{"file":"splat.png"}}""",
        )
        png = File(texture, "splat.png")
        image?.let(png::writeBytes)
        terrainDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(terrain)!!
        terrainDir.refresh(false, true)
    }

    private fun session(): SplatSession {
        val store = LocalAssetFileStore(disk)
        val cache = SplatStateCache(SplatStateReader(testCore.json, ImageIoSplatCodec()), File(disk, "assets"), terrainDir)
        val command = SplatStrokeCommand(
            this.project, terrainDir, SplatDrafts(), SplatTransactions(store), store, cache::current, { splatMode },
            background = { it.run() }, ui = { it.run() }, report = { reports += it },
        )
        return SplatSession(this.project, terrainDir, disk, testCore.json, store, testCore.assetEditor, command, command::dispose, cache)
    }

    private var splatMode: BrushMode? = null

    private fun named(c: Component, name: String): Component? = if (c.name == name) c else
        (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }

    private fun controls(renderer: SceneRenderer, onEnded: () -> Unit = {}): PaintTextureControls {
        val interaction = SceneInteraction(renderer.state, renderer.queries, OrbitCamera(Vector3(), 10f, 0f, 0.5f))
        val session = session()
        return PaintTextureControls(
            renderer, interaction, PlayState(null), BrushKinds(), { _, mode, ready -> splatMode = mode(); ready(session) },
            {}, onEnded, { reports += it },
        ).also {
            renderer.state.selectedId = "1"
            it.refreshSession(force = true)
            it.begin("1")
            it.show(session.snapshot())
        }
    }

    private val target get() = TerrainTarget("1", TerrainData(3, FloatArray(9), 63, 1f), Matrix4())

    fun testTheStripAppearsWithTheFirstLayerChosenAndExclusiveOn() {
        project(uuid)
        val renderer = testRenderer()
        val c = controls(renderer)
        val mode = renderer.state.brush!!
        assertEquals(SPLAT_BRUSH, mode.kind)
        assertEquals(1, (mode.options as SplatBrushOptions).slot)
        assertTrue((mode.options as SplatBrushOptions).exclusive)
        assertEquals(SplatMode.PAINT, (mode.options as SplatBrushOptions).mode)
        assertTrue((named(c.strip(), "paint-texture-exclusive") as JCheckBox).isSelected)
        assertEquals(5, (0 until 5).count { named(c.strip(), "paint-texture-slot-$it") != null })
        assertFalse(named(c.strip(), "paint-texture-create-panel")!!.isVisible)
    }

    fun testShiftReachesReversedForPaintOnly() {
        project(uuid)
        val renderer = testRenderer()
        val c = controls(renderer)
        c.update(shift = true)
        assertTrue(renderer.state.brush!!.reversed)
        (named(c.strip(), "paint-texture-mode") as JComboBox<*>).selectedItem = SplatMode.ERASE
        c.update(shift = true)
        assertFalse(renderer.state.brush!!.reversed)
        assertEquals(SplatMode.ERASE, (renderer.state.brush!!.options as SplatBrushOptions).mode)
    }

    fun testAnUnusableMapShowsItsReasonOffersCreateAndAPressPaintsNothing() {
        project(uuid, image = byteArrayOf(1, 2, 3))
        val renderer = testRenderer()
        val c = controls(renderer)
        val reason = named(c.strip(), "paint-texture-reason") as JLabel
        assertTrue(reason.isVisible)
        assertTrue(reason.text.isNotBlank())
        assertTrue(named(c.strip(), "paint-texture-create-panel")!!.isVisible)
        splatMode = renderer.state.brush
        assertFalse(session().stroke!!.pressed(target, Vector3(30f, 0f, 30f)))
        assertEquals(listOf<Byte>(1, 2, 3), png.readBytes().toList())
    }

    fun testCreateAndMakeUniqueGoThroughTheTransactions() {
        project(null)
        val renderer = testRenderer()
        val c = controls(renderer)
        assertTrue(named(c.strip(), "paint-texture-create-panel")!!.isVisible)
        val before = File(terrainDir.path, "meta.json").readText()
        c.createMap()
        val after = File(terrainDir.path, "meta.json").readText()
        assertTrue(after.contains("\"splatMap\""))
        val created = File(disk, "assets").listFiles()!!.single { it.name.startsWith("texture_terrain_a_splat") }
        assertEquals(1024, codec.decode("splat.png", File(created, "splat.png").readBytes()).side)
        val undo = UndoManager.getInstance(this.project)
        assertTrue(undo.isUndoAvailable(null))
        undo.undo(null)
        assertEquals(before, File(terrainDir.path, "meta.json").readText())
        assertFalse(created.exists())
    }

    fun testASharedMapWarnsAndMakeUniqueCopiesIt() {
        project(uuid)
        val other = File(disk, "assets/terrain_b").apply { mkdirs() }
        File(other, "meta.json").writeText(File(terrainDir.path, "meta.json").readText())
        val renderer = testRenderer()
        val c = controls(renderer)
        val shared = named(c.strip(), "paint-texture-shared") as JLabel
        assertTrue(shared.isVisible)
        assertTrue(shared.text.contains("terrain_b"))
        val otherBefore = File(other, "meta.json").readText()
        c.makeUniqueCopy()
        assertEquals(otherBefore, File(other, "meta.json").readText())
        assertFalse(File(terrainDir.path, "meta.json").readText().contains(uuid.substring(0, 8) + "-0000-0000-0000-000000000001\""))
        val copy = File(disk, "assets").listFiles()!!.single { it.name.startsWith("texture_terrain_a_splat") }
        assertEquals(png.readBytes().toList(), File(copy, "splat.png").readBytes().toList())
    }

    fun testAssignClearAndUndoOfALayerSlot() {
        project(uuid)
        val renderer = testRenderer()
        val c = controls(renderer)
        val session = session()
        val metaFile = File(terrainDir.path, "meta.json")
        val slotG = session.snapshot().layers[2]
        assertNull(slotG.uuid)
        session.assign(slotG, uuid)
        assertTrue(metaFile.readText().contains("\"splatG\":\"$uuid\"") || com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getCachedDocument(terrainDir.findChild("meta.json")!!)!!.text.contains("splatG"))
        val assigned = session.snapshot().layers[2]
        assertEquals("texture_splat", assigned.name)
        session.assign(assigned, null)
        assertNull(session.snapshot().layers[2].uuid)
        val undo = UndoManager.getInstance(this.project)
        val editor = TextEditorProvider.getInstance().createEditor(this.project, terrainDir.findChild("meta.json")!!)
        try {
            undo.undo(editor)
            assertEquals(uuid, session.snapshot().layers[2].uuid)
        } finally {
            Disposer.dispose(editor)
        }
        c.update()
    }

    fun testAnUnresolvedLayerStaysSelectableAndPaintable() {
        project(uuid)
        File(terrainDir.path, "meta.json").writeText(File(terrainDir.path, "meta.json").readText().replace("\"splatMap\"", "\"splatR\":\"gone\",\"splatMap\""))
        terrainDir.refresh(false, true)
        val renderer = testRenderer()
        val c = controls(renderer)
        val layer = session().snapshot().layers[1]
        assertFalse(layer.resolved)
        (named(c.strip(), "paint-texture-slot-1") as javax.swing.JToggleButton).doClick()
        assertEquals(1, (renderer.state.brush!!.options as SplatBrushOptions).slot)
        assertTrue((named(c.strip(), "paint-texture-slot-1") as javax.swing.JToggleButton).text.contains("gone"))
    }

    private fun PaintTextureControls.strip(): Container = stripForTest()

    fun testSelectingAnotherEntityEndsPaintingAndSculptingEndsIt() {
        project(uuid)
        val renderer = testRenderer()
        var ended = 0
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer,
            sculptSession = { _, _, _, ready -> ready(null) },
            splatSession = { _, mode, ready -> splatMode = mode(); ready(session()) })
        panel.onPaintTextureEnded = { ended++ }
        try {
            panel.selectEntity("1")
            panel.setPaintingTexture("1")
            assertEquals(SPLAT_BRUSH, renderer.state.brush?.kind)
            panel.selectEntity("2")
            assertNull(renderer.state.brush)
            assertEquals("the host is told when the view ends it", 1, ended)
            panel.selectEntity("1")
            panel.setPaintingTexture("1")
            panel.setPaintingTexture(null)
            assertNull(renderer.state.brush)
            assertEquals("a host-requested end is not reported back", 1, ended)
        } finally { panel.dispose() }
    }
}
