/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.sync

import net.nevinsky.abyssus.lib.updater.manifest.Manifest
import net.nevinsky.abyssus.lib.updater.manifest.ManifestFile
import net.nevinsky.abyssus.lib.updater.manifest.ManifestPart

/** The SHA-256 of a file of the version an update starts from, `null` when it has no such file. */
fun interface BaseFiles {
    fun sha256(path: String): String?
}

/** What an update must fetch ([partsToDownload]) and what it can copy from the base version ([filesToCopy]). */
data class UpdatePlan(val partsToDownload: List<ManifestPart>, val filesToCopy: List<ManifestFile>)

/** A part is downloaded only when one of its files is missing from the base or differs; the others are copied. */
class UpdatePlanner {
    fun plan(manifest: Manifest, base: BaseFiles): UpdatePlan {
        val changed = manifest.files.filter { base.sha256(it.path) != it.sha256 }.map { it.part }.toSet()
        val parts = manifest.parts.filter { it.name in changed }
        val copied = manifest.files.filter { it.part !in changed }
        return UpdatePlan(parts, copied)
    }
}
