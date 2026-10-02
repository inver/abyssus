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

import net.nevinsky.abyssus.sceneview.terrain.TerrainData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files

class TerrainDataTest {
    private fun terrainFile(vararg heights: Float): File {
        val f = Files.createTempFile("terrain", ".data").toFile()
        DataOutputStream(f.outputStream()).use { out -> heights.forEach(out::writeFloat) }
        return f
    }

    @Test
    fun readsTheTestProjectsTerrain() {
        val file = File("src/test/testData/project/Untitled/assets/terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b/terrain.data")
        val t = TerrainData.read(file, 1600, 60f)
        assertEquals(180, t.resolution)
        assertEquals(180 * 180, t.heights.size)
        assertTrue(t.heights.all { it.isFinite() })
    }

    @Test
    fun readsBigEndianHeightsAndBuildsVertices() {
        val t = TerrainData.read(terrainFile(0f, 1f, 2f, 3f), 10, 4f)
        assertEquals(2, t.resolution)
        val v = t.vertices()
        assertEquals(4 * TerrainData.FLOATS_PER_VERTEX, v.size)
        // vertex (x=1, z=0): position, uv
        val i = 1 * TerrainData.FLOATS_PER_VERTEX
        assertEquals(10f, v[i], 0f)
        assertEquals(1f, v[i + 1], 0f)
        assertEquals(0f, v[i + 2], 0f)
        assertEquals(4f, v[i + 6], 0f)
        assertEquals(0f, v[i + 7], 0f)
    }

    @Test
    fun flatTerrainHasUpNormals() {
        val v = TerrainData(3, FloatArray(9), 8, 1f).vertices()
        for (n in 0 until 9) {
            assertEquals(0f, v[n * 8 + 3], 1e-6f)
            assertEquals(1f, v[n * 8 + 4], 1e-6f)
            assertEquals(0f, v[n * 8 + 5], 1e-6f)
        }
    }

    @Test
    fun indicesCoverEveryCellWithTwoTriangles() {
        val idx = TerrainData(3, FloatArray(9), 8, 1f).indices()
        assertEquals(4 * 6, idx.size)
        assertArrayEquals(shortArrayOf(4, 1, 0, 0, 3, 4), idx.copyOfRange(0, 6))
        assertTrue(idx.all { it in 0..8 })
    }

    @Test
    fun heightIsInterpolatedAndBoundsChecked() {
        val t = TerrainData(2, floatArrayOf(0f, 2f, 0f, 2f), 10, 1f)
        assertEquals(1f, t.heightAt(5f, 5f)!!, 1e-6f)
        assertEquals(2f, t.heightAt(10f, 0f)!!, 1e-6f)
        assertNull(t.heightAt(-1f, 0f))
        assertNull(t.heightAt(0f, 11f))
    }

    @Test
    fun invalidDataIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { TerrainData.read(terrainFile(1f, 2f, 3f), 10, 1f) }
        assertThrows(IllegalArgumentException::class.java) { TerrainData.read(terrainFile(), 10, 1f) }
        val truncated = File("src/test/testData/project/Untitled/assets/terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b/terrain.data")
            .readBytes().copyOf(1000).let { Files.createTempFile("t", ".data").toFile().apply { writeBytes(it) } }
        assertThrows(IllegalArgumentException::class.java) { TerrainData.read(truncated, 1600, 60f) }
        assertThrows(IllegalArgumentException::class.java) { TerrainData(256, FloatArray(256 * 256), 1, 1f) }
    }
}
