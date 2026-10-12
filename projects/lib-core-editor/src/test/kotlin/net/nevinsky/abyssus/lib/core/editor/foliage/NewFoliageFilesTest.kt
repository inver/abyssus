/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import java.io.File
import java.nio.file.Files
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.foliage.FOLIAGE_DATA_FILE
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.editor.terrain.FolderNameError
import net.nevinsky.abyssus.lib.core.editor.terrainData
import net.nevinsky.abyssus.lib.core.editor.testProject
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class NewFoliageFilesTest {

    private val source: File = testProject("Untitled")
    private val terrainName = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val writer = FoliageAssetWriter(json)
    private val terrain by lazy { terrainData(source, terrainName) }

    /** The fixture project as a writable copy: staging never touches the committed `Untitled`. */
    private fun copy(): File {
        val dir = Files.createTempDirectory("new-foliage").toFile()
        source.copyRecursively(dir, overwrite = true)
        return dir
    }

    @Test
    fun `Create for the fixture terrain`() {
        val project = copy()
        val staged = writer.plan(project, terrainName, "meadow", FOLIAGE_MASK_RESOLUTION_DIALOG_DEFAULT, terrain)
        assertTrue(staged != null)
        staged!!

        val metaText = staged.metaText
        assertTrue(metaText.indexOf("\"format\"") < metaText.indexOf("\"version\""))
        assertTrue(metaText.indexOf("\"version\"") < metaText.indexOf("\"uuid\""))
        assertTrue(metaText.indexOf("\"uuid\"") < metaText.indexOf("\"type\""))
        assertTrue(metaText.indexOf("\"type\"") < metaText.indexOf("\"additional\""))
        assertFalse(metaText.contains("layers"))

        val folder = File(project, "assets/${staged.folder}")
        folder.mkdirs()
        File(folder, "meta.json").writeText(metaText)
        File(folder, FOLIAGE_DATA_FILE).writeBytes(staged.dataBytes)

        val files = FileLoader(project)
        val metas = AssetMetaLoader(json, files)
        val meta = metas.loadBaseMeta(staged.folder)!!
        assertEquals(MetaType.FOLIAGE, meta.type)
        assertEquals(staged.uuid, meta.uuid?.toString())

        val foliage = FoliageLoader(files, metas, TerrainLoader(files, metas)).prepare(staged.folder)!!.staged
        assertEquals(setOf(terrainName), foliage.dependencies)
        assertEquals(0, foliage.copyCount)
        assertFalse(foliage.stale)
    }

    @Test
    fun `Name taken`() {
        val project = copy()
        assertEquals(
            FoliageCreateError.FolderName(FolderNameError.EXISTS),
            writer.check(project, "tree", FOLIAGE_MASK_RESOLUTION_DIALOG_DEFAULT),
        )
        assertNull(writer.plan(project, terrainName, "tree", FOLIAGE_MASK_RESOLUTION_DIALOG_DEFAULT, terrain))
        assertFalse(File(project, "assets/tree/$FOLIAGE_DATA_FILE").exists())
    }

    @Test
    fun `Resolution out of range`() {
        val project = copy()
        assertEquals(FoliageCreateError.MaskResolution, writer.check(project, "meadow", 8))
        assertEquals(FoliageCreateError.MaskResolution, writer.check(project, "meadow", 4096))
        assertNull(writer.plan(project, terrainName, "meadow", 4096, terrain))
        assertTrue(writer.check(project, "meadow", FOLIAGE_MASK_RESOLUTION_MIN) == null)
        assertTrue(writer.check(project, "meadow", FOLIAGE_MASK_RESOLUTION_MAX) == null)
    }
}
