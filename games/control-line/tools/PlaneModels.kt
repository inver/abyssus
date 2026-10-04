/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("PlaneModels")

package net.nevinsky.abyssus.games.controlline.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Writes the bundled project's models as asset folders under `<project>/assets`: three low-poly control-line planes
 * (`model_racer`, `model_stunter`, `model_trainer`) and the pilot (`model_pilot`), each a `model.gltf` with its
 * `model.bin` and a native `meta.json`. The output depends only on this file, so running it again gives the same
 * bytes. Run by `./gradlew :games:control-line:generatePlaneModels`.
 *
 * A plane's frame: the nose is +Z, up is +Y, the inboard (left) wing is +X; it stands on its wheels and tail skid with
 * the origin at its centre of gravity.
 */
fun main(args: Array<String>) {
    require(args.size == 1) { "usage: PlaneModels <project folder>" }
    val assets = Path.of(args[0]).resolve("assets")
    for (plane in PLANES) writeModel(assets.resolve("model_${plane.name}"), plane.name, plane.boxes())
    writeModel(assets.resolve("model_pilot"), "pilot", pilotBoxes())
}

/** A colour of the models, as the material's base colour. */
data class Paint(val name: String, val r: Float, val g: Float, val b: Float)

/** An axis-aligned box from ([x0], [y0], [z0]) to ([x1], [y1], [z1]) in [paint]. */
data class Box(val x0: Float, val y0: Float, val z0: Float, val x1: Float, val y1: Float, val z1: Float, val paint: Paint)

/** A plane's proportions (metres) and colours. */
data class PlaneShape(
    val name: String, val span: Float, val chord: Float, val length: Float, val body: Paint, val wing: Paint,
) {
    fun boxes(): List<Box> {
        val half = span / 2
        val nose = length * 0.38f
        val tail = -length * 0.62f
        val w = length * 0.09f
        val dark = Paint("dark", 0.12f, 0.12f, 0.12f)
        val tailSpan = span * 0.36f
        val tailChord = chord * 0.6f
        return listOf(
            // fuselage, engine and propeller
            Box(-w / 2, -w * 0.5f, tail, w / 2, w * 0.7f, nose, body),
            Box(-w * 0.4f, -w * 0.4f, nose, w * 0.4f, w * 0.5f, nose + length * 0.07f, dark),
            Box(-span * 0.13f, -0.012f, nose + length * 0.07f, span * 0.13f, 0.012f, nose + length * 0.08f, dark),
            // wing, with the leadout guide at the inboard tip
            Box(-half, 0f, -chord / 2, half, chord * 0.08f, chord / 2, wing),
            Box(half - 0.02f, -0.01f, -0.05f, half, chord * 0.08f + 0.01f, 0.03f, dark),
            // stabiliser and fin
            Box(-tailSpan / 2, 0f, tail, tailSpan / 2, 0.012f, tail + tailChord, wing),
            Box(-0.006f, 0f, tail, 0.006f, chord * 0.75f, tail + tailChord, body),
            // landing gear: two legs with wheels ahead of the centre of gravity, and a tail skid
            Box(-span * 0.12f, -length * 0.2f, length * 0.1f, -span * 0.12f + 0.01f, -w * 0.4f, length * 0.12f, dark),
            Box(span * 0.12f - 0.01f, -length * 0.2f, length * 0.1f, span * 0.12f, -w * 0.4f, length * 0.12f, dark),
            Box(-span * 0.14f, -length * 0.24f, length * 0.07f, -span * 0.1f, -length * 0.16f, length * 0.15f, dark),
            Box(span * 0.1f, -length * 0.24f, length * 0.07f, span * 0.14f, -length * 0.16f, length * 0.15f, dark),
            Box(-0.005f, -w * 0.9f, tail, 0.005f, -w * 0.4f, tail + 0.03f, dark),
        )
    }
}

