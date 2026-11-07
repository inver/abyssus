/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelimport

import com.badlogic.gdx.files.FileHandle
import java.io.File
import java.nio.file.Files
import java.util.UUID
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.assimp.UpAxis
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.PbrModelMaterial
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelImportTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
    private val format = AbyssusDocumentFormat()
    private val import = ModelImport(json)
    private val fixtures = importFixtures()
    private val uuid = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e")

    private fun stage(name: String, settings: ImportSettings, projectDir: File = fixtures): StagedModelImport =
        ModelSourceOpener().open(File(fixtures, name)).use {
            import.stage(it, settings, projectDir, uuid, 1_700_000_000_000)
        }

    /** The material the model's first node part draws with (Assimp's glTF importer adds an unused default one). */
    private fun usedMaterial(data: ModelData) =
        data.materials.first { m ->
            data.nodes.flatMap { allNodes(it) }.flatMap { it.parts.orEmpty().toList() }.any { it.materialId == m.id }
        }

    /** Writes the staged folder into a temp folder and reads its model back as the editor loads assets. */
    private fun reload(staged: StagedModelImport): Pair<File, ModelData> {
        val folder = Files.createTempDirectory("abyssus_staged").toFile()
        staged.files.forEach { (path, bytes) -> File(folder, path).apply { parentFile.mkdirs() }.writeBytes(bytes) }
        return folder to AssimpModelLoader().loadData(FileHandle(File(folder, MODEL_FILE)))
    }

    @Test
    fun anObjStagesTheAssetFolder() {
        val staged = stage("crate.obj", ImportSettings("model_crate", LengthUnit.CM, UpAxis.Y))
        assertEquals(listOf("meta.json", "model.glb", "textures/wood.png", "source.json"), staged.files.keys.toList())

        val meta = json.readObject(String(staged.files.getValue("meta.json")))
        assertNull(format.validate(meta, DocumentKind.ASSET))
        assertEquals(
            listOf("formatVersion", "version", "lastModified", "uuid", "type", "additional"),
            meta.fieldNames().asSequence().toList(),
        )
        assertEquals(uuid.toString(), meta["uuid"].asText())
        assertEquals("MODEL", meta["type"].asText())
        assertEquals(
            """{"file":"model.glb","format":"GLTF","binary":true,"materials":[]}""",
            meta["additional"].toString(),
        )

        val (folder, data) = reload(staged)
        val texture = usedMaterial(data).textures.single().fileName
        assertEquals(File(folder, "textures/wood.png").canonicalPath, File(texture).canonicalPath)
        assertTrue("nothing is extracted on load", !File(folder, "embedded").exists())
        assertEquals(1f, staged.report.size.y, 1e-5f)
        assertEquals(1, staged.report.textures)
    }

    @Test
    fun anFbxKeepsItsAnimationsAndJoints() {
        val staged = stage("rig.fbx", ImportSettings("model_rig", LengthUnit.CM, UpAxis.Z))
        val (_, data) = reload(staged)
        assertEquals(listOf("Idle" to 2f, "Run" to 1f), data.animations.map { it.id to it.seconds() })
        val bones =
            data.nodes.flatMap { allNodes(it) }.flatMap { it.parts.orEmpty().toList() }.mapNotNull { it.bones }.single()
        assertEquals(2, bones.size)
        assertTrue(staged.files.keys.any { it.startsWith("textures/") && it.endsWith(".png") })
        assertEquals(2f, staged.report.size.y, 1e-4f)
    }

    private fun allNodes(
        node: com.badlogic.gdx.graphics.g3d.model.data.ModelNode
    ): List<com.badlogic.gdx.graphics.g3d.model.data.ModelNode> =
        listOf(node) + node.children.orEmpty().flatMap { allNodes(it) }

    @Test
    fun aGlbKeepsItsMetallicRoughness() {
        val staged = stage("crate.glb", ImportSettings("model_metal", LengthUnit.M, UpAxis.Y))
        val (_, data) = reload(staged)
        val material = usedMaterial(data) as PbrModelMaterial
        assertEquals(1f, material.metallic!!, 1e-6f)
        assertEquals(0.3f, material.roughness!!, 1e-6f)
        assertEquals(listOf("textures/embedded_0.png"), staged.files.keys.filter { it.startsWith("textures/") })
        assertTrue(staged.report.approximated.isEmpty())
        val source = json.readObject(String(staged.files.getValue(SOURCE_FILE)))
        assertEquals(0, source["approximated"].size())
    }

    /**
     * `crate.glb` with its material given emissive factor (0.39, 0.78, 1.0) and `KHR_materials_emissive_strength` 10,
     * and `KHR_texture_transform` declared too (an extension the renderer does not use), in a folder of its own.
     */
    private fun emissiveCrate(): File {
        val glb = File(fixtures, "crate.glb").readBytes()
        val buffer = java.nio.ByteBuffer.wrap(glb).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val jsonLength = buffer.getInt(12)
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        val doc = mapper.readTree(glb.copyOfRange(20, 20 + jsonLength)) as com.fasterxml.jackson.databind.node.ObjectNode
        doc.putArray("extensionsUsed").add("KHR_materials_emissive_strength").add("KHR_texture_transform")
        for (material in doc["materials"]) {
            material as com.fasterxml.jackson.databind.node.ObjectNode
            material.putArray("emissiveFactor").add(0.39).add(0.78).add(1.0)
            material.putObject("extensions").putObject("KHR_materials_emissive_strength").put("emissiveStrength", 10.0)
        }
        var text = mapper.writeValueAsBytes(doc)
        text += ByteArray((4 - text.size % 4) % 4) { ' '.code.toByte() }
        val rest = glb.copyOfRange(20 + jsonLength, glb.size)
        val out = java.nio.ByteBuffer.allocate(20 + text.size + rest.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67).putInt(2).putInt(20 + text.size + rest.size)
        out.putInt(text.size).putInt(0x4E4F534A).put(text).put(rest)
        val dir = Files.createTempDirectory("abyssus_emissive").toFile()
        return File(dir, "emissive_crate.glb").apply { writeBytes(out.array()) }
    }

    @Test
    fun aGlbKeepsItsEmissiveStrength() {
        val source = emissiveCrate()
        val staged =
            ModelSourceOpener().open(source).use {
                import.stage(it, ImportSettings("model_lamp", LengthUnit.M, UpAxis.Y), fixtures, uuid, 1_700_000_000_000)
            }
        val left = staged.report.leftOut.map { it.item }
        assertTrue("emissive strength reported as left out: $left", "KHR_materials_emissive_strength" !in left)
        assertTrue("an unused extension is still reported: $left", "KHR_texture_transform" in left)
        val record = json.readObject(String(staged.files.getValue(SOURCE_FILE)))
        assertTrue(record["skipped"].none { it["item"].asText() == "KHR_materials_emissive_strength" })
        val (_, data) = reload(staged)
        val material = usedMaterial(data) as PbrModelMaterial
        assertEquals(10f, material.emissiveStrength, 1e-5f)
        assertEquals(0.78f, material.emissive!!.g, 1e-4f)
    }

    @Test
    fun aDaeWithItsStatedFrameIsOneMetreAndUpright() {
        val staged = stage("crate.dae", ImportSettings("model_dae", LengthUnit.CM, UpAxis.Z))
        val (_, data) = reload(staged)
        val box = ImportTransform().restBounds(data)
        assertEquals(1f, box.width, 1e-4f)
        assertEquals(1f, box.height, 1e-4f)
        assertEquals(1f, box.depth, 1e-4f)
        assertEquals(0f, box.min.y, 1e-4f)
    }

    @Test
    fun sourceJsonRecordsTheFrameAndWhatWasLeftOut() {
        val staged =
            stage(
                "missing/crate_missing.obj",
                ImportSettings("model_crate", LengthUnit.CM, UpAxis.Y, FitSize.Height(2.0)),
            )
        val source = json.readObject(String(staged.files.getValue(SOURCE_FILE)))
        assertEquals(
            listOf(
                "importer",
                "source",
                "sourcePath",
                "sourceSha256",
                "sourceFormat",
                "stated",
                "chosen",
                "size",
                "animations",
                "skipped",
                "approximated",
            ),
            source.fieldNames().asSequence().toList(),
        )
        assertEquals("model", source["importer"].asText())
        assertEquals("crate_missing.obj", source["source"].asText())
        assertEquals("OBJ", source["sourceFormat"].asText())
        assertEquals("{}", source["stated"].toString())
        assertEquals("""{"unit":"cm","upAxis":"Y"}""", source["chosen"].toString())
        assertEquals("""{"height":2.0}""", source["size"].toString())
        assertEquals("""[{"item":"wood.png","reason":"missing"}]""", source["skipped"].toString())

        val fbx =
            json.readObject(
                String(stage("rig.fbx", ImportSettings("r", LengthUnit.CM, UpAxis.Z)).files.getValue(SOURCE_FILE))
            )
        assertEquals("""{"unit":"cm","upAxis":"Z"}""", fbx["stated"].toString())
        assertEquals("""["Idle","Run"]""", fbx["animations"].toString())
        assertEquals("\"original\"", fbx["size"].toString())
    }

    @Test
    fun theSourcePathIsRelativeInsideTheProject() {
        val project = Files.createTempDirectory("abyssus_project").toFile()
        val sources = File(project, "sources").apply { mkdirs() }
        listOf("crate.obj", "crate.mtl", "wood.png").forEach { File(fixtures, it).copyTo(File(sources, it)) }
        ModelSourceOpener().open(File(sources, "crate.obj")).use { source ->
            val inside = import.stage(source, ImportSettings("c", LengthUnit.CM, UpAxis.Y), project, uuid, 0)
            assertEquals(
                "sources/crate.obj",
                json.readObject(String(inside.files.getValue(SOURCE_FILE)))["sourcePath"].asText(),
            )
        }
        val outside = stage("crate.obj", ImportSettings("c", LengthUnit.CM, UpAxis.Y), project)
        assertEquals(
            File(fixtures, "crate.obj").canonicalPath,
            json.readObject(String(outside.files.getValue(SOURCE_FILE)))["sourcePath"].asText(),
        )
    }

    @Test
    fun theSameImportStagesTheSameBytes() {
        val a = stage("rig.fbx", ImportSettings("r", LengthUnit.CM, UpAxis.Z))
        val b = stage("rig.fbx", ImportSettings("r", LengthUnit.CM, UpAxis.Z))
        assertEquals(a.files.keys, b.files.keys)
        a.files.forEach { (path, bytes) -> assertArrayEquals(path, bytes, b.files[path]) }
    }
}
