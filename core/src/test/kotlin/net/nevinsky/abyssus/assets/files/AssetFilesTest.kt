/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.files

import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.sky.cube.SkyboxMeta
import net.nevinsky.abyssus.assets.testProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AssetFilesTest {
    private val json = JsonProcessor()
    private val files = AssetFiles(testProject("Untitled"), json)

    @Test fun rejectedSnapshotsHaveNoFilesOrUuidsAndReportOncePerRevision() {
        val dir = Files.createTempDirectory("native-snapshots").toFile()
        try {
            val asset = File(dir, "assets/m").apply { mkdirs() }
            File(asset, "model.gltf").writeText("model")
            var text = """{"type":"MODEL","uuid":"u","additional":{"file":"model.gltf"}}"""
            val messages = mutableListOf<String>()
            val snapshot = AssetFiles(dir, json, MetaTextSource { text }, log = net.nevinsky.abyssus.assets.AssetLog { message, _ -> messages += message })
            assertNull(snapshot.model("m"))
            assertNull(snapshot.metaType("m"))
            assertNull(snapshot.loadFile("m", "model.gltf"))
            assertEquals(1, messages.size)
            org.junit.Assert.assertTrue(messages.single().contains("meta.json"))
            org.junit.Assert.assertTrue(messages.single().contains("format"))
            text = """{"format":"abyssus","formatVersion":2,"type":"MODEL","additional":{"file":"model.gltf"}}"""
            assertNull(snapshot.model("m"))
            assertEquals(2, messages.size)
            text = """{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{"file":"model.gltf"}}"""
            assertEquals("model.gltf", snapshot.model("m")?.name)
            assertEquals(2, messages.size)
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun resolvesModelFileFromMeta() {
        val f = files.model("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb")
        assertEquals("model.gltf", f?.name)
    }

    @Test
    fun missingModelResolvesToNull() {
        assertNull(files.model("model_nope"))
        assertNull(files.model("../Untitled"))
        assertNull(files.model("skybox_default"))
    }

    @Test
    fun resolvesTerrain() {
        val t = files.terrain("terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b")!!
        assertEquals("terrain.data", t.data.name)
        assertEquals(1600, t.size)
        assertEquals(60f, t.uv, 0f)
        assertEquals(emptyMap<String, File>(), t.splat)
    }

    @Test
    fun brokenMetaResolvesToNull() {
        val dir = Files.createTempDirectory("proj").toFile()
        File(dir, "assets/m").mkdirs()
        File(dir, "assets/m/meta.json").writeText("{ not json")
        assertNull(AssetFiles(dir, json).model("m"))
        File(dir, "assets/m/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"additional":{"file":"missing.gltf"}}""")
        assertNull(AssetFiles(dir, json).model("m"))
    }

    @Test
    fun terrainSplatTexturesAreResolvedThroughTheirUuid() {
        val dir = Files.createTempDirectory("proj").toFile()
        File(dir, "assets/terr").mkdirs()
        File(dir, "assets/terr/terrain.data").writeBytes(ByteArray(16))
        File(dir, "assets/terr/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"additional":{"terrainFile":"terrain.data","size":10,"uv":2.0,"splatBase":"u-1","splatR":"u-missing"}}"""
        )
        File(dir, "assets/tex").mkdirs()
        File(dir, "assets/tex/a.png").writeText("x")
        File(dir, "assets/tex/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"uuid":"u-1","type":"TEXTURE","additional":{"file":"a.png"}}""")
        val t = AssetFiles(dir, json).terrain("terr")!!
        assertEquals(setOf("splatBase"), t.splat.keys)
        assertEquals("a.png", t.splat["splatBase"]!!.name)
    }

    @Test
    fun oneMetaReadServesTheJsonAndTheTypedLookups() {
        val dir = Files.createTempDirectory("proj").toFile()
        File(dir, "assets/sky").mkdirs()
        val meta = File(dir, "assets/sky/meta.json")
        meta.writeText("""{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"type":"SKYBOX","additional":{"top":"t.png"}}""")
        val files = AssetFiles(dir, json)
        assertEquals(MetaType.SKYBOX, files.metaType("sky"))
        assertEquals("t.png", files.loadAsset(SkyboxMeta::class.java, "sky")?.meta?.additional?.top)
        // rewritten with the same length and modification time: the stamp is unchanged, so the cached read answers
        val stamp = meta.lastModified()
        val text = meta.readText()
        meta.writeText(text.replace("t.png", "u.png")) // same length, same content size
        meta.setLastModified(stamp)
        assertEquals("t.png", files.loadAsset(SkyboxMeta::class.java, "sky")?.meta?.additional?.top)
        // a changed file is read again
        meta.writeText(text.replace("t.png", "other.png"))
        assertEquals("other.png", files.loadAsset(SkyboxMeta::class.java, "sky")?.meta?.additional?.top)
    }

    @Test
    fun metaTypeOfAnUnknownOrBrokenMetaIsUnknownOrNull() {
        val dir = Files.createTempDirectory("proj").toFile()
        File(dir, "assets/odd").mkdirs()
        File(dir, "assets/odd/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"type":"SOMETHING_NEW"}""")
        File(dir, "assets/bad").mkdirs()
        File(dir, "assets/bad/meta.json").writeText("{ not json")
        val files = AssetFiles(dir, json)
        assertEquals(MetaType.UNKNOWN, files.metaType("odd"))
        assertNull(files.metaType("bad"))
        assertNull(files.metaType("missing"))
    }

    private fun project(): File {
        val dir = Files.createTempDirectory("proj").toFile()
        File(dir, "assets/terr").mkdirs()
        File(dir, "assets/terr/terrain.data").writeBytes(ByteArray(16))
        File(dir, "assets/terr/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"additional":{"terrainFile":"terrain.data","size":10,"uv":2.0,"splatBase":"u-1"}}"""
        )
        return dir
    }

    private fun addTexture(dir: File, folder: String, uuid: String, image: String) {
        File(dir, "assets/$folder").mkdirs()
        File(dir, "assets/$folder/$image").writeText("x")
        File(dir, "assets/$folder/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"uuid":"$uuid","type":"TEXTURE","additional":{"file":"$image"}}""")
    }

    @Test
    fun theUuidIndexIsASnapshotUntilRefreshed() {
        val dir = project()
        val first = AssetFiles(dir, json)
        assertEquals(emptySet<String>(), first.terrain("terr")!!.splat.keys)
        addTexture(dir, "tex", "u-1", "a.png")
        assertEquals("the snapshot does not see a texture added later", emptySet<String>(), first.terrain("terr")!!.splat.keys)
        val second = first.refreshed()
        assertEquals(setOf("splatBase"), second.terrain("terr")!!.splat.keys)
        assertEquals(dir.absoluteFile, second.projectDir)
    }

    @Test
    fun aReplacedSplatImageIsFoundByANewSnapshot() {
        val dir = project()
        addTexture(dir, "tex", "u-1", "a.png")
        val first = AssetFiles(dir, json)
        assertEquals("a.png", first.terrain("terr")!!.splat["splatBase"]!!.name)
        File(dir, "assets/tex/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"uuid":"u-1","type":"TEXTURE","additional":{"file":"b.png"}}""")
        File(dir, "assets/tex/b.png").writeText("y")
        assertEquals("b.png", first.refreshed().terrain("terr")!!.splat["splatBase"]!!.name)
    }

    @Test
    fun aChangedTextureUuidUnresolvesTheReference() {
        val dir = project()
        addTexture(dir, "tex", "u-1", "a.png")
        File(dir, "assets/tex/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"uuid":"u-2","type":"TEXTURE","additional":{"file":"a.png"}}""")
        assertEquals(emptyMap<String, File>(), AssetFiles(dir, json).terrain("terr")!!.splat)
    }

    @Test
    fun anAbsentTextureLeavesTheTerrainLoadableWithoutIt() {
        val dir = project()
        val t = AssetFiles(dir, json).terrain("terr")!!
        assertEquals(10, t.size)
        assertEquals(emptyMap<String, File>(), t.splat)
    }

    @Test
    fun metadataIsReadThroughTheSuppliedSource() {
        val dir = project()
        val edited = MetaTextSource { f ->
            if (f.parentFile.name == "terr") """{"format":"abyssus","formatVersion":1,"additional":{"terrainFile":"terrain.data","size":77,"uv":3.0}}""" else f.readText()
        }
        val t = AssetFiles(dir, json, edited).terrain("terr")!!
        assertEquals(77, t.size)
        assertEquals(3f, t.uv, 0f)
        assertEquals("the disk is unchanged", 10, AssetFiles(dir, json).terrain("terr")!!.size)
    }

    @Test
    fun separateProjectsAreIndependent() {
        val a = project()
        val b = project()
        addTexture(a, "tex", "u-1", "a.png")
        assertEquals(setOf("splatBase"), AssetFiles(a, json).terrain("terr")!!.splat.keys)
        assertEquals(emptySet<String>(), AssetFiles(b, json).terrain("terr")!!.splat.keys)
    }
}