val PLANES = listOf(
    PlaneShape("racer", span = 0.7f, chord = 0.13f, length = 0.6f, body = Paint("red", 0.8f, 0.1f, 0.08f), wing = Paint("white", 0.92f, 0.92f, 0.9f)),
    PlaneShape("stunter", span = 1.3f, chord = 0.25f, length = 1.0f, body = Paint("blue", 0.1f, 0.25f, 0.75f), wing = Paint("yellow", 0.95f, 0.8f, 0.1f)),
    PlaneShape("trainer", span = 1.0f, chord = 0.2f, length = 0.8f, body = Paint("orange", 0.95f, 0.5f, 0.1f), wing = Paint("cream", 0.95f, 0.92f, 0.8f)),
)

/** A standing figure about 1.8 m tall, facing +Z, its right arm forward holding the handle at 1.5 m. */
fun pilotBoxes(): List<Box> {
    val trousers = Paint("trousers", 0.2f, 0.22f, 0.3f)
    val shirt = Paint("shirt", 0.2f, 0.55f, 0.3f)
    val skin = Paint("skin", 0.85f, 0.65f, 0.5f)
    val handle = Paint("handle", 0.1f, 0.1f, 0.1f)
    return listOf(
        Box(-0.18f, 0f, -0.1f, -0.02f, 0.85f, 0.1f, trousers),
        Box(0.02f, 0f, -0.1f, 0.18f, 0.85f, 0.1f, trousers),
        Box(-0.2f, 0.85f, -0.12f, 0.2f, 1.5f, 0.12f, shirt),
        Box(-0.11f, 1.52f, -0.11f, 0.11f, 1.78f, 0.11f, skin),
        Box(0.2f, 0.9f, -0.06f, 0.3f, 1.48f, 0.06f, shirt),
        Box(-0.3f, 1.4f, -0.05f, -0.2f, 1.5f, 0.55f, shirt),
        Box(-0.26f, 1.44f, 0.55f, -0.24f, 1.56f, 0.6f, handle),
    )
}

/** The six faces of each box, flat-shaded, one glTF primitive per paint; writes the folder's three files. */
fun writeModel(dir: Path, name: String, boxes: List<Box>) {
    Files.createDirectories(dir)
    val paints = boxes.map { it.paint }.distinct()
    val bin = ByteArrayBuilder()
    val json = ObjectMapper()
    val root = json.createObjectNode()
    root.putObject("asset").put("version", "2.0").put("generator", "Abyssus Control Line PlaneModels")
    root.put("scene", 0)
    root.putArray("scenes").addObject().put("name", name).putArray("nodes").add(0)
    root.putArray("nodes").addObject().put("name", name).put("mesh", 0)
    val accessors = root.putArray("accessors")
    val views = root.putArray("bufferViews")
    val primitives = root.putArray("meshes").addObject().put("name", name).putArray("primitives")
    val materials = root.putArray("materials")
    for ((materialIndex, paint) in paints.withIndex()) {
        val faces = boxes.filter { it.paint == paint }.flatMap(::faces)
        val positions = faces.flatMap { it.corners }
        val normals = faces.flatMap { f -> List(4) { f.normal } }
        val indices = faces.indices.flatMap { f -> listOf(0, 1, 2, 0, 2, 3).map { f * 4 + it } }
        val position = accessor(accessors, views, bin, positions, "VEC3", min = bounds(positions, true), max = bounds(positions, false))
        val normal = accessor(accessors, views, bin, normals, "VEC3")
        val index = indexAccessor(accessors, views, bin, indices)
        primitives.addObject().apply {
            putObject("attributes").put("POSITION", position).put("NORMAL", normal)
            put("indices", index)
            put("material", materialIndex)
        }
        materials.addObject().apply {
            put("name", paint.name)
            putObject("pbrMetallicRoughness").apply {
                putArray("baseColorFactor").add(paint.r).add(paint.g).add(paint.b).add(1f)
                put("metallicFactor", 0f)
                put("roughnessFactor", 0.8f)
            }
        }
    }
    root.putArray("buffers").addObject().put("uri", "model.bin").put("byteLength", bin.size)
    Files.write(dir.resolve("model.bin"), bin.toByteArray())
    Files.writeString(dir.resolve("model.gltf"), json.writerWithDefaultPrettyPrinter().writeValueAsString(root).replace("\r\n", "\n") + "\n")
    val uuid = UUID.nameUUIDFromBytes("abyssus-control-line/$name".toByteArray())
    Files.writeString(dir.resolve("meta.json"),
        """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":$GENERATED_AT,"uuid":"$uuid","type":"MODEL",""" +
            """"additional":{"file":"model.gltf","format":"GLTF","binary":true,"materials":[]}}""")
}

