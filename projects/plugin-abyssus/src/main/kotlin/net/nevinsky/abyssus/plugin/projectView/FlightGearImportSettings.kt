/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.assetfiles.AssetReferenceGuard
import net.nevinsky.abyssus.plugin.assetfiles.PlacedEntity
import net.nevinsky.abyssus.plugin.assetfiles.AssetTransaction
import net.nevinsky.abyssus.plugin.assetfiles.FileChange
import net.nevinsky.abyssus.plugin.assetfiles.FileSnapshot
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.editor.flightgear.FlightGearImportRequest
import net.nevinsky.abyssus.lib.core.editor.flightgear.FlightGearInspection
import net.nevinsky.abyssus.lib.core.editor.flightgear.ImportSize
import net.nevinsky.abyssus.lib.core.editor.flightgear.StagedImport
import net.nevinsky.abyssus.lib.core.editor.terrain.FolderNameError
import net.nevinsky.abyssus.lib.core.editor.terrain.checkFolderName
import java.io.File

/** Why an import's size is refused. */
enum class ImportSizeError { SPAN }

/**
 * The Import FlightGear Aircraft dialog's state, free of Swing: the folder name (default `model_<aircraft>`), the
 * size (original metres, or a target wingspan typed as text) and the parts to keep (ticked by default when the
 * aircraft shows them at rest). [request] is null while anything is invalid or no part is ticked.
 */
class FlightGearImportSettings(private val assetsDir: File, val inspection: FlightGearInspection) {
    var folderName: String = "model_" + inspection.aircraft.id.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    var originalSize: Boolean = true
    var spanText: String = "1.0"
    private val ticked = inspection.parts.filter { it.shownAtRest }.map { it.name }.toMutableSet()

    val parts: List<String> get() = inspection.parts.map { it.name }

    /** True when the archive states no licence: redistribution rights are unknown. */
    val licenseUnknown: Boolean get() = inspection.license == null

    fun isTicked(part: String) = part in ticked

    fun tick(part: String, on: Boolean) {
        if (on) ticked += part else ticked -= part
    }

    fun nameError(): FolderNameError? = checkFolderName(assetsDir, folderName)

    fun sizeError(): ImportSizeError? {
        if (originalSize) return null
        val span = spanText.trim().toDoubleOrNull()
        return if (span == null || !span.isFinite() || span <= 0.0) ImportSizeError.SPAN else null
    }

    fun request(): FlightGearImportRequest? {
        if (nameError() != null || sizeError() != null || ticked.isEmpty()) return null
        val size = if (originalSize) ImportSize.Original else ImportSize.Span(spanText.trim().toDouble())
        return FlightGearImportRequest(inspection.aircraft, folderName.trim(), size, parts.toSet() - ticked)
    }
}

/**
 * The undoable write of a [StagedImport] into [projectDir]'s assets: every file new, the folder (and its
 * sub-folders, and `assets` when missing) created, and Undo refused once a scene references the asset.
 */
fun importTransaction(projectDir: File, staged: StagedImport): AssetTransaction = assetFolderTransaction(
    projectDir, AbyssusBundle.message("commandImportFlightGear"), staged.folder, staged.uuid.toString(), staged.files,
)

/**
 * The undoable write of a new asset folder [folder] with [files] (paths inside it) into [projectDir]'s assets: every file
 * new, the folder (its sub-folders, and `assets` when missing) created, and Undo refused once a scene references the
 * asset, except through the entity [ignoring] gives (one the same command placed).
 */
fun assetFolderTransaction(
    projectDir: File,
    name: String,
    folder: String,
    uuid: String,
    files: Map<String, ByteArray>,
    ignoring: () -> PlacedEntity? = { null },
): AssetTransaction {
    val assetsDir = File(projectDir, ASSETS_DIR)
    val base = "$ASSETS_DIR/$folder"
    val subFolders = files.keys.filter { it.contains('/') }.map { it.substringBeforeLast('/') }
        .flatMap { path -> path.split('/').indices.map { path.split('/').take(it + 1).joinToString("/") } }
        .distinct().sortedBy { it.count { c -> c == '/' } }
    return AssetTransaction(
        name,
        changes = files.map { (path, bytes) -> FileChange("$base/$path", FileSnapshot.Absent, FileSnapshot.Bytes(bytes)) },
        createdDirs = (if (assetsDir.isDirectory) emptyList() else listOf(ASSETS_DIR)) + base + subFolders.map { "$base/$it" },
        guard = AssetReferenceGuard(projectDir).let { guard -> { guard.blocker(folder, uuid, ignoring()) } },
    )
}
