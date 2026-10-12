/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDataFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.RuntimeSceneLoader
import net.nevinsky.abyssus.lib.core.scene.SceneContext
import net.nevinsky.abyssus.lib.core.scene.SceneLoader
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.slf4j.helpers.NOPLogger
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files

/**
 * A temporary native project for foliage rendering tests: a flat 200 m terrain `terr`, two small models copied from
 * the bundled field (`tree` and `grass` stand in for vegetation), a foliage asset with a matching bake, and a scene
 * text builder. Nothing is copied from the committed project but those two model folders.
 */
private const val CHUNK = 50f

class FieldFoliageProject : AutoCloseable {
    val dir: File = Files.createTempDirectory("field-foliage").toFile()
    private val files = FileLoader(dir)
    private val metas = AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files)

    init {
        File(dir, "assets/terr").mkdirs()
        DataOutputStream(File(dir, "assets/terr/terrain.data").outputStream()).use { out -> repeat(4) { out.writeFloat(0f) } }
        File(dir, "assets/terr/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":200,"uv":10.0}}"""
        )
        val assets = bundledProject().resolve("assets").toFile()
        bundledProject().resolve("assets/model_airfield_tree_young").toFile().copyRecursively(File(dir, "assets/tree"))
        File(assets, "model_airfield_bush_low").copyRecursively(File(dir, "assets/grass"))
    }

    /** Layers: an OBJECT layer of `tree` (id 1) and, if [grass], a DETAIL layer of `grass` (id 2). */
    fun foliage(treeCopies: List<FoliageCopy>, grass: List<FoliageCopy> = emptyList(), name: String = "foliage_meadow") {
        File(dir, "assets/$name").mkdirs()
        val layers = buildList {
            add("""{"id":1,"kind":"OBJECT","models":[{"asset":"tree","weight":1.0}],"alignToNormal":0.0}""")
            if (grass.isNotEmpty()) add("""{"id":2,"kind":"DETAIL","models":[{"asset":"grass","weight":1.0}],"alignToNormal":0.0,"drawDistance":400.0}""")
        }
        File(dir, "assets/$name/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","additional":{"terrain":"terr","maskResolution":16,"layers":[${layers.joinToString(",")}]}}"""
        )
        val prepared = FoliageLoader(files, metas, TerrainLoader(files, metas)).prepare(name)!!.staged
        val fingerprint = FoliageFingerprint().of(prepared.meta, prepared.terrain, prepared.masks)
        val bakes = buildList {
            add(grid(1, treeCopies))
            if (grass.isNotEmpty()) add(grid(2, grass))
        }
        File(dir, "assets/$name/foliage.data").writeBytes(FoliageDataFile().write(FoliageBake(fingerprint, CHUNK, bakes)))
    }

    /** [copies] cut into the 4 by 4 grid of 50 m chunks, z-major, so a chunk can be culled on its own. */
    private fun grid(id: Int, copies: List<FoliageCopy>): FoliageLayerBake {
        val chunks = List(16) { ArrayList<FoliageCopy>() }
        for (copy in copies) {
            val x = (copy.x / CHUNK).toInt().coerceIn(0, 3)
            val z = (copy.z / CHUNK).toInt().coerceIn(0, 3)
            chunks[z * 4 + x] += copy
        }
        return FoliageLayerBake(id, 1, 4, 4, chunks)
    }

    /** A field scene text: terrain entities at the given x offsets, each carrying [foliage] (when not null), and a sun. */
    fun scene(xs: List<Float> = listOf(0f), foliage: String? = "foliage_meadow", extra: String = ""): String {
        val terrains = xs.mapIndexed { i, x ->
            """"$i":{"components":{"NameComponent":{"name":"Terrain $i"},"PositionComponent":{"localPosition":{"x":$x}},
              "RenderComponent":{"shaderKey":"terrain","type":"TERRAIN","assetName":"terr"}
              ${foliage?.let { ""","FoliageComponent":{"assetName":"$it"}""" } ?: ""}}}"""
        }
        return """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{${terrains.joinToString(",")}$extra}}}"""
    }

    fun load(text: String, messages: MutableList<String> = mutableListOf()): SceneContext {
        val log = warningsTo(messages)
        val json = JsonProcessor(log)
        val loader = RuntimeSceneLoader(SceneLoader(json, files), EcsLoader(json, ComponentRegistry()), log)
        return requireNotNull(loader.loadFromText(text)) { messages.joinToString("\n") }
    }

    override fun close() {
        dir.deleteRecursively()
    }
}