/** The fixed `lastModified` of generated assets, so the output never changes between runs. */
const val GENERATED_AT = 1767225600000L

private class Face(val corners: List<FloatArray>, val normal: FloatArray)

/** Counter-clockwise from outside. */
private fun faces(b: Box): List<Face> {
    fun p(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
    val x0 = b.x0
    val y0 = b.y0
    val z0 = b.z0
    val x1 = b.x1
    val y1 = b.y1
    val z1 = b.z1
    return listOf(
        Face(listOf(p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1), p(x1, y0, z1)), p(1f, 0f, 0f)),
        Face(listOf(p(x0, y0, z1), p(x0, y1, z1), p(x0, y1, z0), p(x0, y0, z0)), p(-1f, 0f, 0f)),
        Face(listOf(p(x0, y1, z0), p(x0, y1, z1), p(x1, y1, z1), p(x1, y1, z0)), p(0f, 1f, 0f)),
        Face(listOf(p(x0, y0, z1), p(x0, y0, z0), p(x1, y0, z0), p(x1, y0, z1)), p(0f, -1f, 0f)),
        Face(listOf(p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1)), p(0f, 0f, 1f)),
        Face(listOf(p(x1, y0, z0), p(x0, y0, z0), p(x0, y1, z0), p(x1, y1, z0)), p(0f, 0f, -1f)),
    )
}

private fun bounds(points: List<FloatArray>, min: Boolean): FloatArray = FloatArray(3) { axis ->
    if (min) points.minOf { it[axis] } else points.maxOf { it[axis] }
}

private fun accessor(
    accessors: ArrayNode, views: ArrayNode,
    bin: ByteArrayBuilder, values: List<FloatArray>, type: String, min: FloatArray? = null, max: FloatArray? = null,
): Int {
    val offset = bin.size
    for (v in values) for (f in v) bin.float(f)
    val view = views.size()
    views.addObject().put("buffer", 0).put("byteOffset", offset).put("byteLength", bin.size - offset).put("target", 34962)
    accessors.addObject().apply {
        put("bufferView", view)
        put("componentType", 5126)
        put("count", values.size)
        put("type", type)
        min?.let { m -> putArray("min").apply { m.forEach { add(it) } } }
        max?.let { m -> putArray("max").apply { m.forEach { add(it) } } }
    }
    return accessors.size() - 1
}

private fun indexAccessor(
    accessors: ArrayNode, views: ArrayNode,
    bin: ByteArrayBuilder, indices: List<Int>,
): Int {
    val offset = bin.size
    for (i in indices) bin.int(i)
    val view = views.size()
    views.addObject().put("buffer", 0).put("byteOffset", offset).put("byteLength", bin.size - offset).put("target", 34963)
    accessors.addObject().put("bufferView", view).put("componentType", 5125).put("count", indices.size).put("type", "SCALAR")
    return accessors.size() - 1
}

/** Little-endian bytes, as glTF buffers are. */
private class ByteArrayBuilder {
    private val buffer = ByteBuffer.allocate(1 shl 20).order(ByteOrder.LITTLE_ENDIAN)
    val size get() = buffer.position()
    fun float(f: Float) { buffer.putFloat(f) }
    fun int(i: Int) { buffer.putInt(i) }
    fun toByteArray(): ByteArray = buffer.array().copyOf(size)
}
