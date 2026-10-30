/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.testing.perf

import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDataFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.slf4j.helpers.NOPLogger
import java.io.File
import java.security.MessageDigest
import java.util.Random

/** What [StressScene.generate] made, for a test or a report to assert on. */
data class StressSceneInfo(
    val sceneName: String,
    /** SHA-256 over the scene text and the foliage bake, hex. Stable for a seed and the source project. */
    val hash: String,
    val modelPlacements: Int,
    val foliageCopies: Int,
    val trianglesPerModel: Long,
    val seed: Long,
) {
    /** Triangles of the repeated models alone. */
    val modelTriangles get() = trianglesPerModel * modelPlacements
}

/**
 * Builds the deterministic stress project of the graphics evidence in a disposable folder: one terrain, [MODELS]
 * placements of one model and a foliage asset, all from a fixed [seed]. The source is a project with the `Untitled`
 * layout (its first model and its terrain are copied); nothing is written to it.
 */
class StressScene(private val seed: Long = SEED) {
    /** [triangles] is the number of triangles in one model, which the caller reads with the model loader. */
    fun generate(source: File, target: File, triangles: Long = 0L): StressSceneInfo {
        val model = File(source, "assets").listFiles { f -> f.isDirectory && f.name.startsWith("model_") && File(f, "model.gltf").isFile }!!
            .minBy { it.name }
        val terrain = File(source, "assets").listFiles { f -> f.isDirectory && f.name.startsWith("terrain_") }!!.minBy { it.name }
        target.mkdirs()
        File(source, "Untitled.abss").copyTo(File(target, "Untitled.abss"), overwrite = true)
        model.copyRecursively(File(target, "assets/${model.name}"), overwrite = true)
        terrain.copyRecursively(File(target, "assets/${terrain.name}"), overwrite = true)
        File(target, "scenes").mkdirs()

        val size = terrainSize(File(target, "assets/${terrain.name}/meta.json"))
        val random = Random(seed)
        val foliage = writeFoliage(target, terrain.name, model.name, size, random)
        val scene = sceneText(terrain.name, model.name, size, random)
        File(target, "scenes/$SCENE_NAME.scene").writeText(scene)

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(scene.toByteArray())
        digest.update(File(target, "assets/$FOLIAGE_NAME/foliage.data").readBytes())
        return StressSceneInfo(
            "$SCENE_NAME.scene", digest.digest().joinToString("") { "%02x".format(it) }, MODELS, foliage, triangles, seed,
        )
    }

    private fun terrainSize(meta: File): Float =
        Regex(""""size"\s*:\s*([0-9.]+)""").find(meta.readText())!!.groupValues[1].toFloat()

    private fun sceneText(terrain: String, model: String, size: Float, random: Random): String {
        val entities = StringBuilder()
        entities.append(
            """"1":{"archetype":1,"components":{"NameComponent":{"name":"Terrain"},"PositionComponent":{"localPosition":{"x":0.0,"y":0.0,"z":0.0}},""" +
                """"RenderComponent":{"renderable":{"kind":"asset","shaderKey":"terrain","asset":{"type":"TERRAIN","assetName":"$terrain"}}},""" +
                """"FoliageComponent":{"assetName":"$FOLIAGE_NAME"},"TypeComponent":{"type":"TERRAIN"}}}"""
        )
        entities.append(
            ""","2":{"archetype":4,"components":{"NameComponent":{"name":"Sun"},"TypeComponent":{"type":"LIGHT_DIRECTIONAL"},""" +
                """"PositionComponent":{"localRotation":{"x":0.38268328,"w":-0.9238796},"localPosition":{"x":0.0,"y":50.0,"z":0.0}}}}"""
        )
        // a square grid with a seeded jitter, so the placements spread over the terrain without overlapping much
        val side = Math.ceil(Math.sqrt(MODELS.toDouble())).toInt()
        val step = size / side
        for (i in 0 until MODELS) {
            val x = ((i % side) + random.nextFloat()) * step - size / 2
            val z = ((i / side) + random.nextFloat()) * step - size / 2
            entities.append(
                ""","${i + 3}":{"archetype":1,"components":{"NameComponent":{"name":"Model ${i + 3}"},""" +
                    """"PositionComponent":{"localPosition":{"x":${"%.3f".format(java.util.Locale.ROOT, x)},"y":0.0,"z":${"%.3f".format(java.util.Locale.ROOT, z)}}},""" +
                    """"RenderComponent":{"renderable":{"kind":"asset","shaderKey":"defaultShader","asset":{"type":"MODEL","assetName":"$model"}}},""" +
                    """"TypeComponent":{"type":"OBJECT"}}}"""
            )
        }
        return """{"format":"abyssus","formatVersion":1,"id":0,"name":"$SCENE_NAME","ambientLightEnabled":true,""" +
            """"ambientLight":{"color":{"r":1.0,"g":1.0,"b":1.0,"a":1.0},"intensity":0.3},"fogEnabled":false,"skyboxEnabled":false,"ecs":{$entities}}"""
    }

    /** Writes the foliage asset and its bake; returns the number of copies. */
    private fun writeFoliage(target: File, terrain: String, model: String, size: Float, random: Random): Int {
        val dir = File(target, "assets/$FOLIAGE_NAME").also { it.mkdirs() }
        File(dir, "meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","additional":{"terrain":"$terrain","maskResolution":16,""" +
                """"layers":[{"id":1,"kind":"OBJECT","models":[{"asset":"$model","weight":1.0}],"alignToNormal":0.0}]}}"""
        )
        val files = FileLoader(target)
        val metas = AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files)
        val prepared = FoliageLoader(files, metas, TerrainLoader(files, metas)).prepare(FOLIAGE_NAME)!!.staged
        val fingerprint = FoliageFingerprint().of(prepared.meta, prepared.terrain, prepared.masks)
        val chunk = size / CHUNKS
        val chunks = List(CHUNKS * CHUNKS) { ArrayList<FoliageCopy>() }
        repeat(FOLIAGE_COPIES) {
            val x = random.nextFloat() * size
            val z = random.nextFloat() * size
            val cx = (x / chunk).toInt().coerceIn(0, CHUNKS - 1)
            val cz = (z / chunk).toInt().coerceIn(0, CHUNKS - 1)
            chunks[cz * CHUNKS + cx] += FoliageCopy(0, x, z, random.nextFloat() * 6.2831855f, 0.5f + random.nextFloat())
        }
        File(dir, "foliage.data").writeBytes(
            FoliageDataFile().write(FoliageBake(fingerprint, chunk, listOf(FoliageLayerBake(1, 1, CHUNKS, CHUNKS, chunks))))
        )
        return FOLIAGE_COPIES
    }

    companion object {
        const val SEED = 20261016L
        const val MODELS = 1000
        const val FOLIAGE_COPIES = 5000
        const val SCENE_NAME = "Stress Scene"
        const val FOLIAGE_NAME = "foliage_stress"
        private const val CHUNKS = 8
    }
}
