/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.foliage

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.plugin.assetfiles.*
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.filetype.editSceneJson

/** Keeps the metadata document's native Undo and the binary transaction in the same command. */
internal fun applyFoliageFiles(
    project: Project,
    command: AssetFileCommand,
    txn: AssetTransaction,
    meta: VirtualFile,
    before: String,
    after: String,
): AssetCommandResult {
    if (before == after) return command.execute(txn, affected = { listOf(meta) })
    val target = SceneJson().parse(after) as ObjectNode
    var result: AssetCommandResult = AssetCommandResult.Cancelled
    CommandProcessor.getInstance().executeCommand(project, {
        if (textOf(meta) != before) {
            result = AssetCommandResult.Conflict(meta.path)
            return@executeCommand
        }
        var undo: AssetFileUndoAction? = null
        result = command.execute(txn, affected = { listOf(meta) }, handBack = { undo = it })
        if (result != AssetCommandResult.Done) return@executeCommand
        val manager = UndoManager.getInstance(project)
        // Undo runs actions in reverse: this saves the document after its native text Undo has finished.
        manager.undoableActionPerformed(MetadataSaveAction(meta, onUndo = true))
        val edited = editSceneJson(project, meta, txn.name) { root ->
            (root as ObjectNode).removeAll().setAll<ObjectNode>(target)
            true
        }
        if (!edited) {
            result = command.revert(txn).let {
                if (it == AssetCommandResult.Done) AssetCommandResult.Conflict(meta.path) else it
            }
            return@executeCommand
        }
        manager.undoableActionPerformed(undo!!)
        // Redo runs actions forward: this saves after the native text Redo and the binary Redo have finished.
        manager.undoableActionPerformed(MetadataSaveAction(meta, onUndo = false))
    }, txn.name, null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION)
    command.flush()
    return result
}

private class MetadataSaveAction(private val file: VirtualFile, private val onUndo: Boolean) : UndoableAction {
    private val reference = DocumentReferenceManager.getInstance().create(
        FileDocumentManager.getInstance().getDocument(file)!!,
    )
    override fun getAffectedDocuments() = arrayOf(reference)
    override fun isGlobal() = false
    override fun undo() { if (onUndo) save() }
    override fun redo() { if (!onUndo) save() }
    private fun save() {
        val manager = FileDocumentManager.getInstance()
        manager.getCachedDocument(file)?.let(manager::saveDocument)
    }
}
