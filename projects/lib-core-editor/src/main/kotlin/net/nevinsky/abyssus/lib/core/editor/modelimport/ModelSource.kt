/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelimport

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.utils.Array
import com.fasterxml.jackson.databind.ObjectMapper
import net.nevinsky.abyssus.lib.core.assimp.AssimpFlags
import net.nevinsky.abyssus.lib.core.assimp.AssimpImportException
import net.nevinsky.abyssus.lib.core.assimp.AssimpModelDataLoader
import net.nevinsky.abyssus.lib.core.assimp.ColladaAsset
import net.nevinsky.abyssus.lib.core.assimp.LeftOut
import net.nevinsky.abyssus.lib.core.assimp.UpAxis
import net.nevinsky.abyssus.lib.core.gltf.PhongToPbr
import net.nevinsky.abyssus.lib.core.model.ModelData
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import javax.imageio.ImageIO
import javax.xml.stream.XMLStreamException

/** A source cannot be imported; [message] is the reason. */
class ModelImportException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Why something of the source is not in the imported model; [id] is how `source.json` names it. */
enum class LeftOutReason(val id: String) {
    CAMERA("camera"),
    LIGHT("light"),
    POINTS_OR_LINES("points or lines"),
    MORPH_TARGETS("morph targets"),
    GLTF_EXTENSION("glTF extension"),
    MISSING("missing"),
    UNSUPPORTED_TEXTURE("unsupported texture format"),
}

/** Something left out of the imported model: a camera, a mesh, a texture file name, an extension. */
data class LeftOutItem(val item: String, val reason: LeftOutReason)

/** A material term that could not be kept as it is, such as `specular colour dropped`, by material. */
data class ApproximatedItem(val item: String, val reason: String)

/** Where a pre-filled unit or up axis comes from. */
enum class FrameOrigin {
    /** The file states it. */
    FILE,

    /** The format defines it: glTF is metres and Y up; 3DS is Z up (Assimp applies its master scale). */
    FORMAT,
}

/**
 * The unit and up axis the source states, or its format defines; `null` where neither says anything. [upAxis] may be
 * [UpAxis.X] (a DAE `X_UP`), which the import does not offer.
 */
data class SourceFrame(val unit: LengthUnit?, val upAxis: UpAxis?, val origin: FrameOrigin) {
    /** The settings' starting unit: the stated one, or metres. */
    fun defaultUnit(): LengthUnit = unit ?: LengthUnit.M

    /** The settings' starting up axis: the stated one when offered, or Y. */
    fun defaultUpAxis(): UpAxis = upAxis?.takeIf { it != UpAxis.X } ?: UpAxis.Y
}

/** An animation of the source, by name, and its length. */
data class AnimationInfo(val name: String, val seconds: Float)

/**
 * A source model file, read once. [data] is the model as the import writes it before the transform: Assimp's view
 * of the file without any unit or axis applied (`normalize = false`), with materials mapped to metallic-roughness,
 * unused materials dropped, skin weights summing to 1, and missing or unreadable texture slots cleared. It is not
 * changed afterwards; [ImportTransform] wraps it rather than modify it, so a preview may draw it while a new
 * transform is computed.
 *
 * Embedded textures (FBX, GLB, glTF data URIs) are extracted into a temp folder that [close] deletes; nothing is
 * written next to the source.
 */
