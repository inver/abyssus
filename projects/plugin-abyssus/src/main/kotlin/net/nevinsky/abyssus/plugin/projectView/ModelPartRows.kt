/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.ModelKey
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelPart
import net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelPartsInfo
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.filetype.AbyssusModelPartsRead

/** The `Parts` row of the model entity [entityId]: [info] is null while the model is read or cannot be read. */
class PartsNode(
    val scene: SceneDto,
    val entityId: Long,
    val asset: String,
    val meshId: String?,
    val info: ModelPartsInfo?,
)

/** A part row of [entityId]'s model: [part] with its children, as the model nests them. */
class PartNode(
    val scene: SceneDto,
    val entityId: Long,
    val asset: String,
    val meshId: String?,
    val part: ModelPart,
)

/**
 * Which part of which model entity a tree row stands for, with the scene file it is in: what the scene view selects
 * when the row is selected.
 */
data class PartTarget(val file: VirtualFile, val entityId: Long, val node: String, val asset: String, val meshId: String?)

/** The part the Abyssus view row [node] stands for, or null for any other row. */
fun partTargetOf(node: Any?): PartTarget? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    val file = entry.source?.takeIf { it.isValid } ?: return null
    val part = entry.value as? PartNode ?: return null
    return PartTarget(file, part.entityId, part.part.name, part.asset, part.meshId)
}

/** The model entity row [node] (or its `Parts` row) stands for, with the scene file; null for any other row. */
fun partsOwnerOf(node: Any?): Pair<VirtualFile, Long>? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    val file = entry.source?.takeIf { it.isValid } ?: return null
    return (entry.value as? PartsNode)?.let { file to it.entityId }
}

/**
 * Reads the parts of the models the tree lists, in the background: [cached] answers at once, [request] starts the read
 * and publishes [AbyssusModelPartsRead] when it is done so the tree rebuilds with the part rows.
 */
@Service(Service.Level.PROJECT)
class TreeModelParts(private val project: Project) {
    private val requested = ConcurrentHashMap.newKeySet<Pair<File, String>>()

    private fun loading() = service<AbyssusPluginCtx>().assets.loading

    /** The parts read for model [key]; null while they have not been read, `info` null when it cannot be read. */
    fun cached(dir: File, key: String) = loading().cachedModelParts(dir, key)

    fun request(dir: File, key: String, sceneFile: VirtualFile) {
        if (!requested.add(dir to key)) {
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                runCatchingKeepingCancellation { loading().modelParts(dir, key) }
            } finally {
                requested.remove(dir to key)
            }
            if (!project.isDisposed) {
                project.messageBus.syncPublisher(AbyssusModelPartsRead.TOPIC).partsRead(sceneFile)
            }
        }
    }

    companion object {
        fun of(project: Project): TreeModelParts = project.service()
    }
}

/** The model asset and mesh a model entity draws, or null when it is not a model entity with an asset. */
private fun modelOf(node: EntityNode): Pair<String, String?>? {
    val entity = node.entity ?: return null
    if (!isModelEntity(entity)) return null
    val render = entity.component(RenderComponent::class.java) ?: return null
    if (render.type != MetaType.MODEL || render.assetName.isEmpty()) return null
    return render.assetName to render.meshId?.takeIf(String::isNotEmpty)
}

/**
 * The `Parts` row of model entity [node] in the scene file [sceneFile], or none for an entity that draws no model. The
 * parts come from the cache; a miss starts a background read and the row is empty until it is done.
 */
fun partsRows(project: Project, node: EntityNode, sceneFile: VirtualFile?): List<DtoRow> {
    val (asset, meshId) = modelOf(node) ?: return emptyList()
    var info: ModelPartsInfo? = null
    val dir = sceneFile?.let(ProjectLayout::projectDirFor)
    if (dir != null) {
        val reads = TreeModelParts.of(project)
        val key = ModelKey(asset, meshId).encode()
        val cached = reads.cached(dir, key)
        if (cached == null) {
            reads.request(dir, key, sceneFile)
        } else {
            info = cached.info
        }
    }
    return listOf(DtoRow(PARTS_ROW, PartsNode(node.scene, node.id, asset, meshId, info)))
}

/** The name of the `Parts` row; part rows are named by their node. */
const val PARTS_ROW = "parts"

/** The part rows below a `Parts` row or a part row. */
fun partRows(node: PartsNode): List<DtoRow> =
    node.info?.roots.orEmpty().map { DtoRow(it.name, PartNode(node.scene, node.entityId, node.asset, node.meshId, it)) }

fun partRows(node: PartNode): List<DtoRow> =
    node.part.children.map { DtoRow(it.name, PartNode(node.scene, node.entityId, node.asset, node.meshId, it)) }

/** What a part row shows after its name: the marks of its pivot and hinge. */
fun partMarks(part: ModelPart): String? {
    val marks = buildList {
        if (part.pivot.configured) add(AbyssusBundle.message("treePartPivot"))
        if (part.hinge != null) add(AbyssusBundle.message("treePartHinge"))
    }
    return marks.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
