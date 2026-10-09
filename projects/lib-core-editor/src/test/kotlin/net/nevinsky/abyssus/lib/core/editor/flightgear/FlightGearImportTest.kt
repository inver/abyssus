/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.flightgear

import com.badlogic.gdx.files.FileHandle
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.imageio.ImageIO

class FlightGearImportTest {
    @get:Rule
    val temp = TemporaryFolder()
    private val json = JsonProcessor()
    private val importer = FlightGearImport(json, AbyssusDocumentFormat())
    private val uuid = UUID.fromString("6f1c2b5e-7d0a-4c39-9e61-1d3a2f5b8c90")

    private fun stage(license: Boolean = false, size: ImportSize = ImportSize.Span(1.0), excluded: Set<String> = setOf("PropDisc")): Pair<File, StagedImport> {
        val zip = fixtureArchive(temp.newFolder(), license)
        FlightGearArchive(zip).use { archive ->
            val request = FlightGearImportRequest(archive.aircraft().single(), "model_fixture", size, excluded)
            return zip to importer.stage(archive, request, uuid, 1767225600000)
        }
    }

    /** Each node's positions (x, y, z triples) from the staged GLB. */
    private fun positions(glb: ByteArray): Map<String, List<FloatArray>> {
        val doc = glbJson(glb)
        val jsonLength = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
        val bin = ByteBuffer.wrap(glb, 28 + jsonLength, glb.size - 28 - jsonLength).slice().order(ByteOrder.LITTLE_ENDIAN)
        return doc["nodes"].associate { node ->
            val mesh = doc["meshes"][node["mesh"].asInt()]
            node["name"].asText() to mesh["primitives"].flatMap { p ->
                val accessor = doc["accessors"][p["attributes"]["POSITION"].asInt()]
                val view = doc["bufferViews"][accessor["bufferView"].asInt()]
                (0 until accessor["count"].asInt()).map { i ->
                    FloatArray(3) { c -> bin.getFloat(view["byteOffset"].asInt() + (i * 3 + c) * 4) }
                }
            }
        }
    }

    private fun List<FloatArray>.min(c: Int) = minOf { it[c] }
    private fun List<FloatArray>.max(c: Int) = maxOf { it[c] }

    @Test
    fun inspectionListsPartsTheirRestStateAndWhatIsSkipped() {
        FlightGearArchive(fixtureArchive(temp.root)).use { archive ->
            val inspection = importer.inspect(archive, archive.aircraft().single())
            assertEquals(listOf("Body", "Wing", "Prop", "PropDisc", "Tail", "Wheel"), inspection.parts.map { it.name })
            assertEquals(listOf("PropDisc"), inspection.parts.filter { !it.shownAtRest }.map { it.name })
            assertTrue(inspection.skipped.any { it.reason == "panels are not imported" })
            assertTrue(inspection.skipped.any { it.item == "missing.rgb" })
            assertNull(inspection.license)
        }
    }

    @Test
    fun theModelIsInTheAbyssusFrameScaledCentredAndStanding() {
        val (_, staged) = stage()
        val parts = positions(staged.files.getValue(IMPORTED_MODEL_FILE))
        assertFalse("PropDisc was excluded", "PropDisc" in parts)
        assertEquals(setOf("Body", "Wing", "Prop", "Tail", "Wheel"), parts.keys)
        val all = parts.values.flatten()
        assertEquals(1f, all.max(0) - all.min(0), 1e-4f)
        assertEquals(0f, all.min(0) + all.max(0), 1e-4f)
        assertEquals(0f, all.min(1), 1e-6f)
        assertEquals(0f, all.min(2) + all.max(2), 1e-4f)
        // the propeller is the nose: the +Z end; the wing reaches both tips; the nested wheel sits forward and lowest
        assertEquals(all.max(2), parts.getValue("Prop").max(2), 1e-5f)
        assertEquals(0.5f, parts.getValue("Wing").max(0), 1e-4f)
        assertTrue(parts.getValue("Wheel").min(2) > 0f)
        assertEquals(0f, parts.getValue("Wheel").min(1), 1e-6f)
        assertTrue(parts.getValue("Wing").min(1) > parts.getValue("Body").min(1))
    }

    @Test
    fun aSourcePointBecomesTheOrigin() {
        val zip = fixtureArchive(temp.newFolder())
        val staged = FlightGearArchive(zip).use { archive ->
            // the FlightGear body point of AC vertex (1, 0.6, 0): the body's top rear edge (x aft, z up)
            val request = FlightGearImportRequest(archive.aircraft().single(), "m", ImportSize.Original, origin = ImportOrigin.SourcePoint(1.0, 0.0, 0.6))
            importer.stage(archive, request, uuid, 1L)
        }
        val body = positions(staged.files.getValue(IMPORTED_MODEL_FILE)).getValue("Body")
        assertEquals(0f, body.min(2), 1e-5f) // the tail end is at Z = 0
        assertEquals(0f, body.max(1), 1e-5f) // the body's top is at Y = 0
        assertEquals(2f, body.max(2), 1e-5f) // the nose is 2 m ahead
    }

    @Test
    fun originalSizeKeepsMetres() {
        val (_, staged) = stage(size = ImportSize.Original, excluded = emptySet())
        val all = positions(staged.files.getValue(IMPORTED_MODEL_FILE)).values.flatten()
        assertEquals(3f, all.max(0) - all.min(0), 1e-4f)
    }

