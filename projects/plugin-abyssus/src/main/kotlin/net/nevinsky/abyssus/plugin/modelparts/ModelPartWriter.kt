/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.modelparts

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.assets.model.PartHinge
import net.nevinsky.abyssus.lib.core.assets.model.PartPivot
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.editor.scene.CompensateBoundChildren
import net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelPartEdits
import net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelPartsInfo
import net.nevinsky.abyssus.lib.core.editor.modelmesh.pivotMatrix
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.filetype.editAssetMetaAndScene
import net.nevinsky.abyssus.plugin.filetype.editTextDocument

/**
 * Writes a model part's pivot and hinge into the model asset's `meta.json` (OpenSpec `model-part-pivots`,
 * `model-part-hinges`). A pivot change also moves the entities of the scene [scene] that are bound to that node, in the
 * same undoable command; a hinge change touches only the meta.
 */
class ModelPartWriter(private val project: Project) {
    private val edits =
        ModelPartEdits(
            AssetMetaTextEditor(AssetMetaParser(service<AbyssusPluginCtx>().documents.json)),
            EditorBundle,
        )

    /** The `meta.json` of model asset [asset] in the project folder [projectDir], or null when it has none. */
    fun metaFile(projectDir: File, asset: String): VirtualFile? = modelMetaFile(projectDir, asset)

    /**
     * Sets (or, with a null [pivot], clears) the pivot of [node]. [info] is the node's current parts answer, which gives
     * the rest transforms before and after for the children bound to it. False when nothing was written.
     */
    fun setPivot(
        projectDir: File,
        asset: String,
        node: String,
        pivot: PartPivot?,
        info: ModelPartsInfo,
        scene: VirtualFile?,
    ): Boolean {
        val meta = metaFile(projectDir, asset) ?: return false
        val fileFrame = info.fileFrames[node] ?: return false
        val oldRest = info.rest[node] ?: return false
        val newRest = pivot?.let { com.badlogic.gdx.math.Matrix4(fileFrame).mul(pivotMatrix(it)) } ?: fileFrame
        val command =
            AbyssusBundle.message(if (pivot == null) "commandResetPivot" else "commandEditPivot", node)
        return editAssetMetaAndScene(
            project,
            meta,
            scene,
            command,
            { text ->
                if (pivot == null) edits.clearPivot(text, node) else edits.setPivot(text, node, pivot)
            },
            scene?.let { CompensateBoundChildren(asset, node, oldRest, newRest) },
        )
    }

    /** Sets the hinge of [node]; false when the limits are refused or nothing changed. */
    fun setHinge(projectDir: File, asset: String, node: String, hinge: PartHinge, adding: Boolean): Boolean {
        val meta = metaFile(projectDir, asset) ?: return false
        val command = AbyssusBundle.message(if (adding) "commandAddHinge" else "commandEditHinge", node)
        return editTextDocument(project, meta, command, global = true) { edits.setHinge(it, node, hinge) }
    }

    fun clearHinge(projectDir: File, asset: String, node: String): Boolean {
        val meta = metaFile(projectDir, asset) ?: return false
        val command = AbyssusBundle.message("commandRemoveHinge", node)
        return editTextDocument(project, meta, command, global = true) { edits.clearHinge(it, node) }
    }

    /** Whether the limits are acceptable, for a field to show an error; null when they are, else the reason. */
    fun hingeProblem(min: Float, max: Float): String? =
        net.nevinsky.abyssus.lib.core.editor.modelmesh.HingeLimits().let { limits ->
            limits.problem(min, max)?.let { limits.describe(it, EditorBundle) }
        }
}

/** The `meta.json` of model asset [asset] in the project folder [projectDir], or null when it has none. */
fun modelMetaFile(projectDir: File, asset: String): VirtualFile? {
    val folder = FileLoader(projectDir).folder(asset) ?: return null
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(folder, META_FILE))
}

/** The outcome message of a refused edit, when there is one. */
internal fun TextEditOutcome.refusal(): String? = (this as? TextEditOutcome.Refused)?.cause?.message
