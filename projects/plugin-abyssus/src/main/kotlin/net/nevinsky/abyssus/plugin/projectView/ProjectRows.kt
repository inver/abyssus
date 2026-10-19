/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.plugin.dto.ProjectDto

/** The two asset folders under a project row; `Scenes` stays the project's own `scenes` list. */
enum class FolderKind { UI, ASSETS }

/** A view-only folder row (`UI` or `Assets`) holding [assets]. Nothing in a document names it. */
data class FolderRow(val kind: FolderKind, val assets: List<Asset<Any>>)

/** A view-only type group under `Assets`: every asset whose type falls in [group] (`SKYBOX` also holds the procedural and HDR skies). */
data class TypeGroup(val group: String, val assets: List<Asset<Any>>)

/** The group a [type] lists under: the three skybox types share one. */
fun groupOf(type: MetaType): String = when (type) {
    MetaType.SKYBOX, MetaType.SKYBOX_PROCEDURAL, MetaType.SKYBOX_HDR -> "SKYBOX"
    else -> type.name
}

private val GROUP_ORDER = listOf("MODEL", "TERRAIN", "FOLIAGE", "TEXTURE", "SKYBOX", "CLOUDS")

/** Fixed order for the well-known groups, then any other group alphabetically. */
val typeGroupOrder: Comparator<String> = compareBy<String>({ GROUP_ORDER.indexOf(it).let { i -> if (i < 0) GROUP_ORDER.size else i } }, { it })

/** The `Scenes`, `UI` and `Assets` rows of [project], in that order; the project's `name` is not a row. */
fun projectRows(project: ProjectDto): List<DtoRow> {
    val (ui, other) = project.assets.partition { it.meta.type == MetaType.UI }
    return listOf(
        DtoRow("scenes", project.scenes),
        DtoRow("ui", FolderRow(FolderKind.UI, ui)),
        DtoRow("assets", FolderRow(FolderKind.ASSETS, other)),
    )
}

/** The rows under a folder: `UI` lists its assets, `Assets` one [TypeGroup] per type present. */
fun folderRows(folder: FolderRow): List<DtoRow> = when (folder.kind) {
    FolderKind.UI -> folder.assets.sortedBy { it.name }.map { DtoRow(it.name, it) }
    FolderKind.ASSETS -> folder.assets.groupBy { groupOf(it.meta.type) }.toSortedMap(typeGroupOrder).map { (group, assets) ->
        DtoRow(group, TypeGroup(group, assets))
    }
}

/** The assets of a type group, by asset folder name. */
fun groupRows(group: TypeGroup): List<DtoRow> = group.assets.sortedBy { it.name }.map { DtoRow(it.name, it) }

/**
 * The place of a row that keeps a fixed order under the platform's sorting: `Scenes`, `UI`, `Assets`, then the type groups in
 * [typeGroupOrder]. Null for every other row, which sorts as the platform sorts.
 */
internal fun fixedRank(value: Any?, name: String): Pair<Int, String>? = when {
    value is List<*> && name == "scenes" -> 0 to ""
    value is FolderRow -> (if (value.kind == FolderKind.UI) 1 else 2) to ""
    value is TypeGroup -> 3 to value.group
    else -> null
}

/** Orders [a] and [b] by [fixedRank] when both have one; null when the platform's comparator decides. */
internal fun compareFixed(a: Pair<Int, String>?, b: Pair<Int, String>?): Int? {
    if (a == null || b == null) return null
    return if (a.first != b.first) a.first.compareTo(b.first) else typeGroupOrder.compare(a.second, b.second)
}
