/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.modelimport

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.gdx.gltf.GltfWriteException
import net.nevinsky.abyssus.lib.gdx.gltf.GltfWriter
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import java.io.File
import java.util.UUID

/** The model file an import writes into its asset folder. */
const val MODEL_FILE = "model.glb"

/** The side file that records where an imported asset came from; loaders ignore it. */
const val SOURCE_FILE = "source.json"

/**
 * What an import writes and leaves out: the model's [size] in metres, its [animations], the number of [textures], and
 * what was [leftOut] or [approximated].
 */
class ImportReport(
    val size: Vector3,
    val animations: List<AnimationInfo>,
    val textures: Int,
    val leftOut: List<LeftOutItem>,
    val approximated: List<ApproximatedItem>,
)

/** The files of an imported asset folder, keyed by their path inside it, and its report. */
class StagedModelImport(val folder: String, val uuid: UUID, val files: Map<String, ByteArray>, val report: ImportReport)

/**
 * Turns a [ModelSource] and the chosen [ImportSettings] into the files of one native MODEL asset: `meta.json`,
 * `model.glb` (the transformed model, [GltfWriter]), `textures/<name>.png` ([TextureGather]) and `source.json`. No GL,
 * no IntelliJ; [cancel] is called between steps and throws to stop.
 */
class ModelImport(
    private val json: JsonProcessor,
    private val format: AbyssusDocumentFormat,
    private val transform: ImportTransform = ImportTransform(),
    private val textures: TextureGather = TextureGather(),
    private val writer: GltfWriter = GltfWriter(),
) {
    /**
     * @param projectDir the folder of the project's `.abss` file: `source.json` records the source path relative to
     * it when the source is inside it
     */
    fun stage(
        source: ModelSource,
        settings: ImportSettings,
        projectDir: File,
        uuid: UUID,
        lastModified: Long,
        cancel: () -> Unit = {},
    ): StagedModelImport {
        cancel()
        val transformed = transform.apply(source.data, settings)
        cancel()
        val gathered = textures.gather(transformed.data, cancel)
        cancel()
        val glb = try {
            writer.write(transformed.data, gathered.uris, GENERATOR)
        } catch (e: GltfWriteException) {
            throw ModelImportException(e.message ?: "the model cannot be written as glTF", e)
        }
        val leftOut = (source.leftOut + gathered.leftOut).distinct()

        val files = LinkedHashMap<String, ByteArray>()
        val meta = json.mapper.createObjectNode()
        meta.put("format", "abyssus").put("formatVersion", 1).put("version", 1).put("lastModified", lastModified)
            .put("uuid", uuid.toString()).put("type", "MODEL")
        meta.putObject("additional").put("file", MODEL_FILE).put("format", "GLTF").put("binary", true)
            .putArray("materials")
        format.validate(meta, DocumentKind.ASSET)?.let { throw IllegalStateException("the staged meta.json is not native: $it") }
        files["meta.json"] = json.pretty(meta).toByteArray()
        files[MODEL_FILE] = glb
        files.putAll(gathered.files)

        val record = linkedMapOf<String, Any?>(
            "importer" to "model",
            "source" to source.file.name,
            "sourcePath" to sourcePath(source.file, projectDir),
            "sourceSha256" to source.sha256,
            "sourceFormat" to source.format.id,
            "stated" to linkedMapOf("unit" to source.frame.unit?.id, "upAxis" to source.frame.upAxis?.name),
            "chosen" to linkedMapOf("unit" to settings.unit.id, "upAxis" to settings.upAxis.name),
            "size" to when (val fit = settings.fit) {
                FitSize.Original -> "original"
                is FitSize.LargestExtent -> mapOf("largestExtent" to fit.metres)
                is FitSize.Height -> mapOf("height" to fit.metres)
            },
            "animations" to source.animations.map { it.name },
            "skipped" to leftOut.map { linkedMapOf("item" to it.item, "reason" to it.reason.id) },
            "approximated" to source.approximated.map { linkedMapOf("item" to it.item, "reason" to it.reason) },
        )
        files[SOURCE_FILE] = json.pretty(record).toByteArray()

        val report = ImportReport(transformed.size, source.animations, gathered.files.size, leftOut, source.approximated)
        return StagedModelImport(settings.folderName, uuid, files, report)
    }

    /** Relative to [projectDir] with `/` separators when [file] is inside it; its absolute path otherwise. */
    fun sourcePath(file: File, projectDir: File): String {
        val source = file.canonicalFile
        val root = projectDir.canonicalFile
        val relative = source.toPath().startsWith(root.toPath()) && source != root
        return if (relative) root.toPath().relativize(source.toPath()).joinToString("/") else source.path
    }
}

private const val GENERATOR = "Abyssus model import"
