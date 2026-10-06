/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.flightgear

import net.nevinsky.abyssus.core.io.JsonProcessor
import net.nevinsky.abyssus.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.core.format.DocumentKind
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The deepest nesting of model XML files an import follows. */
const val MAX_MODEL_DEPTH = 8

/** The model file an import writes into its asset folder. */
const val IMPORTED_MODEL_FILE = "model.glb"

/** How big the imported model is. */
sealed interface ImportSize {
    /** FlightGear's metres. */
    data object Original : ImportSize

    /** Scaled so the model's extent along X (wing tip to wing tip) is [metres]. */
    data class Span(val metres: Double) : ImportSize
}

/** Where the imported model's origin is. */
sealed interface ImportOrigin {
    /** Centred on its span and length, its lowest point at y = 0: the model stands on its origin. */
    data object Ground : ImportOrigin

    /** At a point of the source model, in FlightGear's body frame (metres: x aft, y right, z up), such as its centre of gravity. */
    data class SourcePoint(val x: Double, val y: Double, val z: Double) : ImportOrigin
}

/** Something an import leaves out, and why. */
data class SkippedItem(val item: String, val reason: String)

/** A named part of the model, and whether the aircraft shows it at rest (every property 0). */
data class ImportPart(val name: String, val shownAtRest: Boolean)

/** What an aircraft would import: its parts, what would be skipped, and its licence (null: none found). */
data class FlightGearInspection(
    val aircraft: FlightGearAircraft,
    val parts: List<ImportPart>,
    val skipped: List<SkippedItem>,
    val license: String?,
    val licenseFiles: List<String>,
)

/** An import to stage: the [aircraft], the asset [folder] name, its [size], the parts to leave out and its [origin]. */
data class FlightGearImportRequest(
    val aircraft: FlightGearAircraft,
    val folder: String,
    val size: ImportSize,
    val excludedParts: Set<String> = emptySet(),
    val origin: ImportOrigin = ImportOrigin.Ground,
)

/** The files of an imported asset folder, keyed by their path inside it, and what was skipped. */
class StagedImport(val folder: String, val uuid: UUID, val files: Map<String, ByteArray>, val skipped: List<SkippedItem>)

/**
 * Turns an aircraft of a FlightGear archive into the files of one native MODEL asset (design: "Convert to GLB"): the
 * model XML's AC3D geometry and nested models at their offsets, in the Abyssus plane frame (nose +Z, up +Y, left
 * wing +X), centred on its span and length, standing on y = 0, optionally scaled to a span; textures as PNG (or copied
 * PNG/JPEG) beside it; `meta.json`, `source.json` and the archive's licence files. [cancel] is called between files
 * and throws to stop. No GL, no IntelliJ.
 */
