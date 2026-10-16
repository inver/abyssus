/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.testing.perf

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDataFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneLoader
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File
import java.nio.file.Files

class StressSceneTest {
    private val dirs = mutableListOf<File>()
    private val source = testProject("Untitled")

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private fun generate(seed: Long = StressScene.SEED): Pair<File, StressSceneInfo> {
        val dir = Files.createTempDirectory("stress").toFile().also(dirs::add)
        return dir to StressScene(seed).generate(source, dir)
    }

    @Test
    fun theSceneHashIsStableForTheSeedAndChangesWithIt() {
        assertEquals(generate().second.hash, generate().second.hash)
        assertNotEquals(generate().second.hash, generate(seed = 1L).second.hash)
    }

    @Test
    fun theSceneHoldsThe1000PlacementsATerrainAndAValidFoliageBake() {
        val (dir, info) = generate()
        assertEquals(1000, info.modelPlacements)
        assertEquals(5000, info.foliageCopies)
        val scene = SceneLoader(JsonProcessor(NOPLogger.NOP_LOGGER), FileLoader(dir)).load(info.sceneName)
        val entities = scene.ecs!!
        val models = entities.properties().count { (_, e) -> e.toString().contains("\"type\":\"MODEL\"") }
        assertEquals(1000, models)
        assertTrue(entities.toString().contains("\"type\":\"TERRAIN\""))

        // the bake is not stale: it matches the terrain and the foliage meta
        val files = FileLoader(dir)
        val metas = AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files)
        val prepared = FoliageLoader(files, metas, TerrainLoader(files, metas)).prepare(StressScene.FOLIAGE_NAME)!!.staged
        val bake = FoliageDataFile().read(File(dir, "assets/${StressScene.FOLIAGE_NAME}/foliage.data").readBytes())!!
        assertEquals(5000, bake.layers.single().chunks.sumOf { it.size })
        assertArrayEquals(FoliageFingerprint().of(prepared.meta, prepared.terrain, prepared.masks), bake.fingerprint)
    }

    @Test
    fun theModelTriangleCountIsReadFromTheCopiedModel() {
        GdxNativesLoader.load()
        val (dir, _) = generate()
        val model = File(dir, "assets").listFiles { f -> f.name.startsWith("model_") }!!.single()
        val data = AssimpModelLoader().loadData(FileHandle(File(model, "model.gltf")))
        val triangles = data.meshes.sumOf { mesh -> mesh.parts.sumOf { it.indices.size.toLong() / 3 } }
        assertTrue(triangles > 0)
        val info = StressScene().generate(source, Files.createTempDirectory("stress").toFile().also(dirs::add), triangles)
        assertEquals(triangles * 1000, info.modelTriangles)
    }
}
