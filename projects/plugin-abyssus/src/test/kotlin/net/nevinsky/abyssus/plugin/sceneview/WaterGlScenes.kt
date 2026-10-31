/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.scene.renderParamsOf
import net.nevinsky.abyssus.lib.core.editor.water.WaterCodec
import net.nevinsky.abyssus.lib.core.water.WaterSettings
import com.badlogic.gdx.math.Vector3
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files

/** Test projects for the water GL checks: a copy of Untitled whose terrain is a shaped height field. */
internal object WaterGlScenes {
    const val TERRAIN = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"
    const val SIZE = 1600
    const val RESOLUTION = 180

    /** A copy of the Untitled project whose terrain has the heights [shape] gives for each world position (x, z). */
    fun project(shape: (Double, Double) -> Float): File {
        val dir = Files.createTempDirectory("water-gl").toFile().also { it.deleteOnExit() }
        File("src/test/testData/project/Untitled").copyRecursively(dir, overwrite = true)
        val data = File(dir, "assets/$TERRAIN/terrain.data")
        DataOutputStream(data.outputStream().buffered()).use { out ->
            for (z in 0 until RESOLUTION) for (x in 0 until RESOLUTION) {
                out.writeFloat(shape(x.toDouble() / (RESOLUTION - 1) * SIZE, z.toDouble() / (RESOLUTION - 1) * SIZE))
            }
        }
        return dir
    }

    /** Low in the middle, rising to [rim] at the edge of a circle of radius 800 around the terrain's centre. */
    fun bowl(depth: Float = 3f, rim: Float = 3f): (Double, Double) -> Float = { x, z ->
        val r = Math.hypot(x - SIZE / 2.0, z - SIZE / 2.0) / (SIZE / 2.0)
        (-depth + (depth + rim) * r * r).toFloat()
    }

    /** The scene of [dir] with [surfaces] added under `abyssus.waterSurfaces`, no fog, drawn from the default camera. */
    fun params(dir: File, vararg surfaces: WaterSettings, patch: (ObjectNode) -> Unit = {}): SceneRenderParams {
        val root = SceneJson().parseObject(File(dir, "scenes/Main Scene.scene").readText())
        val container = root.putObject("abyssus").putObject("waterSurfaces")
        for (s in surfaces) container.set<ObjectNode>(s.id, WaterCodec().node(s))
        root.put("fogEnabled", false)
        patch(root)
        return renderParamsOf(parseScene(SceneJson().compact(root)), CameraParams.DEFAULT, dir)
    }

    const val BLENDED = "model_blended"

    /** Adds a model asset made of one red, 90 % opaque (blended) quad, 2 wide and 2 high, standing on y = 0 and facing +Z. */
    fun addBlendedAsset(dir: File) {
        val buffer = java.nio.ByteBuffer.allocate(108).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (v in listOf(-1f to 0f, 1f to 0f, 1f to 2f, -1f to 2f)) { buffer.putFloat(v.first); buffer.putFloat(v.second); buffer.putFloat(0f) }
        repeat(4) { buffer.putFloat(0f); buffer.putFloat(0f); buffer.putFloat(1f) }
        for (i in listOf(0, 1, 2, 0, 2, 3)) buffer.putShort(i.toShort())
        val uri = "data:application/octet-stream;base64," + java.util.Base64.getEncoder().encodeToString(buffer.array())
        val gltf = """
            {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
             "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"indices":2,"material":0}]}],
             "materials":[{"name":"blended","alphaMode":"BLEND","doubleSided":true,"pbrMetallicRoughness":{"baseColorFactor":[1,0,0,0.9],"metallicFactor":0,"roughnessFactor":1}}],
             "buffers":[{"byteLength":108,"uri":"$uri"}],
             "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":48},{"buffer":0,"byteOffset":48,"byteLength":48},{"buffer":0,"byteOffset":96,"byteLength":12}],
             "accessors":[
               {"bufferView":0,"componentType":5126,"count":4,"type":"VEC3","min":[-1,0,0],"max":[1,2,0]},
               {"bufferView":1,"componentType":5126,"count":4,"type":"VEC3"},
               {"bufferView":2,"componentType":5123,"count":6,"type":"SCALAR"}]}
        """.trimIndent()
        val folder = File(dir, "assets/$BLENDED").also { it.mkdirs() }
        File(folder, "model.gltf").writeText(gltf)
        File(folder, "meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"uuid":"b1e4d000-0000-4000-8000-000000000001","type":"MODEL","additional":{"file":"model.gltf","format":"GLTF","binary":false,"materials":[]}}""",
        )
    }

