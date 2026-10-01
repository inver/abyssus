package net.nevinsky.abyssus.projectView

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.ui.SimpleTextAttributes
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.dto.AssetReadResult
import net.nevinsky.abyssus.dto.AssetReader
import net.nevinsky.abyssus.dto.DtoProperty
import net.nevinsky.abyssus.dto.DtoValue
import net.nevinsky.abyssus.dto.ProjectReader
import net.nevinsky.abyssus.dto.foldToggles
import net.nevinsky.abyssus.filetype.AbyssusProjectIcons
import net.nevinsky.abyssus.filetype.SceneIcons
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

/** Exact, case-sensitive, suffix-based match: `.SCENE` and `.scene.bak` are not assets. */
val ASSET_EXTENSIONS = setOf("scene", "abss")

fun isAssetFile(file: VirtualFile) = !file.isDirectory && file.extension in ASSET_EXTENSIONS

/** A scene in the `scenes` folder next to an `.abss` is shown inside that project, not on its own. */
fun isProjectScene(file: VirtualFile): Boolean {
    if (file.extension != "scene") return false
    val dir = file.parent ?: return false
    return dir.name == ProjectReader.SCENES_DIR && dir.parent?.children?.any { it.extension == "abss" } == true
}

fun findTopLevelAssets(project: Project): List<VirtualFile> {
    val found = mutableListOf<VirtualFile>()
    for (root in ProjectRootManager.getInstance(project).contentRoots) {
        VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (isAssetFile(file) && !isProjectScene(file)) found += file
                return true
            }
        })
    }
    return found.distinct().sortedWith(compareBy({ it.extension != "abss" }, { it.path }))
}

private object AssetCache {
    private data class Entry(val stamp: Long, val result: AssetReadResult)

    private val cache = ConcurrentHashMap<VirtualFile, Entry>()

    fun read(file: VirtualFile): AssetReadResult? {
        val reader = AssetReader.forExtension(file.extension) ?: return null
        val stamp = reader.stamp(file)
        cache[file]?.takeIf { it.stamp == stamp }?.let { return it.result }
        return reader.read(file).also { cache[file] = Entry(stamp, it) }
    }
}

private fun assetIcon(file: VirtualFile): Icon =
    if (file.extension == "abss") AbyssusProjectIcons.FILE else SceneIcons.FILE

/** Top level of the view: every `.abss` project (and any scene outside a project), with no folder nodes. */
class AbyssusRootNode(project: Project, settings: ViewSettings?) :
    ProjectViewNode<Project>(project, project, settings) {
    override fun contains(file: VirtualFile) = ProjectRootManager.getInstance(project!!).fileIndex.isInContent(file)

    override fun getChildren(): Collection<AbstractTreeNode<*>> =
        findTopLevelAssets(project!!).map { AbyssusAssetNode(project!!, it, settings) }

    override fun update(presentation: PresentationData) {
        presentation.presentableText = project!!.name
        presentation.setIcon(AllIcons.Nodes.Project)
    }
}

class AbyssusAssetNode(project: Project, file: VirtualFile, settings: ViewSettings?) :
    ProjectViewNode<VirtualFile>(project, file, settings) {
    override fun contains(file: VirtualFile) = file == value

    override fun getVirtualFile(): VirtualFile = value

    override fun getChildren(): Collection<AbstractTreeNode<*>> =
        when (val result = AssetCache.read(value)) {
            is AssetReadResult.Success -> result.root.properties.foldToggles().map { DtoEntryNode(project!!, value.path, it, result.root.source ?: value, emptyList()) }
            else -> emptyList()
        }

    override fun update(presentation: PresentationData) {
        presentation.addText(value.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        presentation.setIcon(assetIcon(value))
        (AssetCache.read(value) as? AssetReadResult.Failure)?.let {
            presentation.addText("  " + AbyssusBundle.message("assetParseError", it.message), SimpleTextAttributes.ERROR_ATTRIBUTES)
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
    val value: DtoValue,
    val enabled: Boolean?,
    val toggleName: String?,
    val source: VirtualFile?,
    val parentKeys: List<String>,
) {
    // The tree keeps an existing node when a refreshed one is equal, so anything that changes how the
    // row looks (the toggle state, a scalar's value) must take part, or the row stays stale after a toggle.
    override fun equals(other: Any?) =
        other is DtoEntry && other.path == path && other.enabled == enabled &&
            (value as? DtoValue.Scalar) == (other.value as? DtoValue.Scalar)
    override fun hashCode() = path.hashCode()
}

class DtoEntryNode(
    project: Project,
    parentPath: String,
    property: DtoProperty,
    source: VirtualFile?,
    parentKeys: List<String>,
    var label: String? = null,
    /** True when an ancestor is disabled, so the whole subtree is grayed too. */
    private val inheritedDisabled: Boolean = false,
) : AbstractTreeNode<DtoEntry>(
    project,
    DtoEntry("$parentPath/${property.name}", property.name, property.value, property.enabled, property.toggleName, source, parentKeys),
) {
    private fun child(property: DtoProperty, label: String? = null): DtoEntryNode {
        val v = value
        val dto = v.value
        val ownSource = (dto as? DtoValue.Obj)?.source
        // an object read from its own file restarts the key path; otherwise it extends this entry's
        val (src, keys) = if (ownSource != null) ownSource to emptyList() else v.source to (v.parentKeys + v.name)
        return DtoEntryNode(project!!, v.path, property, src, keys, label, isDisabled)
    }

    private val isDisabled get() = inheritedDisabled || value.enabled == false

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val v = value
        return when (val dto = v.value) {
            is DtoValue.Scalar -> emptyList()
            is DtoValue.Obj -> dto.properties.foldToggles().map { child(it) }
            is DtoValue.Items -> dto.items.mapIndexed { i, item ->
                child(DtoProperty("$i", item), (item as? DtoValue.Obj)?.label ?: AbyssusBundle.message("dtoListElementLabel", v.name, i))
            }
        }
    }

    override fun update(presentation: PresentationData) {
        val v = value
        val shown = label ?: v.name
        val text = when (val dto = v.value) {
            is DtoValue.Scalar -> "$shown: ${dto.value ?: AbyssusBundle.message("dtoNullValue")}"
            else -> shown
        }
        val attrs = if (isDisabled) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
        presentation.addText(text, attrs)
        presentation.setIcon(if (v.value is DtoValue.Scalar) AllIcons.Nodes.Property else AllIcons.Nodes.Class)
    }
}
