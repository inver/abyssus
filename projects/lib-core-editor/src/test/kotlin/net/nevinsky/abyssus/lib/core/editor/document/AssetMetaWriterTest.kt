/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.document

import java.io.File
import java.util.UUID
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.flightgear.FlightGearArchive
import net.nevinsky.abyssus.lib.core.editor.flightgear.FlightGearImport
import net.nevinsky.abyssus.lib.core.editor.flightgear.ImportSize
import net.nevinsky.abyssus.lib.core.editor.flightgear.FlightGearImportRequest
import net.nevinsky.abyssus.lib.core.editor.flightgear.fixtureArchive
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageAssetWriter
import net.nevinsky.abyssus.lib.core.editor.modelimport.ImportSettings
import net.nevinsky.abyssus.lib.core.editor.modelimport.LengthUnit
import net.nevinsky.abyssus.lib.core.editor.modelimport.ModelImport
import net.nevinsky.abyssus.lib.core.editor.modelimport.ModelSourceOpener
import net.nevinsky.abyssus.lib.core.editor.modelimport.importFixtures
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainAssetWriter
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainHeightEncoder
import net.nevinsky.abyssus.lib.core.editor.ui.UiAssetTemplate
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.assimp.UpAxis
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.slf4j.helpers.NOPLogger

class AssetMetaWriterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val uuid = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)

    private fun expected(kind: String) = javaClass.getResource("/asset-meta/$kind.json")!!.readText()

    @Test fun terrainBytesKeepFloatTextAndExplicitNullSplats() {
        assertEquals(expected("terrain"), TerrainAssetWriter(json, TerrainHeightEncoder()).meta(uuid, 1234, 800, 0.5f))
    }

    @Test fun foliageBytesKeepOmittedLayersAndNoNewline() {
        val writer = FoliageAssetWriter(json, clock = { 1234L }, randomUuid = { uuid })
        val files = writer.plan(tmp.root, "terrain", "meadow", 16, TerrainData(2, FloatArray(4), 800, 1f))!!
        assertEquals(expected("foliage"), files.metaText)
    }

    @Test fun uiBytesKeepTheInitialScreenAndTrailingNewline() {
        assertEquals(expected("ui"), UiAssetTemplate().create(uuid, 1234))
    }

    @Test fun modelBytesKeepEmptyMaterialsAndTrailingNewline() {
        val fixtures = importFixtures()
        ModelSourceOpener().open(File(fixtures, "crate.obj")).use { source ->
            val staged = ModelImport(json, AbyssusDocumentFormat()).stage(
                source, ImportSettings("model_crate", LengthUnit.CM, UpAxis.Y), fixtures, uuid, 1234,
            )
            assertEquals(expected("model"), staged.files.getValue("meta.json").toString(Charsets.UTF_8))
        }
    }

    @Test fun flightGearBytesKeepTheSameModelMetadata() {
        FlightGearArchive(fixtureArchive(tmp.root)).use { archive ->
            val staged = FlightGearImport(json, AbyssusDocumentFormat()).stage(
                archive, FlightGearImportRequest(archive.aircraft().single(), "model_fixture", ImportSize.Original), uuid, 1234,
            )
            assertEquals(expected("model"), staged.files.getValue("meta.json").toString(Charsets.UTF_8))
        }
    }

    @Test fun typedEnvelopeKeepsDeclarationOrderAndDefaults() {
        val document = AssetMetaDocument(lastModified = 1234, uuid = uuid.toString(), type = MetaType.MODEL,
            additional = ModelAssetAdditional())
        assertEquals(expected("model"), SceneJson().pretty(document))
    }

    @Test fun modelSourceRecordKeepsItsBytes() {
        val fixtures = importFixtures()
        ModelSourceOpener().open(File(fixtures, "missing/crate_missing.obj")).use { source ->
            val staged = ModelImport(json, AbyssusDocumentFormat()).stage(source,
                ImportSettings("model_crate", LengthUnit.CM, UpAxis.Y,
                    net.nevinsky.abyssus.lib.core.editor.modelimport.FitSize.Height(2.0)), fixtures, uuid, 1234)
            assertRecord("model-source", staged.files.getValue("source.json"))
        }
    }

    @Test fun flightGearSourceRecordKeepsItsBytes() {
        FlightGearArchive(fixtureArchive(tmp.root)).use { archive ->
            val staged = FlightGearImport(json, AbyssusDocumentFormat()).stage(archive,
                FlightGearImportRequest(archive.aircraft().single(), "model_fixture", ImportSize.Span(1.0), setOf("PropDisc")), uuid, 1234)
            assertRecord("flightgear-source", staged.files.getValue("source.json"))
        }
    }

    private fun assertRecord(name: String, bytes: ByteArray) {
        val text = bytes.toString(Charsets.UTF_8).replace(Regex("[0-9a-f]{64}"), "DIGEST")
        assertEquals(expected(name), text)
    }
}