    /** Places the blended quad of [addBlendedAsset] at ([x], [y], [z]) in the scene tree [root]; returns its entity id. */
    fun placeBlended(root: ObjectNode, x: Double, y: Double, z: Double): String {
        val factory = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
        return net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree(root).insert { _ ->
            factory.objectNode().also { c ->
                c.putObject("NameComponent").put("name", "Blended")
                c.putObject("PositionComponent").putObject("localPosition").put("x", x).put("y", y).put("z", z)
                c.putObject("RenderComponent").put("shaderKey", "pbr").put("type", "MODEL").put("assetName", BLENDED)
                c.putObject("TypeComponent").put("type", "OBJECT")
            }
        }!!
    }

    /** The scene tree of [dir] as JSON, to patch before [paramsOf]. */
    fun rootOf(dir: File): ObjectNode = SceneJson().parseObject(File(dir, "scenes/Main Scene.scene").readText())

    /** [root] with the surfaces under `abyssus.waterSurfaces`, no fog, drawn from the default camera. */
    fun paramsOf(dir: File, root: ObjectNode, vararg surfaces: WaterSettings, keepFog: Boolean = false): SceneRenderParams {
        if (surfaces.isNotEmpty()) {
            val extension = root.get("abyssus") as? ObjectNode ?: root.putObject("abyssus")
            val container = extension.get("waterSurfaces") as? ObjectNode ?: extension.putObject("waterSurfaces")
            for (s in surfaces) container.set<ObjectNode>(s.id, WaterCodec().node(s))
        }
        if (!keepFog) root.put("fogEnabled", false)
        return renderParamsOf(parseScene(SceneJson().compact(root)), CameraParams.DEFAULT, dir)
    }

    /** Moves the terrain entity of [root] by [dy] in y. */
    fun liftTerrain(root: ObjectNode, dy: Double) {
        val tree = net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree(root)
        val position = tree.components("1")!!.get("PositionComponent").get("localPosition") as ObjectNode
        position.put("y", position.get("y").asDouble() + dy)
    }

    private val presets = net.nevinsky.abyssus.lib.core.water.WaterPresets()

    /** A Lake preset 120 wide and long centred on the origin at [level], with [tint] when given. */
    fun lake(level: Double = 0.0, tint: Triple<Double, Double, Double>? = null): WaterSettings =
        presets.defaults(net.nevinsky.abyssus.lib.core.water.WaterPreset.LAKE, "w1", "Lake", 0.0, level, 0.0).copy(width = 120.0, length = 120.0).let {
            if (tint == null) it else it.copy(tintR = tint.first, tintG = tint.second, tintB = tint.third)
        }

    /**
     * The Untitled scene over a flat floor at y = -2 (the terrain entity shifted by [terrainDy]) with [water] (the origin lake
     * by default); [patch] edits the project folder and the scene tree before it is read. Without [withModels] the scene
     * places no model.
     */
    fun flatLake(
        water: List<WaterSettings> = listOf(lake()),
        terrainDy: Double = 0.0,
        withModels: Boolean = true,
        keepFog: Boolean = false,
        patch: (File, ObjectNode) -> Unit = { _, _ -> },
    ): SceneRenderParams {
        val dir = project { _, _ -> -2f }
        val root = rootOf(dir)
        if (terrainDy != 0.0) liftTerrain(root, terrainDy)
        patch(dir, root)
        val params = paramsOf(dir, root, *water.toTypedArray(), keepFog = keepFog)
        return if (withModels) params else params.copy(content = params.content.copy(models = emptyList()))
    }

    /** World position of the bowl's centre: the terrain's middle plus the terrain entity's own offset in the Untitled scene. */
    val BOWL_CENTRE = Vector3(SIZE / 2f - 38.25267f, 0f, SIZE / 2f - 32.7754f)

    /** A lake of [size] around the bowl's centre at [level]. */
    fun bowlLake(level: Double = 0.0, size: Double = 1500.0, configure: (WaterSettings) -> WaterSettings = { it }): WaterSettings =
        configure(
            net.nevinsky.abyssus.lib.core.water.WaterPresets().defaults(
                net.nevinsky.abyssus.lib.core.water.WaterPreset.LAKE, "w1", "Lake", BOWL_CENTRE.x.toDouble(), level, BOWL_CENTRE.z.toDouble(),
            ).copy(width = size, length = size),
        )

    /** The Untitled scene over the bowl terrain with [water], no models; [far] sets the camera's far plane. */
    fun bowlScene(vararg water: WaterSettings, far: Float = 1000f, depth: Float = 3f, rim: Float = 3f, patch: (ObjectNode) -> Unit = {}): SceneRenderParams {
        val dir = project(bowl(depth, rim))
        val params = params(dir, *water, patch = patch)
        return params.copy(
            camera = params.camera.copy(far = far),
            content = params.content.copy(models = emptyList(), cameras = emptyList()),
        )
    }
}