class FlightGearImport(
    private val json: JsonProcessor,
    private val format: AbyssusDocumentFormat,
    private val ac3d: Ac3dReader = Ac3dReader(),
    private val sgi: SgiImage = SgiImage(),
    private val rest: RestState = RestState(),
    private val glb: GlbWriter = GlbWriter(json.mapper),
) {
    fun inspect(archive: FlightGearArchive, aircraft: FlightGearAircraft, cancel: () -> Unit = {}): FlightGearInspection {
        val collected = collect(archive, aircraft, cancel)
        val hidden = collected.selects.filter { !rest.shows(it.condition) }.flatMap { it.parts }.toSet()
        val shown = collected.selects.filter { rest.shows(it.condition) }.flatMap { it.parts }.toSet()
        val parts = collected.placements.flatMap { placement -> placement.objects().mapNotNull { it.name } }.distinct()
            .map { ImportPart(it, it in shown || it !in hidden) }
        return FlightGearInspection(aircraft, parts, collected.skipped + textureProblems(archive, collected),
            license(archive, aircraft), archive.licenseFiles(aircraft.folder))
    }

    fun stage(
        archive: FlightGearArchive,
        request: FlightGearImportRequest,
        uuid: UUID,
        lastModified: Long,
        cancel: () -> Unit = {},
    ): StagedImport {
        val aircraft = request.aircraft
        val collected = collect(archive, aircraft, cancel)
        val skipped = collected.skipped.toMutableList()
        val files = LinkedHashMap<String, ByteArray>()

        // textures: one output file per distinct source file
        val images = ArrayList<String>()
        val imageOf = HashMap<String, Int?>()
        fun image(placement: Placement, texture: String?): Int? {
            if (texture == null) return null
            val source = textureSource(archive, aircraft, placement, texture) ?: return null
            return imageOf.getOrPut(source) {
                cancel()
                val stem = source.substringAfterLast('/').substringBeforeLast('.')
                val extension = source.substringAfterLast('.', "").lowercase()
                val bytes = archive.read(source)!!
                val (name, data) = when (extension) {
                    in SGI_EXTENSIONS -> try {
                        "$stem.png" to sgi.toPng(bytes)
                    } catch (e: SgiImageException) {
                        skipped += SkippedItem(source, "texture not decoded: ${e.message}")
                        return@getOrPut null
                    }
                    "png", "jpg", "jpeg" -> source.substringAfterLast('/') to bytes
                    else -> {
                        skipped += SkippedItem(source, "texture format .$extension is not supported")
                        return@getOrPut null
                    }
                }
                var unique = "textures/$name"
                var n = 2
                while (files.containsKey(unique)) unique = "textures/${name.substringBeforeLast('.')}-${n++}.${name.substringAfterLast('.')}"
                files[unique] = data
                images += unique
                images.size - 1
            }
        }
        skipped += textureProblems(archive, collected)

        // geometry, in the Abyssus frame before scaling
        val materials = ArrayList<GltfMaterial>()
        val nodes = ArrayList<PartGeometry>()
        for (placement in collected.placements) {
            cancel()
            for ((obj, toWorld) in placement.objectsWithTransforms()) {
                val name = obj.name ?: "part${nodes.size + 1}"
                if (name in request.excludedParts) continue
                val lines = obj.surfaces.count { it.type != 0 }
                if (lines > 0) skipped += SkippedItem("$name: $lines line surfaces", "lines are not imported")
                val polygons = obj.surfaces.filter { it.type == 0 && it.refs.size >= 3 }
                if (polygons.isEmpty()) continue
                val imageIndex = image(placement, obj.texture)
                nodes += geometry(name, obj, polygons, toWorld, placement.model.materials, imageIndex, materials)
            }
        }
        if (nodes.isEmpty()) throw FlightGearArchiveException("${aircraft.id} has no polygons to import")

        // place: centre span and length, lowest point at 0, then scale
        var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
        for (node in nodes) for (p in node.primitives) for (i in p.positions.indices step 3) {
            minX = minOf(minX, p.positions[i]); maxX = maxOf(maxX, p.positions[i])
            minY = minOf(minY, p.positions[i + 1])
            minZ = minOf(minZ, p.positions[i + 2]); maxZ = maxOf(maxZ, p.positions[i + 2])
        }
        val scale = when (val size = request.size) {
            ImportSize.Original -> 1f
            is ImportSize.Span -> (size.metres / (maxX - minX).coerceAtLeast(1e-6f)).toFloat()
        }
        val origin = when (val o = request.origin) {
            ImportOrigin.Ground -> floatArrayOf((minX + maxX) / 2, minY, (minZ + maxZ) / 2)
            is ImportOrigin.SourcePoint -> FloatArray(3).also {
                FG_TO_ABYSSUS.transform(floatArrayOf(o.x.toFloat(), o.y.toFloat(), o.z.toFloat()), 0, it, 0)
            }
        }
        for (node in nodes) for (p in node.primitives) for (i in p.positions.indices step 3) {
            for (c in 0..2) p.positions[i + c] = (p.positions[i + c] - origin[c]) * scale
        }
        files[IMPORTED_MODEL_FILE] = glb.write(nodes.map { GltfNode(it.name, it.primitives) }, materials, images,
            "Abyssus FlightGear import")

        val licenseFiles = archive.licenseFiles(aircraft.folder)
        for (path in licenseFiles) files[path.substringAfterLast('/')] = archive.read(path)!!

        val meta = json.mapper.createObjectNode()
        meta.put("format", "abyssus").put("formatVersion", 1).put("version", 1).put("lastModified", lastModified)
            .put("uuid", uuid.toString()).put("type", "MODEL")
        meta.putObject("additional").put("file", IMPORTED_MODEL_FILE).put("format", "GLTF").put("binary", true)
            .putArray("materials")
        format.validate(meta, DocumentKind.ASSET)?.let { throw IllegalStateException("the staged meta.json is not native: $it") }
        files["meta.json"] = json.pretty(meta).toByteArray()

        val source = linkedMapOf<String, Any?>(
            "importer" to "flightgear",
            "archive" to archive.file.name,
            "archiveSha256" to archive.sha256(),
            "aircraft" to aircraft.id,
            "description" to aircraft.description,
            "authors" to aircraft.authors,
            "model" to aircraft.modelPath?.removePrefix(aircraft.folder),
            "license" to (license(archive, aircraft) ?: "unknown"),
            "licenseFiles" to licenseFiles.map { it.substringAfterLast('/') },
            "size" to when (val size = request.size) {
                ImportSize.Original -> "original"
                is ImportSize.Span -> mapOf("span" to size.metres)
            },
            "excludedParts" to request.excludedParts.sorted(),
            "skipped" to skipped.distinct().map { mapOf("item" to it.item, "reason" to it.reason) },
            "frame" to when (val o = request.origin) {
                ImportOrigin.Ground -> "nose +Z, up +Y, left wing +X; centred on span and length; lowest point at y = 0"
                is ImportOrigin.SourcePoint -> "nose +Z, up +Y, left wing +X; origin at FlightGear body point (${o.x}, ${o.y}, ${o.z}) m"
            },
        )
        files["source.json"] = json.pretty(source).toByteArray()
        return StagedImport(request.folder, uuid, files, skipped.distinct())
    }

    /** The licence the set file states, or the archive's licence files; null when neither exists. */
    private fun license(archive: FlightGearArchive, aircraft: FlightGearAircraft): String? =
        aircraft.license ?: archive.licenseFiles(aircraft.folder).takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "see ") { it.substringAfterLast('/') }

    private fun textureProblems(archive: FlightGearArchive, collected: Collected): List<SkippedItem> =
        collected.placements.flatMap { placement ->
            placement.objects().mapNotNull { obj ->
                val texture = obj.texture ?: return@mapNotNull null
                if (textureSource(archive, collected.aircraft, placement, texture) != null) null
                else SkippedItem(texture.substringAfterLast('/'), "texture missing from the archive")
            }
        }.distinct()

    /** The archive path of an AC texture: by file name, in the AC file's folder first, then anywhere in the aircraft. */
    private fun textureSource(archive: FlightGearArchive, aircraft: FlightGearAircraft, placement: Placement, texture: String): String? {
        val name = texture.replace('\\', '/').substringAfterLast('/')
        if (name.isEmpty()) return null
        val folder = placement.acPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        return archive.findByName(folder, name).firstOrNull() ?: archive.findByName(aircraft.folder, name).firstOrNull()
    }

    // --- collecting the model ---------------------------------------------------------------------------------------

    private class Collected(
        val aircraft: FlightGearAircraft,
        val placements: List<Placement>,
        val selects: List<SelectAnimation>,
        val skipped: List<SkippedItem>,
    )

    /** One AC3D file placed by [offsets] (FlightGear frame), outermost first. */
    private class Placement(val acPath: String, val model: Ac3dModel, val offsets: List<FlightGearOffsets>) {
        fun objects(): List<Ac3dObject> = objectsWithTransforms().map { it.first }

        /** Every poly object with the matrix from its own vertices to the Abyssus frame (before centring). */
        fun objectsWithTransforms(): List<Pair<Ac3dObject, Matrix>> {
            var fgToAbyssus = FG_TO_ABYSSUS
            for (o in offsets) fgToAbyssus = fgToAbyssus * offsetsMatrix(o)
            val acToAbyssus = fgToAbyssus * AC_TO_FG
            val out = ArrayList<Pair<Ac3dObject, Matrix>>()
            fun walk(obj: Ac3dObject, parent: Matrix) {
                val world = parent * acMatrix(obj.rotation, obj.location)
                if (obj.type == "poly") out += obj to world
                obj.kids.forEach { walk(it, world) }
            }
            walk(model.world, acToAbyssus)
            return out
        }
    }

    private fun collect(archive: FlightGearArchive, aircraft: FlightGearAircraft, cancel: () -> Unit): Collected {
        val modelPath = aircraft.modelPath ?: throw FlightGearArchiveException("${aircraft.id} names no model")
        val placements = ArrayList<Placement>()
        val selects = ArrayList<SelectAnimation>()
        val skipped = ArrayList<SkippedItem>()
        val reader = FlightGearModelXmlReader(archive)

        fun placeAc(path: String, offsets: List<FlightGearOffsets>) {
            val bytes = archive.read(path)
            if (bytes == null) {
                skipped += SkippedItem(path, "model file missing from the archive")
                return
            }
            placements += Placement(path, ac3d.read(String(bytes, Charsets.ISO_8859_1), path), offsets)
        }

        fun visit(path: String, offsets: List<FlightGearOffsets>, depth: Int) {
            cancel()
            when {
                depth > MAX_MODEL_DEPTH -> skipped += SkippedItem(path, "nested deeper than $MAX_MODEL_DEPTH models")
                path.endsWith(".ac", ignoreCase = true) -> placeAc(path, offsets)
                path.endsWith(".xml", ignoreCase = true) -> {
                    if (archive.read(path) == null) {
                        skipped += SkippedItem(path, "model file missing from the archive")
                        return
                    }
                    val xml = reader.read(path)
                    val here = offsets + xml.offsets
                    if (xml.panels > 0) skipped += SkippedItem("${xml.panels} instrument panel(s) in ${path.substringAfterLast('/')}", "panels are not imported")
                    selects += xml.selects
                    when {
                        xml.acPath == null && xml.acWritten != null -> skipped += SkippedItem(xml.acWritten, "path leaves the archive")
                        xml.acPath != null -> visit(xml.acPath, here, depth + 1)
                    }
                    for (nested in xml.nested) {
                        if (nested.path == null) skipped += SkippedItem(nested.written, "path leaves the archive")
                        else visit(nested.path, here + nested.offsets, depth + 1)
                    }
                }
                else -> skipped += SkippedItem(path, "only AC3D models are imported")
            }
        }
        visit(modelPath, emptyList(), 0)
        return Collected(aircraft, placements, selects, skipped)
    }

    // --- geometry -----------------------------------------------------------------------------------------------------

    private class PartGeometry(val name: String, val primitives: List<GltfPrimitive>)

    private fun geometry(
        name: String,
        obj: Ac3dObject,
        polygons: List<Ac3dSurface>,
        toWorld: Matrix,
        acMaterials: List<Ac3dMaterial>,
        image: Int?,
        materials: MutableList<GltfMaterial>,
    ): PartGeometry {
        val count = obj.vertices.size / 3
        val world = FloatArray(obj.vertices.size)
        for (i in 0 until count) toWorld.transform(obj.vertices, i * 3, world, i * 3)

        // face normals of every polygon, for flat shading and the crease test
        val faceNormals = polygons.map { s -> newell(world, s.refs) }
        val smoothAt = HashMap<Int, MutableList<Int>>() // vertex -> smooth faces using it
        polygons.forEachIndexed { f, s -> if (s.smooth) s.refs.forEach { smoothAt.getOrPut(it) { ArrayList() } += f } }
        val crease = cos(Math.toRadians(obj.crease.toDouble())).toFloat()

        class Builder {
            val positions = ArrayList<Float>(); val normals = ArrayList<Float>(); val uvs = ArrayList<Float>(); val indices = ArrayList<Int>()
        }
        val builders = LinkedHashMap<Int, Builder>()
        polygons.forEachIndexed { f, s ->
            val acMaterial = acMaterials.getOrNull(s.material)
            val material = GltfMaterial(
                acMaterial?.name ?: "material${s.material}",
                floatArrayOf(acMaterial?.r ?: 0.8f, acMaterial?.g ?: 0.8f, acMaterial?.b ?: 0.8f, 1f - (acMaterial?.transparency ?: 0f)),
                image, s.twoSided,
            )
            val index = materials.indexOf(material).takeIf { it >= 0 } ?: (materials.size.also { materials += material })
            val b = builders.getOrPut(index) { Builder() }
            val first = b.positions.size / 3
            val n = faceNormals[f]
            for (k in s.refs.indices) {
                val v = s.refs[k]
                b.positions += world[v * 3]; b.positions += world[v * 3 + 1]; b.positions += world[v * 3 + 2]
                val normal = if (s.smooth) {
                    val sum = FloatArray(3)
                    for (g in smoothAt[v].orEmpty()) {
                        val m = faceNormals[g]
                        if (m[0] * n[0] + m[1] * n[1] + m[2] * n[2] >= crease) { sum[0] += m[0]; sum[1] += m[1]; sum[2] += m[2] }
                    }
                    normalised(sum, n)
                } else n
                b.normals += normal[0]; b.normals += normal[1]; b.normals += normal[2]
                b.uvs += s.uvs[k * 2] * obj.textureRepeat[0] + obj.textureOffset[0]
                b.uvs += 1f - (s.uvs[k * 2 + 1] * obj.textureRepeat[1] + obj.textureOffset[1]) // glTF's V runs down
            }
            for (k in 1 until s.refs.size - 1) { b.indices += first; b.indices += first + k; b.indices += first + k + 1 }
        }
        return PartGeometry(name, builders.map { (material, b) ->
            GltfPrimitive(material, b.positions.toFloatArray(), b.normals.toFloatArray(), b.uvs.toFloatArray(), b.indices.toIntArray())
        })
    }

    private fun newell(positions: FloatArray, refs: IntArray): FloatArray {
        val n = FloatArray(3)
        for (k in refs.indices) {
            val a = refs[k] * 3
            val b = refs[(k + 1) % refs.size] * 3
            n[0] += (positions[a + 1] - positions[b + 1]) * (positions[a + 2] + positions[b + 2])
            n[1] += (positions[a + 2] - positions[b + 2]) * (positions[a] + positions[b])
            n[2] += (positions[a] - positions[b]) * (positions[a + 1] + positions[b + 1])
        }
        return normalised(n, floatArrayOf(0f, 1f, 0f))
    }

    private fun normalised(v: FloatArray, fallback: FloatArray): FloatArray {
        val length = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return if (length < 1e-12f) fallback else floatArrayOf(v[0] / length, v[1] / length, v[2] / length)
    }
}

