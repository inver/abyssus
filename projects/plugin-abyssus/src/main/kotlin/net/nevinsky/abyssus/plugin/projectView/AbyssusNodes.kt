/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.editor.host.*
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.ui.SimpleTextAttributes
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.lib.core.editor.water.ValidWater
import net.nevinsky.abyssus.lib.core.editor.water.WaterSurface
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.lib.core.ui.model.UiScreen
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.SceneEntry
import net.nevinsky.abyssus.plugin.filetype.*
import net.nevinsky.abyssus.plugin.water.WaterGroup
import javax.swing.Icon

/** Shown in a scene entry's label (`name (id)`) and edited via Rename, so not repeated as rows. */
private val SCENE_HEADER = setOf("id", "name")

/** The `.scene` a project-view node can open in the scene view (a standalone scene or a project's scene entry), else null. */
fun viewableSceneFile(node: Any?): VirtualFile? = when (node) {
    is AbyssusAssetNode -> node.virtualFile.takeIf(ProjectLayout::isScene)
    is DtoEntryNode -> sceneFileOf(node.value)
    else -> null
}

fun findTopLevelAssets(project: Project): List<VirtualFile> {
    val found = mutableListOf<VirtualFile>()
    for (root in ProjectRootManager.getInstance(project).contentRoots) {
        VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (ProjectLayout.isAssetFile(file) && !ProjectLayout.isProjectScene(file)) found += file
                return true
            }
        })
    }
    return found.distinct().sortedWith(compareBy({ it.extension != ProjectLayout.PROJECT_EXTENSION }, { it.path }))
}

private fun assetIcon(file: VirtualFile): Icon =
    if (file.extension == ProjectLayout.PROJECT_EXTENSION) AbyssusProjectIcons.PROJECT else SceneIcons.FILE

/** Top level of the view: every `.abss` project (and any scene outside a project), with no folder nodes. */
class AbyssusRootNode(project: Project, settings: ViewSettings?) :
    ProjectViewNode<Project>(project, project, settings) {
    override fun contains(file: VirtualFile) = ProjectRootManager.getInstance(project!!).fileIndex.isInContent(file)

    override fun getChildren(): Collection<AbstractTreeNode<*>> =
        findTopLevelAssets(project!!).map { AbyssusAssetNode(project!!, it, settings) }

    override fun update(presentation: PresentationData) {
        presentation.presentableText = project!!.name
        presentation.setIcon(AbyssusProjectIcons.PROJECT)
    }
}

