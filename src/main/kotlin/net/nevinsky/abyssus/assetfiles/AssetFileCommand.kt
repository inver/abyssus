/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assetfiles

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.command.undo.UnexpectedUndoException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.filetype.documentDisplayMessage as displayMessage

/**
 * Applies an [AssetTransaction] to a [AssetFileStore], forward or in reverse, as far as the files allow: every
 * expectation is checked first, nothing is written when one fails, and a failure part-way puts back what was written
 * (application-level rollback, not a filesystem transaction: a process that dies mid-write can leave files behind).
 * Pure file logic with no platform state, so it is tested with an in-memory store that fails on demand.
 */
class AssetTransactionEngine(val store: AssetFileStore) {
    /** Why the transaction cannot be applied now, or null when its starting state holds. */
    fun verify(txn: AssetTransaction, forward: Boolean): AssetCommandResult? {
        if (forward) {
            txn.createdDirs.firstOrNull { store.dirExists(it) }?.let { return AssetCommandResult.Collision(it) }
            for ((path, expected) in txn.expectedFiles) if (store.read(path) != expected) return AssetCommandResult.Conflict(path)
            for (c in txn.changes) if (store.read(c.path) != c.before) return AssetCommandResult.Conflict(c.path)
            return null
        }
        for (c in txn.changes) if (store.read(c.path) != c.after) return AssetCommandResult.Conflict(c.path)
        for (dir in txn.createdDirs) {
            val expected = (txn.changes.map { it.path } + txn.createdDirs).filter { it.substringBeforeLast('/', "") == dir }
                .map { it.substringAfterLast('/') }.toSet()
            if (store.children(dir).toSet() != expected) return AssetCommandResult.Conflict(dir)
        }
        txn.guard()?.let { return AssetCommandResult.Blocked(it) }
        return null
    }

    /** Verifies, then applies; on a failed write restores the files already written. Call inside a write action. */
    fun apply(txn: AssetTransaction, forward: Boolean, beforeFirstWrite: () -> Boolean = { true }): AssetCommandResult {
        verify(txn, forward)?.let { return it }
        // cancellation is honoured only here: once the first file is written, the commit and any rollback run to the end
        if (!beforeFirstWrite()) return AssetCommandResult.Cancelled
        val undo = ArrayDeque<Pair<String, () -> Unit>>()
        try {
            if (forward) {
                for (dir in txn.createdDirs) {
                    // registered first, and conditional: a makeDir that fails after taking effect is undone too
                    undo.addFirst(dir to { if (store.dirExists(dir)) store.removeDir(dir) })
                    store.makeDir(dir)
                }
                for (c in txn.changes) write(c.path, c.before, c.after, undo)
            } else {
                for (c in txn.changes.asReversed()) write(c.path, c.after, c.before, undo)
                for (dir in txn.createdDirs.asReversed()) {
                    undo.addFirst(dir to { if (!store.dirExists(dir)) store.makeDir(dir) })
                    store.removeDir(dir)
                }
            }
        } catch (e: Throwable) {
            return Failed(e, undo)
        }
        return AssetCommandResult.Done
    }

    private fun write(path: String, from: FileSnapshot, to: FileSnapshot, undo: ArrayDeque<Pair<String, () -> Unit>>) {
        // the inverse is registered first: a write that fails half way is then restored too
        undo.addFirst(path to { restore(path, from) })
        when (to) {
            FileSnapshot.Absent -> store.delete(path)
            is FileSnapshot.Bytes -> store.write(path, to.toByteArray())
        }
    }

    private fun restore(path: String, snapshot: FileSnapshot) {
        when (snapshot) {
            FileSnapshot.Absent -> if (store.read(path) != FileSnapshot.Absent) store.delete(path)
            is FileSnapshot.Bytes -> store.write(path, snapshot.toByteArray())
        }
    }

    private fun Failed(cause: Throwable, undo: ArrayDeque<Pair<String, () -> Unit>>): AssetCommandResult.Failed {
        val leftover = mutableListOf<String>()
        for ((path, inverse) in undo) {
            try {
                inverse()
            } catch (e: Throwable) {
                cause.addSuppressed(e)
                leftover += path
            }
        }
        return AssetCommandResult.Failed(cause, leftover)
    }
}

/**
 * Runs an [AssetTransaction] as one named, undoable command. The files are staged as immutable snapshots before this is
 * called; here the starting state is checked again inside the write command (so no event gap can overwrite a newer edit),
 * the writes happen, and only after they all succeed is Undo registered and [onDone] called once.
 */
class AssetFileCommand(private val project: Project, private val store: AssetFileStore) {
    private val engine = AssetTransactionEngine(store)

    /**
     * [affected] are the files whose Undo stack the command joins (the `meta.json` shown in the properties panel, say), so
     * Undo from that context reaches it; empty makes it a global action. Call on the UI thread.
     */
    fun execute(
        txn: AssetTransaction,
        affected: () -> List<VirtualFile> = { emptyList() },
        isCancelled: () -> Boolean = { false },
        onDone: () -> Unit = {},
    ): AssetCommandResult {
        var result: AssetCommandResult = AssetCommandResult.Cancelled
        WriteCommandAction.runWriteCommandAction(project, txn.name, null, {
            result = engine.apply(txn, forward = true) { !isCancelled() }
            if (result == AssetCommandResult.Done) {
                UndoManager.getInstance(project).undoableActionPerformed(AssetFileUndoAction(txn, engine, affected()))
            }
        })
        // the VFS learns of the files after the command, so the platform records nothing for them itself
        store.flush(async = false)
        if (result == AssetCommandResult.Done) onDone()
        return result
    }
}

/** Undo and Redo of an [AssetTransaction]: each re-checks the files and refuses, with the reason, when they changed since. */
class AssetFileUndoAction(
    private val txn: AssetTransaction,
    private val engine: AssetTransactionEngine,
    affected: List<VirtualFile>,
) : UndoableAction {
    // a text file is referenced through its document, as an editor on it does; a binary file through the file
    private val references: Array<DocumentReference> = affected.map { file ->
        val manager = DocumentReferenceManager.getInstance()
        FileDocumentManager.getInstance().getDocument(file)?.let(manager::create) ?: manager.create(file)
    }.toTypedArray()

    override fun getAffectedDocuments(): Array<DocumentReference>? = references.takeIf { it.isNotEmpty() }

    override fun isGlobal() = references.isEmpty()

    override fun undo() = run(forward = false)

    override fun redo() = run(forward = true)

    private fun run(forward: Boolean) {
        when (val result = engine.apply(txn, forward)) {
            AssetCommandResult.Done -> engine.store.flush(async = true)
            is AssetCommandResult.Conflict -> throw UnexpectedUndoException("${txn.name}: ${result.path} changed since")
            is AssetCommandResult.Collision -> throw UnexpectedUndoException("${txn.name}: ${result.path} already exists")
            is AssetCommandResult.Blocked -> throw UnexpectedUndoException(result.reason)
            AssetCommandResult.Cancelled -> throw UnexpectedUndoException("${txn.name}: cancelled")
            is AssetCommandResult.Failed -> throw UnexpectedUndoException("${txn.name}: ${result.cause.displayMessage()}")
        }
    }
}