/** A 3x4 affine matrix (rotation and scale, then translation), row-major. */
internal class Matrix(private val m: DoubleArray) {
    operator fun times(o: Matrix): Matrix {
        val r = DoubleArray(12)
        for (row in 0..2) for (col in 0..3) {
            var sum = if (col == 3) m[row * 4 + 3] else 0.0
            for (k in 0..2) sum += m[row * 4 + k] * o.m[k * 4 + col]
            r[row * 4 + col] = sum
        }
        return Matrix(r)
    }

    fun transform(src: FloatArray, at: Int, dst: FloatArray, to: Int) {
        val x = src[at].toDouble()
        val y = src[at + 1].toDouble()
        val z = src[at + 2].toDouble()
        for (row in 0..2) dst[to + row] = (m[row * 4] * x + m[row * 4 + 1] * y + m[row * 4 + 2] * z + m[row * 4 + 3]).toFloat()
    }
}

/** AC3D files (y up) to FlightGear's body frame (x aft, y right, z up): x, -z, y. */
internal val AC_TO_FG = Matrix(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 1.0, 0.0, 0.0))

/** FlightGear's body frame to the Abyssus plane frame (nose +Z, up +Y, left wing +X): -y, z, -x. */
internal val FG_TO_ABYSSUS = Matrix(doubleArrayOf(0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, -1.0, 0.0, 0.0, 0.0))