class AbyssusAssetNode(project: Project, file: VirtualFile, settings: ViewSettings?) :
    ProjectViewNode<VirtualFile>(project, file, settings) {
    override fun contains(file: VirtualFile) = file == value

    override fun getVirtualFile(): VirtualFile = value

    override fun getChildren(): Collection<AbstractTreeNode<*>> =
        when (val result = AssetReadCache.of(project!!).read(value)) {
            null -> emptyList()
            else -> result.obj?.let { root ->
                UnusedFilter.apply(project!!, childrenOf(root)).foldToggles().map {
                    DtoEntryNode(project!!, value.path, it, value, emptyList())
                }
            } ?: emptyList()
        }

    override fun update(presentation: PresentationData) {
        presentation.addText(value.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        presentation.setIcon(assetIcon(value))
        AssetReadCache.of(project!!).read(value)?.takeIf { !it.success }?.let {
            presentation.addText(
                "  " + AbyssusBundle.message("assetParseError", it.message.orEmpty()),
                SimpleTextAttributes.ERROR_ATTRIBUTES
            )
        }
    }
}

/**
 * Identity is the path of the entry inside its asset, so equal values in siblings never collapse.
 * [source] and [parentKeys] locate the entry's container in the JSON file it was read from, which
 * is what an enabled toggle needs to write back.
 */
class DtoEntry(
    val path: String,
    val name: String,
    val value: Any?,
    val enabled: Boolean?,
    val toggleName: String?,
    val source: VirtualFile?,
    val parentKeys: List<String>,
) {
    // The tree keeps an existing node when a refreshed one is equal, so anything that changes how the
    // row looks (the toggle state, a scalar's value, the entity type) must take part to avoid stale rows.
    override fun equals(other: Any?) =
        other is DtoEntry && other.path == path && other.enabled == enabled && rowText(other) == rowText(this) &&
            (if (isScalar(value)) scalarOf(value) == scalarOf(other.value) else !isScalar(other.value)) &&
            (value as? Asset<*>)?.unused == (other.value as? Asset<*>)?.unused && entityType() == other.entityType() &&
            isGame == other.isGame
    override fun hashCode() = path.hashCode()

    /** A component row of a component the game project registers; it carries the `game` badge. */
    val isGame: Boolean get() = isComponentEntry(this) && isGameComponent(name)
}

/** Reads only the admitted row's JSON, without loading runtime components or assets. */
private fun DtoEntry.entityType(): EntityType? {
    if (!isEntityEntry(this)) return null
    val name = net.nevinsky.abyssus.lib.core.editor.host.entityTypeOf(value as? Document) ?: return null
    return EntityType.entries.firstOrNull { it.name == name }
}

private fun entryIcon(entry: DtoEntry): Icon {
    val dto = entry.value
    return when {
        dto is List<*> && entry.name == "scenes" -> ScenesIcons.LIST
        dto is FolderRow -> if (dto.kind == FolderKind.UI) ScenesIcons.UI else ScenesIcons.ASSETS
        dto is UiScreenGroup -> if (dto.kind == net.nevinsky.abyssus.lib.core.ui.model.UiScreenKind.OVERLAY) ScenesIcons.UI_OVERLAYS else ScenesIcons.UI_SCREENS
        dto is TypeGroup -> ScenesIcons.FOLDER
        dto is UiScreen || dto is UiScreenEntry -> ScenesIcons.UI_SCREEN
        dto is WaterGroup -> ScenesIcons.WATER
        dto is ValidWater -> if (dto.settings.preset == WaterPreset.SEA) ScenesIcons.WATER_SEA else ScenesIcons.WATER_LAKE
        dto is WaterSurface -> AllIcons.Nodes.Variable
        dto is Asset<*> -> AssetIcons.forType(dto.meta.type.name)
        isEntityEntry(entry) -> EntityIcons.forType(entry.entityType())
        isComponentEntry(entry) -> ComponentIcons.forComponent(entry.name)
        dto is SceneEntry && dto.file.extension == ProjectLayout.SCENE_EXTENSION -> SceneIcons.FILE
        PropertyIcons.forProperty(entry.name) != null -> PropertyIcons.forProperty(entry.name)!!
        isScalar(dto) -> AllIcons.Nodes.Property
        else -> AllIcons.Nodes.Class
    }
}

class DtoEntryNode(
    project: Project,
    parentPath: String,
    row: DtoRow,
    source: VirtualFile?,
    parentKeys: List<String>,
    var label: String? = null,
    /** True when an ancestor is disabled, so the whole subtree is grayed too. */
    private val inheritedDisabled: Boolean = false,
) : AbstractTreeNode<DtoEntry>(
    project,
    DtoEntry("$parentPath/${row.name}", row.name, row.value, row.enabled, row.toggleName, source, parentKeys),
) {
    private fun child(row: DtoRow, label: String? = null): DtoEntryNode {
        val v = value
        val ownSource = (v.value as? SceneEntry)?.file
        // an object read from its own file restarts the key path; otherwise it extends this entry's
        val (src, baseKeys) = if (ownSource != null) ownSource to emptyList() else v.source to (v.parentKeys + v.name)
        // keys the view skips (`entities`, `components`) stay in the path and the key path, so they still name the real location
        val keys = baseKeys + row.via
        val parentPath = row.via.fold(v.path) { acc, key -> "$acc/$key" }
        return DtoEntryNode(project!!, parentPath, row, src, keys, label, isGray)
    }

    /** The Game UI asset this row is, or the one it is a screen of, with the screen to show. */
    private fun uiTarget(): Pair<Asset<*>, String?>? {
        val own = value.value
        if (own is UiScreenEntry) return own.asset to own.screen.id
        if (own is Asset<*>) return own.takeIf { it.type == MetaType.UI }?.let { it to null }
        if (own !is UiScreen) return null
        var node: AbstractTreeNode<*>? = parent
        while (node != null) {
            val asset = (node as? DtoEntryNode)?.value?.value as? Asset<*>
            if (asset != null) return asset to own.id
            node = node.parent
        }
        return null
    }

    private fun uiMetaFile(asset: Asset<*>): VirtualFile? =
        LocalFileSystem.getInstance().findFileByIoFile(java.io.File(asset.baseDir, META_FILE))

    override fun canNavigate() = uiTarget() != null

    override fun canNavigateToSource() = canNavigate()

    override fun navigate(requestFocus: Boolean) {
        val (asset, screen) = uiTarget() ?: return
        uiMetaFile(asset)?.let { project!!.service<UiScreenOpener>().open(it, screen) }
    }

    private val isDisabled get() = inheritedDisabled || value.enabled == false

    /** A project asset no scene reaches. */
    private val isUnused get() = (value.value as? Asset<*>)?.unused == true

    private val isGray get() = isDisabled || isUnused

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val v = value
        val dto = v.value
        val rows = when {
            isEcsEntry(v) -> ecsRows(dto as Document)
            // a path arrangement is a plain line: its copies are squashed into it and its components are edited in Properties
            isEntityEntry(v) && isArrangementEntity(dto as Document) -> emptyList()
            isEntityEntry(v) -> entityRows(dto as Document)
            else -> childrenOf(dto)
        }.filterNot { sceneFileOf(v) != null && it.name in SCENE_HEADER }.foldToggles()
        return rows.mapIndexed { i, row ->
            val label = when {
                dto is List<*> -> elementLabel(v.name, row.value, i)
                row.value is Asset<*> -> row.value.name // an asset in a type group or under UI is named by its folder
                else -> null
            }
            child(row, label)
        }
    }

    override fun update(presentation: PresentationData) {
        val v = value
        val shown = rowText(v, label)
        val attrs = if (isGray) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
        val secondaryAttrs =
            if (isGray) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.GRAY_ATTRIBUTES
        if (isScalar(v.value)) {
            presentation.addText("${shown.label}: ", attrs)
            presentation.addText((scalarOf(v.value) ?: EditorBundle.message("dtoNullValue")).toString(), secondaryAttrs)
        } else {
            presentation.addText(shown.label, attrs)
            shown.secondary?.let { presentation.addText("  $it", secondaryAttrs) }
        }
        // a disabled row, and everything under a disabled one, shows a dimmed icon
        presentation.setIcon(entryIcon(v).let { if (isDisabled) com.intellij.openapi.util.IconLoader.getDisabledIcon(it) else it })
    }
}