class ModelSource(
    val file: File,
    loader: AssimpModelDataLoader = AssimpModelDataLoader(normalize = false),
    collada: ColladaAsset = ColladaAsset(),
    mapper: ObjectMapper = ObjectMapper(),
    phongToPbr: PhongToPbr = PhongToPbr(),
    cancel: () -> Unit = {},
) : AutoCloseable {
    val format: SourceFormat = sourceFormatOf(file.name)
        ?: throw ModelImportException("${file.name} is not an OBJ, FBX, 3DS, DAE, glTF or GLB file")
    val sha256: String
    val frame: SourceFrame
    val data: ModelData
    val animations: List<AnimationInfo>
    val leftOut: List<LeftOutItem>
    val approximated: List<ApproximatedItem>

    /** Where embedded textures were extracted; deleted by [close]. */
    val embeddedDir: File = Files.createTempDirectory("abyssus_model_import").toFile()

    init {
        try {
            cancel()
            sha256 = sha256(file)
            val loaded = try {
                loader.loadScene(file.name, FileHandle(file), AssimpFlags.DEFAULT, FileHandle(embeddedDir))
            } catch (e: AssimpImportException) {
                throw ModelImportException(e.message ?: "the file could not be read", e)
            }
            cancel()
            data = loaded.data
            if (data.meshes.isEmpty) throw ModelImportException("${file.name} holds no triangle geometry")

            frame = when (format) {
                SourceFormat.FBX -> SourceFrame(lengthUnitOf(loaded.stated.unitMetres), loaded.stated.upAxis, FrameOrigin.FILE)
                SourceFormat.DAE -> try {
                    collada.read(file).let { SourceFrame(lengthUnitOf(it.unitMetres), it.upAxis, FrameOrigin.FILE) }
                } catch (e: XMLStreamException) {
                    SourceFrame(null, null, FrameOrigin.FILE)
                }
                SourceFormat.GLTF, SourceFormat.GLB -> SourceFrame(LengthUnit.M, UpAxis.Y, FrameOrigin.FORMAT)
                SourceFormat.THREE_DS -> SourceFrame(LengthUnit.M, UpAxis.Z, FrameOrigin.FORMAT)
                SourceFormat.OBJ -> SourceFrame(null, null, FrameOrigin.FILE)
            }

            val left = ArrayList<LeftOutItem>()
            for (item in loaded.leftOut) {
                left += LeftOutItem(item.item, when (item.kind) {
                    LeftOut.Kind.CAMERA -> LeftOutReason.CAMERA
                    LeftOut.Kind.LIGHT -> LeftOutReason.LIGHT
                    LeftOut.Kind.NON_TRIANGLE_MESH -> LeftOutReason.POINTS_OR_LINES
                    LeftOut.Kind.MORPH_TARGETS -> LeftOutReason.MORPH_TARGETS
                })
            }
            if (format == SourceFormat.GLTF || format == SourceFormat.GLB) {
                gltfExtensions(file, format, mapper).forEach { left += LeftOutItem(it, LeftOutReason.GLTF_EXTENSION) }
            }

            val approximated = ArrayList<ApproximatedItem>()
            keepUsedMaterials(data)
            for (i in 0 until data.materials.size) {
                val converted = phongToPbr.convert(data.materials[i])
                converted.approximated.forEach { approximated += ApproximatedItem(data.materials[i].id ?: "material $i", it) }
                data.materials[i] = converted.material
            }
            left += clearUnusableTextures(data)
            normalizeWeights(data)

            animations = data.animations.map { animation ->
                val end = animation.nodeAnimations?.maxOfOrNull { a ->
                    listOfNotNull(
                        a.translation?.maxOfOrNull { it?.keytime ?: 0f },
                        a.rotation?.maxOfOrNull { it?.keytime ?: 0f },
                        a.scaling?.maxOfOrNull { it?.keytime ?: 0f },
                    ).maxOrNull() ?: 0f
                } ?: 0f
                AnimationInfo(animation.id ?: "", end)
            }
            this.leftOut = left.distinct()
            this.approximated = approximated.distinct()
        } catch (e: Throwable) {
            embeddedDir.deleteRecursively()
            throw e
        }
    }

    /** The number of distinct texture files the kept materials use. */
    fun textureCount(): Int =
        data.materials.flatMap { it.textures?.toList().orEmpty() }.mapNotNull { it.fileName }.distinct().size

    override fun close() {
        embeddedDir.deleteRecursively()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** The `extensionsUsed` of a glTF file or of a GLB's JSON chunk; Assimp drops the ones it doesn't know silently. */
    private fun gltfExtensions(file: File, format: SourceFormat, mapper: ObjectMapper): List<String> {
        val json = try {
            if (format == SourceFormat.GLTF) {
                mapper.readTree(file)
            } else {
                val header = ByteArray(20)
                file.inputStream().use { input ->
                    if (input.readNBytes(header, 0, 20) < 20) return emptyList()
                    val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    if (b.getInt(0) != 0x46546C67 || b.getInt(16) != 0x4E4F534A) return emptyList()
                    val length = b.getInt(12)
                    if (length <= 0 || length > file.length()) return emptyList()
                    mapper.readTree(input.readNBytes(length))
                }
            }
        } catch (e: IOException) {
            return emptyList()
        }
        return json?.get("extensionsUsed")?.mapNotNull { it.textValue() }.orEmpty().sorted()
    }

    /** Assimp adds a default material that no mesh may use; only materials of node parts are kept. */
    private fun keepUsedMaterials(data: ModelData) {
        val used = HashSet<String>()
        fun walk(node: ModelNode) {
            node.parts?.forEach { part -> part.materialId?.let(used::add) }
            node.children?.forEach(::walk)
        }
        data.nodes.forEach(::walk)
        val kept = data.materials.filter { it.id in used }
        data.materials.clear()
        kept.forEach(data.materials::add)
    }

    /** Clears texture slots whose file is missing or that ImageIO cannot read, keeping the material's colour. */
    private fun clearUnusableTextures(data: ModelData): List<LeftOutItem> {
        val result = ArrayList<LeftOutItem>()
        val verdicts = HashMap<String, LeftOutReason?>()
        for (material in data.materials) {
            val textures = material.textures ?: continue
            val kept = Array<ModelTexture>()
            for (texture in textures) {
                val name = texture.fileName ?: continue
                val reason = verdicts.getOrPut(name) { textureProblem(File(name)) }
                if (reason == null) kept.add(texture) else result += LeftOutItem(File(name).name, reason)
            }
            material.textures = kept
        }
        return result
    }

    private fun textureProblem(file: File): LeftOutReason? {
        if (!file.isFile) return LeftOutReason.MISSING
        return try {
            ImageIO.createImageInputStream(file).use { stream ->
                if (stream != null && ImageIO.getImageReaders(stream).hasNext()) null else LeftOutReason.UNSUPPORTED_TEXTURE
            }
        } catch (e: IOException) {
            LeftOutReason.UNSUPPORTED_TEXTURE
        }
    }

    /**
     * glTF needs each skinned vertex's weights to sum to 1. A vertex with weights is scaled to 1; one without any is
     * bound to the first bone.
     */
    private fun normalizeWeights(data: ModelData) {
        for (mesh in data.meshes) {
            val stride = mesh.attributes.sumOf { it.numComponents }
            val offsets = ArrayList<Int>()
            var offset = 0
            for (a in mesh.attributes) {
                if (a.usage == VertexAttributes.Usage.BoneWeight) offsets += offset
                offset += a.numComponents
            }
            if (offsets.isEmpty() || stride == 0) continue
            val v = mesh.vertices
            for (base in 0 until v.size / stride * stride step stride) {
                var sum = 0f
                for (o in offsets) sum += v[base + o + 1].coerceAtLeast(0f)
                if (sum > 0f) {
                    for (o in offsets) v[base + o + 1] = v[base + o + 1].coerceAtLeast(0f) / sum
                } else {
                    v[base + offsets[0]] = 0f
                    v[base + offsets[0] + 1] = 1f
                }
            }
        }
    }
}

/** Opens [ModelSource]s; the plugin passes one instead of calling the constructor. */
class ModelSourceOpener(
    private val loader: AssimpModelDataLoader = AssimpModelDataLoader(normalize = false),
    private val collada: ColladaAsset = ColladaAsset(),
    private val mapper: ObjectMapper = ObjectMapper(),
    private val phongToPbr: PhongToPbr = PhongToPbr(),
) {
    /** @throws ModelImportException with the reason when the file cannot be imported */
    fun open(file: File, cancel: () -> Unit = {}): ModelSource = ModelSource(file, loader, collada, mapper, phongToPbr, cancel)
}
