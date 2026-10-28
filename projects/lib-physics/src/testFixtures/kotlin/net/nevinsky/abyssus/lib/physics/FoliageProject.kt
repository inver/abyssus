/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

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
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files

/**
 * A temporary native project with a flat 100 m terrain `terr` (heights 0), the models `tree` and `rock`, and helpers to
 * write a foliage asset with a matching bake: the physics tests copy nothing from the committed fixtures. [close]
 * deletes it.
 */
class FoliageProject : AutoCloseable {
    val dir: File = Files.createTempDirectory("physics-foliage").toFile()
    private val files = FileLoader(dir)
    private val metas = AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files)

    init {
        File(dir, "assets/terr").mkdirs()
        DataOutputStream(File(dir, "assets/terr/terrain.data").outputStream()).use { out -> repeat(4) { out.writeFloat(0f) } }
        File(dir, "assets/terr/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":100,"uv":1.0}}"""
        )
        for (model in listOf("tree", "rock")) {
            File(dir, "assets/$model").mkdirs()
            File(dir, "assets/$model/meta.json").writeText(
                """{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{"file":"model.gltf"}}"""
            )
        }
    }

    /** The foliage asset [name] with [layers] (JSON for the `layers` array). */
    fun foliage(layers: String, name: String = "foliage_meadow", terrain: String = "terr") {
        File(dir, "assets/$name").mkdirs()
        File(dir, "assets/$name/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","additional":{"terrain":"$terrain","maskResolution":16,"layers":$layers}}"""
        )
    }

    /** A bake of the current inputs holding [layers]; [stale] writes a fingerprint that matches nothing. */
    fun bake(layers: List<FoliageLayerBake>, name: String = "foliage_meadow", stale: Boolean = false) {
        val prepared = FoliageLoader(files, metas, TerrainLoader(files, metas)).prepare(name)!!.staged
        val fingerprint = if (stale) ByteArray(32) else FoliageFingerprint().of(prepared.meta, prepared.terrain, prepared.masks)
        File(dir, "assets/$name/foliage.data").writeBytes(FoliageDataFile().write(FoliageBake(fingerprint, 100f, layers)))
    }

    fun deleteBake(name: String = "foliage_meadow") {
        File(dir, "assets/$name/foliage.data").delete()
    }

    override fun close() {
        dir.deleteRecursively()
    }

    companion object {
        /** A one-chunk layer bake of [copies] for layer [id]. */
        fun layer(copies: List<FoliageCopy>, id: Int = 1) = FoliageLayerBake(id, 1, 1, 1, listOf(copies))

        /** The collider of layer `1`: a capsule of radius 0.5 and half height 1 on the ground. */
        const val CAPSULE = """{"shape":"CAPSULE","radius":0.5,"halfHeight":1.0}"""

        fun layerJson(collider: String?, id: Int = 1, kind: String = "OBJECT") =
            """{"id":$id,"kind":"$kind","models":[{"asset":"tree","weight":1.0}]${collider?.let { ""","collider":$it""" } ?: ""}}"""

        /**
         * A scene with a terrain entity `0` carrying the foliage [name] (and [terrainExtra], more terrain components)
         * and a 100 m box `Floor` far below it, plus [extra] entities.
         */
        fun scene(name: String = "foliage_meadow", terrainExtra: String = "", extra: String = ""): String =
            """{"format":"abyssus","formatVersion":1,"ecs":{
              "0":{"components":{"NameComponent":{"name":"Ground"},"PositionComponent":{},
                "RenderComponent":{"shaderKey":"terrain","type":"TERRAIN","assetName":"terr"},
                "FoliageComponent":{"assetName":"$name"}$terrainExtra}},
              "1":{"components":{"NameComponent":{"name":"Floor"},"PositionComponent":{"localPosition":{"y":-20}},
                "ColliderComponent":{"shape":"BOX","halfExtents":{"x":200,"y":1,"z":200}}}}$extra}}"""

        /** A 1 m dynamic box named `Box` above the point (x, z) at height [y]. */
        fun box(x: Float, y: Float, z: Float, id: Int = 2) =
            ""","$id":{"components":{"NameComponent":{"name":"Box"},"PositionComponent":{"localPosition":{"x":$x,"y":$y,"z":$z}},
              "RigidBodyComponent":{},"ColliderComponent":{"shape":"BOX","halfExtents":{"x":0.5,"y":0.5,"z":0.5}}}}"""
    }
}
