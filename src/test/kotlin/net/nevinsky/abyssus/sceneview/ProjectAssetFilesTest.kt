/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProjectAssetFilesTest {
    private val files = ProjectAssetFiles(File("src/test/testData/project/Untitled"))

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
    fun resolvesSkybox() {
        val s = files.skybox("skybox_default")!!
        assertEquals("skybox_default.png", s.top.name)
        assertNotNull(s.back)
    }

    @Test
    fun brokenMetaResolvesToNull() {
        val dir = Files.createTempDirectory("proj").toFile()
        File(dir, "assets/m").mkdirs()
        File(dir, "assets/m/meta.json").writeText("{ not json")
        assertNull(ProjectAssetFiles(dir).model("m"))
        File(dir, "assets/m/meta.json").writeText("""{"additional":{"file":"missing.gltf"}}""")
        assertNull(ProjectAssetFiles(dir).model("m"))
    }

    @Test
    fun skyboxWithAMissingFaceResolvesToNull() {
        val dir = Files.createTempDirectory("proj").toFile()
        File(dir, "assets/sky").mkdirs()
        File(dir, "assets/sky/top.png").writeText("x")
        File(dir, "assets/sky/meta.json").writeText(
            """{"additional":{"top":"top.png","bottom":"top.png","left":"top.png","right":"top.png","front":"top.png","back":"gone.png"}}"""
        )
        assertNull(ProjectAssetFiles(dir).skybox("sky"))
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
        val t = ProjectAssetFiles(dir).terrain("terr")!!
        assertEquals(setOf("splatBase"), t.splat.keys)
        assertEquals("a.png", t.splat["splatBase"]!!.name)
    }
}
