/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.flightgear

import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.editor.document.FlightGearImportRecord
import net.nevinsky.abyssus.lib.core.editor.document.OriginalImportSize
import net.nevinsky.abyssus.lib.core.editor.document.ImportSpan
import net.nevinsky.abyssus.lib.core.editor.document.ImportItemRecord
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaDocument
import net.nevinsky.abyssus.lib.core.editor.document.ModelAssetAdditional
import net.nevinsky.abyssus.lib.core.editor.document.validateAssetDocument
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.gdx.gltf.GltfWriter
import java.util.UUID

/** Stages all asset bytes in memory; failed or cancelled imports expose no partial result. */
internal class FlightGearStaging(
    private val collection: FlightGearCollection,
    private val geometry: FlightGearGeometry,
    private val format: AbyssusDocumentFormat,
    private val sgi: SgiImage,
    private val writer: GltfWriter,
) {
    fun stage(
        archive: FlightGearArchive,
        request: FlightGearImportRequest,
        uuid: UUID,
        lastModified: Long,
        cancel: () -> Unit = {},
    ): StagedImport {
        val aircraft = request.aircraft
        val collected = collection.collect(archive, aircraft, cancel)
        val skipped = collected.skipped.toMutableList()
        val files = LinkedHashMap<String, ByteArray>()

        // textures: one output file per distinct source file
        val images = ArrayList<String>()
        val imageOf = HashMap<String, Int?>()
        fun image(placement: Placement, texture: String?): Int? {
            if (texture == null) return null
            val source = collection.textureSource(archive, aircraft, placement, texture) ?: return null
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
        skipped += collection.textureProblems(archive, collected)

        val model = geometry.build(collected, request, images, skipped, cancel, ::image)
        files[IMPORTED_MODEL_FILE] = writer.write(model, images.associateWith { it },
            "Abyssus FlightGear import")

        val licenseFiles = archive.licenseFiles(aircraft.folder)
        for (path in licenseFiles) files[path.substringAfterLast('/')] = archive.read(path)!!

        val meta = AssetMetaDocument(
            lastModified = lastModified, uuid = uuid.toString(), type = MetaType.MODEL,
            additional = ModelAssetAdditional(file = IMPORTED_MODEL_FILE),
        )
        validateAssetDocument(meta, format)?.let { throw IllegalStateException("the staged meta.json is not native: $it") }
        files[META_FILE] = SceneJson().pretty(meta).toByteArray()

        val source = FlightGearImportRecord(
            archive = archive.file.name,
            archiveSha256 = archive.sha256(),
            aircraft = aircraft.id,
            description = aircraft.description,
            authors = aircraft.authors,
            model = aircraft.modelPath?.removePrefix(aircraft.folder),
            license = (collection.license(archive, aircraft) ?: "unknown"),
            licenseFiles = licenseFiles.map { it.substringAfterLast('/') },
            size = when (val size = request.size) {
                ImportSize.Original -> OriginalImportSize.ORIGINAL
                is ImportSize.Span -> ImportSpan(size.metres)
            },
            excludedParts = request.excludedParts.sorted(),
            skipped = skipped.distinct().map { ImportItemRecord(it.item, it.reason) },
            frame = when (val o = request.origin) {
                ImportOrigin.Ground -> "nose +Z, up +Y, left wing +X; centred on span and length; lowest point at y = 0"
                is ImportOrigin.SourcePoint -> "nose +Z, up +Y, left wing +X; origin at FlightGear body point (${o.x}, ${o.y}, ${o.z}) m"
            },
        )
        files["source.json"] = SceneJson().pretty(source).toByteArray()
        return StagedImport(request.folder, uuid, files, skipped.distinct())
    }
}
