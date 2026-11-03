/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.undo

import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Saves the cached document of [file] after an undo ([onUndo] true) or a redo ([onUndo] false), so that a command whose
 * disk files were written outside the document leaves the document and the disk in step. Registered next to the command
 * that edited [file].
 */
internal class DocumentSaveUndoableAction(private val file: VirtualFile, private val onUndo: Boolean) : UndoableAction {
    private val reference: DocumentReference = DocumentReferenceManager.getInstance().create(
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
