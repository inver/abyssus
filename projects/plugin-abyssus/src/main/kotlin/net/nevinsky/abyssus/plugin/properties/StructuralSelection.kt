/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ide.util.treeView.AbstractTreeNode
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ui.model.UiScreen
import net.nevinsky.abyssus.lib.core.editor.panel.SceneSetting
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.projectView.DtoEntryNode
import net.nevinsky.abyssus.plugin.projectView.FolderRow
import net.nevinsky.abyssus.plugin.projectView.TypeGroup
import net.nevinsky.abyssus.plugin.projectView.isEcsEntry
import net.nevinsky.abyssus.plugin.projectView.rowText
import net.nevinsky.abyssus.lib.core.editor.panel.FolderKind as PanelFolderKind

/**
 * A selected tree row that stands for no single document of its own: a folder, the `ecs` row, a scene setting row or a
 * UI screen. It decides which panel layout shows; reading what the layout needs is [readStructuralState].
 */
sealed interface Structural {
    /** A `Scenes`, `UI` or `Assets` row, or a type group under `Assets`; [abss] is the project file the row sits in. */
    data class Folder(val kind: PanelFolderKind, val name: String, val items: Int, val abss: VirtualFile?) : Structural

    data class Setting(val file: VirtualFile, val setting: SceneSetting) : Structural

    data class Ecs(val file: VirtualFile) : Structural

    /** A screen row; [meta] is the UI asset's `meta.json` and [screenId] the screen to open in the Screen Editor. */
    data class Screen(val name: String, val asset: String, val screenId: String, val meta: VirtualFile?, val abss: VirtualFile?) :
        Structural
}

private val SETTINGS = SceneSetting.entries.associateBy { it.key }

/** The structural meaning of the tree row [node], or null for any other node. */
internal fun structuralOf(node: Any?): Structural? {
    val tree = node as? DtoEntryNode ?: return null
    val entry = tree.value
    val value = entry.value
    val source = entry.source?.takeIf { it.isValid }
    val abss = source?.takeIf { it.extension == ProjectLayout.PROJECT_EXTENSION }
    return when {
        value is FolderRow -> Structural.Folder(
            if (value.kind == net.nevinsky.abyssus.plugin.projectView.FolderKind.UI) PanelFolderKind.UI else PanelFolderKind.ASSETS,
            tree.label ?: rowText(entry).label, value.assets.size, abss,
        )

        value is TypeGroup -> Structural.Folder(PanelFolderKind.GROUP, tree.label ?: rowText(entry).label, value.assets.size, abss)
        value is List<*> && entry.name == "scenes" && abss != null ->
            Structural.Folder(PanelFolderKind.SCENES, tree.label ?: rowText(entry).label, value.size, abss)

        value is UiScreen -> uiAsset(tree)?.let { asset ->
            Structural.Screen(value.name, asset.name, value.id, java.io.File(asset.baseDir, "meta.json").let {
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByIoFile(it)
            }, abss)
        }

        source != null && ProjectLayout.isScene(source) && entry.parentKeys.isEmpty() ->
            if (value is JsonNode && isEcsEntry(entry)) Structural.Ecs(source)
            else SETTINGS[entry.name]?.let { Structural.Setting(source, it) }

        else -> null
    }
}

private fun uiAsset(node: DtoEntryNode): Asset<*>? {
    var parent: AbstractTreeNode<*>? = node.parent
    while (parent != null) {
        val asset = (parent as? DtoEntryNode)?.value?.value as? Asset<*>
        if (asset != null && asset.type == MetaType.UI) return asset
        parent = parent.parent
    }
    return null
}
