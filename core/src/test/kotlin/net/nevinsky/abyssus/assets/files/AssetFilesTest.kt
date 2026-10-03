/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.files

import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.testProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AssetFilesTest {
    private val json = JsonProcessor()
    private val files = AssetFiles(testProject("Untitled"), json)

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
        File(dir, "assets/m/meta.json").writeText("""{"additional":{"file":"missing.gltf"}}""")
        assertNull(AssetFiles(dir, json).model("m"))
    }

    @Test
    fun terrainSplatTexturesAreResolvedThroughTheirUuid() {
        val dir = Files.createTempDirectory("proj").toFile()
        File(dir, "assets/terr").mkdirs()
        File(dir, "assets/terr/terrain.data").writeBytes(ByteArray(16))
        File(dir, "assets/terr/meta.json").writeText(
            """{"additional":{"terrainFile":"terrain.data","size":10,"uv":2.0,"splatBase":"u-1","splatR":"u-missing"}}"""
        )
        File(dir, "assets/tex").mkdirs()
        File(dir, "assets/tex/a.png").writeText("x")
        File(dir, "assets/tex/meta.json").writeText("""{"uuid":"u-1","type":"TEXTURE","additional":{"file":"a.png"}}""")
        val t = AssetFiles(dir, json).terrain("terr")!!
        assertEquals(setOf("splatBase"), t.splat.keys)
        assertEquals("a.png", t.splat["splatBase"]!!.name)
    }
}
