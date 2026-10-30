/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.project

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.dto.ProjectDto
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import java.io.File

enum class UiRegistryProblem { DUPLICATE, MISSING, UNREADABLE, WRONG_TYPE }

/** One `uiAssetNames` entry of a project: a [Registered] UI asset (maybe refused by admission) or a [Problem]. */
sealed interface UiRegistryEntry {
    val name: String

    data class Registered(override val name: String, val admission: UiAdmission) : UiRegistryEntry
    data class Problem(override val name: String, val problem: UiRegistryProblem) : UiRegistryEntry
}

/**
 * Resolves the project's `uiAssetNames` against its asset folders. Reading is on the caller's thread; the project file
 * is never touched, so an omitted list stays omitted.
 */
class UiProjectRegistry(
    private val json: JsonProcessor,
    private val files: FileLoader,
    private val decoder: UiCollectionDecoder = UiCollectionDecoder(),
) {
    /** One entry per listed name, in order; a repeated name yields [UiRegistryProblem.DUPLICATE] after its first use. */
    fun entries(project: ProjectDto): List<UiRegistryEntry> {
        val seen = HashSet<String>()
        return project.uiAssetNames.map { name ->
            if (!seen.add(name)) UiRegistryEntry.Problem(name, UiRegistryProblem.DUPLICATE) else entry(name)
        }
    }

    /** The admitted-or-refused UI asset registered as [name], or null when [name] is not a valid registered UI asset. */
    fun lookup(project: ProjectDto, name: String): UiAdmission? =
        (entries(project).firstOrNull { it.name == name } as? UiRegistryEntry.Registered)?.admission

    private fun entry(name: String): UiRegistryEntry {
        val meta = files.folder(name)?.let { File(it, META_FILE) }?.takeIf { it.isFile }
            ?: return UiRegistryEntry.Problem(name, UiRegistryProblem.MISSING)
        val tree = runCatchingKeepingCancellation { json.readObject(meta.readText()) }.getOrNull()
            ?: return UiRegistryEntry.Problem(name, UiRegistryProblem.UNREADABLE)
        if (tree.get("type")?.takeIf { it.isTextual }?.textValue() != "UI")
            return UiRegistryEntry.Problem(name, UiRegistryProblem.WRONG_TYPE)
        return UiRegistryEntry.Registered(name, decoder.decode(tree))
    }
}
