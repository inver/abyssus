/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.flightgear

import net.nevinsky.abyssus.lib.core.editor.modelimport.MODEL_FILE
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.gltf.GltfWriter
import java.util.UUID

/** The deepest nesting of model XML files an import follows. */
const val MAX_MODEL_DEPTH = 8

/** The model file an import writes into its asset folder. */
const val IMPORTED_MODEL_FILE = MODEL_FILE

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
    private val writer: GltfWriter = GltfWriter(),
) {
    private val collection = FlightGearCollection(ac3d, rest)
    private val staging = FlightGearStaging(collection, FlightGearGeometry(), format, sgi, writer)

    fun inspect(archive: FlightGearArchive, aircraft: FlightGearAircraft, cancel: () -> Unit = {}): FlightGearInspection =
        collection.inspect(archive, aircraft, cancel)

    fun stage(
        archive: FlightGearArchive,
        request: FlightGearImportRequest,
        uuid: UUID,
        lastModified: Long,
        cancel: () -> Unit = {},
    ): StagedImport = staging.stage(archive, request, uuid, lastModified, cancel)
}