/** An AC3D object's placement in its parent: its `rot` (row-major) then its `loc`. */
internal fun acMatrix(rotation: FloatArray, location: FloatArray) = Matrix(doubleArrayOf(
    rotation[0].toDouble(), rotation[1].toDouble(), rotation[2].toDouble(), location[0].toDouble(),
    rotation[3].toDouble(), rotation[4].toDouble(), rotation[5].toDouble(), location[1].toDouble(),
    rotation[6].toDouble(), rotation[7].toDouble(), rotation[8].toDouble(), location[2].toDouble(),
))

/** FlightGear `offsets` in its body frame: translation, then heading about z, pitch about y and roll about x. */
internal fun offsetsMatrix(o: FlightGearOffsets): Matrix {
    fun rad(degrees: Double) = Math.toRadians(degrees)
    val (ch, sh) = cos(rad(o.heading)) to sin(rad(o.heading))
    val (cp, sp) = cos(rad(o.pitch)) to sin(rad(o.pitch))
    val (cr, sr) = cos(rad(o.roll)) to sin(rad(o.roll))
    val heading = Matrix(doubleArrayOf(ch, -sh, 0.0, 0.0, sh, ch, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0))
    val pitch = Matrix(doubleArrayOf(cp, 0.0, sp, 0.0, 0.0, 1.0, 0.0, 0.0, -sp, 0.0, cp, 0.0))
    val roll = Matrix(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, cr, -sr, 0.0, 0.0, sr, cr, 0.0))
    val move = Matrix(doubleArrayOf(1.0, 0.0, 0.0, o.x, 0.0, 1.0, 0.0, o.y, 0.0, 0.0, 1.0, o.z))
    return move * heading * pitch * roll
}