    @Test
    fun texturesAreConvertedAndMissingOnesReported() {
        val (_, staged) = stage()
        val png = ImageIO.read(ByteArrayInputStream(staged.files.getValue("textures/skin.png")))
        assertEquals(4, png.width)
        assertEquals(fixtureSkinPixel(1, 0, 0), (png.getRGB(1, 0) shr 16) and 0xFF)
        val doc = glbJson(staged.files.getValue(IMPORTED_MODEL_FILE))
        assertEquals(listOf("textures/skin.png"), doc["images"].map { it["uri"].asText() })
        assertTrue(staged.skipped.any { it.item == "missing.rgb" })
        assertTrue(staged.skipped.any { it.item.startsWith("Body: 1 line surfaces") })
        assertTrue(staged.skipped.any { it.reason == "panels are not imported" })
    }

    @Test
    fun theMetaIsNativeAndTheSourceRecordsOrigin() {
        val (zip, staged) = stage()
        val meta = json.readObject(String(staged.files.getValue("meta.json")))
        assertNull(AbyssusDocumentFormat().validate(meta, DocumentKind.ASSET))
        assertEquals(uuid.toString(), meta["uuid"].asText())
        assertEquals("MODEL", meta["type"].asText())
        assertEquals(IMPORTED_MODEL_FILE, meta["additional"]["file"].asText())
        assertTrue(meta["additional"]["binary"].asBoolean())
        val source: JsonNode = json.readObject(String(staged.files.getValue("source.json")))
        assertEquals("unknown", source["license"].asText())
        assertEquals(FlightGearArchive(zip).use { it.sha256() }, source["archiveSha256"].asText())
        assertEquals("fixture.zip", source["archive"].asText())
        assertEquals("Models/fixture.xml", source["model"].asText())
        assertEquals(listOf("PropDisc"), source["excludedParts"].map { it.asText() })
        assertEquals(1.0, source["size"]["span"].asDouble(), 0.0)
    }

    @Test
    fun licenceFilesAreCopiedAndNamed() {
        val (_, staged) = stage(license = true)
        assertEquals("Fixture licence text\n", String(staged.files.getValue("COPYING")))
        assertEquals("see COPYING", json.readObject(String(staged.files.getValue("source.json")))["license"].asText())
    }

    @Test
    fun theSameImportStagesTheSameBytes() {
        val zip = fixtureArchive(temp.newFolder())
        val runs = (1..2).map {
            FlightGearArchive(zip).use { archive ->
                importer.stage(archive, FlightGearImportRequest(archive.aircraft().single(), "m", ImportSize.Span(1.0)), uuid, 1L)
            }
        }
        assertEquals(runs[0].files.keys, runs[1].files.keys)
        for (key in runs[0].files.keys) assertTrue(key, runs[0].files.getValue(key).contentEquals(runs[1].files.getValue(key)))
    }

    @Test
    fun theStagedFolderLoads() {
        val (_, staged) = stage()
        val folder = temp.newFolder("model_fixture")
        for ((path, bytes) in staged.files) File(folder, path).apply { parentFile.mkdirs() }.writeBytes(bytes)
        val data = AssimpModelLoader().loadData(FileHandle(File(folder, IMPORTED_MODEL_FILE)))
        val names = data.nodes.flatMap { root -> listOf(root) + (root.children?.toList() ?: emptyList()) }.map { it.id }
        assertTrue(names.toString(), names.containsAll(listOf("Body", "Wing", "Prop", "Tail", "Wheel")))
        assertFalse(File(folder, "embedded").exists())
    }

    @Test
    fun theCessnaImportsAtOneMetre() {
        val zip = cessnaArchive()
        assumeTrue("c172r.zip is not on this machine", zip != null)
        FlightGearArchive(zip!!).use { archive ->
            val aircraft = archive.aircraft().single()
            assertEquals("Cessna 172R", aircraft.description)
            val inspection = importer.inspect(archive, aircraft)
            assertEquals(listOf("Propeller.2"), inspection.parts.filter { !it.shownAtRest }.map { it.name })
            assertNull(inspection.license)
            val staged = importer.stage(archive, FlightGearImportRequest(aircraft, "model_c172r", ImportSize.Span(1.0), setOf("Propeller.2")), uuid, 1L)
            val parts = positions(staged.files.getValue(IMPORTED_MODEL_FILE))
            val all = parts.values.flatten()
            assertEquals(1f, all.max(0) - all.min(0), 1e-4f)
            assertEquals(0f, all.min(1), 1e-6f)
            assertEquals(all.max(2), parts.getValue("Hub").max(2), 1e-4f) // the spinner is the nose
            assertTrue(parts.getValue("Wings").min(1) > parts.getValue("NoseWheel").max(1)) // a high wing above the gear
            assertEquals(0f, minOf(parts.getValue("LeftWheel").min(1), parts.getValue("NoseWheel").min(1)), 1e-4f)
            assertTrue(parts.getValue("LeftAileron").min(0) > 0f) // left is +X
            assertTrue(setOf("textures/c172-01.png", "textures/c172-02.png").all { it in staged.files })
        }
    }
}
