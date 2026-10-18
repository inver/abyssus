/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.document

import com.fasterxml.jackson.annotation.JsonProperty

sealed interface ImportRecordSize
enum class OriginalImportSize : ImportRecordSize {
    @JsonProperty("original")
    ORIGINAL
}

data class ImportHeight(val height: Double) : ImportRecordSize
data class ImportLargestExtent(val largestExtent: Double) : ImportRecordSize
data class ImportSpan(val span: Double) : ImportRecordSize

data class ImportFrameRecord(val unit: String?, val upAxis: String?)
data class ImportItemRecord(val item: String, val reason: String)

/** Declaration order and nullable stated frame fields are part of the existing source.json output. */
data class ModelImportRecord(
    val importer: String = "model",
    val source: String,
    val sourcePath: String,
    val sourceSha256: String,
    val sourceFormat: String,
    val stated: ImportFrameRecord,
    val chosen: ImportFrameRecord,
    val size: ImportRecordSize,
    val animations: List<String>,
    val skipped: List<ImportItemRecord>,
    val approximated: List<ImportItemRecord>,
)

data class FlightGearImportRecord(
    val importer: String = "flightgear",
    val archive: String,
    val archiveSha256: String,
    val aircraft: String,
    val description: String?,
    val authors: String?,
    val model: String?,
    val license: String,
    val licenseFiles: List<String>,
    val size: ImportRecordSize,
    val excludedParts: List<String>,
    val skipped: List<ImportItemRecord>,
    val frame: String,
)
