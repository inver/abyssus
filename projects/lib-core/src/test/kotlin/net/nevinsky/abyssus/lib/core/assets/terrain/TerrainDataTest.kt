/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.terrain

import net.nevinsky.abyssus.lib.core.assets.terrain.TERRAIN_FLOATS_PER_VERTEX
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.After
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files

class TerrainDataTest {
    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    /** A one-terrain project whose `terrain.data` holds [bytes], read through the loader the way the plugin does. */
    private fun read(bytes: ByteArray, size: Int = 10, uv: Float = 1f): TerrainData {
        val dir = Files.createTempDirectory("proj").toFile().also(dirs::add)
        File(dir, "assets/t").mkdirs()
        File(dir, "assets/t/terrain.data").writeBytes(bytes)
        File(dir, "assets/t/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":$size,"uv":$uv}}"""
        )
        val files = FileLoader(dir)
        val metas = testMetaLoader(dir, fileLoader = files)
        return TerrainLoader(files, metas).prepare("t")!!.staged.data
    }

    private fun bigEndian(vararg heights: Float): ByteArray =
        ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out -> heights.forEach(out::writeFloat) } }.toByteArray()

    @Test
    fun readsTheTestProjectsTerrain() {
        val file = File(testProject("Untitled"), "assets/terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b/terrain.data")
        val t = read(file.readBytes(), 1600, 60f)
        assertEquals(180, t.resolution)
        assertEquals(180 * 180, t.heights.size)
        assertTrue(t.heights.all { it.isFinite() })
    }

    @Test
    fun readsBigEndianHeightsAndBuildsVertices() {
        val t = read(bigEndian(0f, 1f, 2f, 3f), 10, 4f)
        assertEquals(2, t.resolution)
        val v = t.vertices()
        assertEquals(4 * TERRAIN_FLOATS_PER_VERTEX, v.size)
        // vertex (x=1, z=0): position, uv
        val i = 1 * TERRAIN_FLOATS_PER_VERTEX
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
        assertArrayEquals(intArrayOf(4, 1, 0, 0, 3, 4), idx.copyOfRange(0, 6))
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
        assertThrows(IllegalArgumentException::class.java) { read(bigEndian(1f, 2f, 3f)) }
        assertThrows(IllegalArgumentException::class.java) { read(bigEndian()) }
        val whole = File(testProject("Untitled"), "assets/terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b/terrain.data").readBytes()
        assertThrows(IllegalArgumentException::class.java) { read(whole.copyOf(1000), 1600, 60f) }
        assertThrows(IllegalArgumentException::class.java) { TerrainData(256, FloatArray(256 * 256), 1, 1f) }
    }

    @Test
    fun readsNineHeightsAsABigEndianThreeByThreeGrid() {
        val heights = FloatArray(9) { it * 1.25f - 3f }
        val t = read(bigEndian(*heights), 100, 1f)
        assertEquals(3, t.resolution)
        assertArrayEquals(heights, t.heights, 0f)
    }

    private fun terrain(heights: FloatArray, resolution: Int = 8) = TerrainData(resolution, heights, 70, 2f)

    private fun rows(t: TerrainData, minX: Int, minZ: Int, maxX: Int, maxZ: Int): Map<Pair<Int, Int>, List<Float>> {
        val w = maxX - minX + 1
        val out = FloatArray(w * TERRAIN_FLOATS_PER_VERTEX)
        val map = HashMap<Pair<Int, Int>, List<Float>>()
        for (z in minZ..maxZ) {
            t.vertexRow(z, minX, maxX, out, 0)
            for (x in 0 until w) map[(minX + x) to z] = out.slice(x * TERRAIN_FLOATS_PER_VERTEX until (x + 1) * TERRAIN_FLOATS_PER_VERTEX)
        }
        return map
    }

    @Test
    fun aRegionEditMatchesAFullRebuildAndOnlyTheOneVertexBorderMoves() {
        val resolution = 8
        val before = terrain(FloatArray(resolution * resolution) { (it % 5) * 0.7f })
        val oldVertices = before.vertices()
        val edited = FloatArray(resolution * resolution) { before.heights[it] }
        for (z in 3..4) for (x in 2..5) edited[z * resolution + x] += 9f
        before.copyHeights(edited, 2, 3, 5, 4)
        val rebuilt = terrain(edited).vertices()
        assertArrayEquals("a region edit is the same as a rebuild from the new heights", rebuilt, before.vertices(), 0f)
        // vertices more than one step away from the edited rectangle are what they were
        val moved = HashSet<Pair<Int, Int>>()
        for (z in 0 until resolution) for (x in 0 until resolution) {
            val from = (z * resolution + x) * TERRAIN_FLOATS_PER_VERTEX
            if (!(0 until TERRAIN_FLOATS_PER_VERTEX).all { oldVertices[from + it] == rebuilt[from + it] }) moved += x to z
        }
        assertTrue(moved.isNotEmpty())
        assertTrue(moved.all { (x, z) -> x in 1..6 && z in 2..5 })
        // and the rows of the expanded rectangle carry every change
        val patch = rows(before, 1, 2, 6, 5)
        for ((xz, v) in patch) {
            val from = (xz.second * resolution + xz.first) * TERRAIN_FLOATS_PER_VERTEX
            assertEquals(rebuilt.slice(from until from + TERRAIN_FLOATS_PER_VERTEX), v)
        }
    }

    @Test
    fun copyHeightsChangesTheArrayInPlace() {
        val t = terrain(FloatArray(64))
        val heights = t.heights
        t.copyHeights(FloatArray(64) { 5f }, 0, 0, 1, 1)
        assertTrue(heights === t.heights)
        assertEquals(5f, heights[9], 0f)
        assertEquals(0f, heights[2], 0f)
    }

    @Test
    fun copyHeightsRefusesAnotherGridOrARectangleOutsideIt() {
        val t = terrain(FloatArray(64))
        assertThrows(IllegalArgumentException::class.java) { t.copyHeights(FloatArray(9), 0, 0, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { t.copyHeights(FloatArray(64), 0, 0, 8, 1) }
    }
}
