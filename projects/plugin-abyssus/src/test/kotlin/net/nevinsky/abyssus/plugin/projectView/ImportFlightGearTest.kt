/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.editor.flightgear.FlightGearImportRequest
import net.nevinsky.abyssus.lib.core.editor.flightgear.fixtureArchive
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.editor.flightgear.writeZip
import java.io.File

/** Import FlightGear Aircraft into a copy of the Untitled fixture. */
class ImportFlightGearTest : BasePlatformTestCase() {
    private lateinit var projectDir: File
    private lateinit var abss: VirtualFile
    private lateinit var archive: File

    override fun setUp() {
        super.setUp()
        projectDir = FileUtil.createTempDirectory("abyssus-flightgear", null)
        FileUtil.copyDir(File("src/test/testData/project/Untitled"), projectDir)
        abss = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(projectDir, "Untitled.abss"))!!
        abss.parent.refresh(false, true)
        archive = fixtureArchive(FileUtil.createTempDirectory("abyssus-flightgear-zip", null))
    }

    private fun request(): FlightGearImportRequest {
        val inspection = inspectArchive(archive).single()
        return FlightGearImportSettings(File(projectDir, "assets"), inspection).apply {
            originalSize = false
            spanText = "1.0"
        }.request()!!
    }

    private fun tree(dir: File): Map<String, List<Byte>> = dir.walkTopDown().filter { it.isFile }
        .associate { it.relativeTo(dir).path to it.readBytes().toList() }

    fun testImportCreatesANativeUnusedAssetAndTouchesNoSceneOrProject() {
        val before = tree(projectDir)
        val errors = mutableListOf<String>()
        assertEquals(AssetCommandResult.Done, importFlightGear(project, abss, archive, request(), errors::add))
        assertEquals(emptyList<String>(), errors)
        val folder = File(projectDir, "assets/model_fixture")
        val meta = JsonProcessor().readObject(File(folder, "meta.json").readText())
        assertNull(AbyssusDocumentFormat().validate(meta, DocumentKind.ASSET))
        assertEquals("MODEL", meta["type"].asText())
        assertTrue(File(folder, "model.glb").isFile)
        assertTrue(File(folder, "textures/skin.png").isFile)
        assertEquals("unknown", JsonProcessor().readObject(File(folder, "source.json").readText())["license"].asText())
        val after = tree(projectDir)
        assertEquals("only the new folder appears", before, after.filterKeys { !it.startsWith("assets/model_fixture/") })
        val uuids = File(projectDir, "assets").listFiles()!!.mapNotNull { File(it, "meta.json").takeIf(File::isFile) }
            .mapNotNull { JsonProcessor().readObject(it.readText())["uuid"]?.asText() }
        assertEquals("the uuid is fresh", uuids.size, uuids.toSet().size)
    }

    fun testUndoRemovesTheFolderAndRedoRestoresTheSameBytes() {
        assertEquals(AssetCommandResult.Done, importFlightGear(project, abss, archive, request()) { fail(it) })
        val folder = File(projectDir, "assets/model_fixture")
        val made = tree(folder)
        UndoManager.getInstance(project).undo(null)
        assertFalse(folder.exists())
        UndoManager.getInstance(project).redo(null)
        assertEquals(made, tree(folder))
    }

    fun testAnUnsupportedProjectIsRefusedBeforeWriting() {
        File(projectDir, "Untitled.abss").writeText("""{"name":"legacy"}""")
        abss.refresh(false, false)
        val errors = mutableListOf<String>()
        assertNull(importFlightGear(project, abss, archive, request(), errors::add))
        assertEquals(1, errors.size)
        assertFalse(File(projectDir, "assets/model_fixture").exists())
    }

    fun testAnArchiveWithoutAircraftOffersNothing() {
        val empty = File(FileUtil.createTempDirectory("abyssus-flightgear-empty", null), "empty.zip")
        writeZip(empty, mapOf("readme.txt" to "x".toByteArray()))
        assertTrue(inspectArchive(empty).isEmpty())
    }
}
