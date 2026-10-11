/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.assetfiles

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.command.undo.UnexpectedUndoException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage as displayMessage

/**
 * Applies an [AssetTransaction] to a [AssetFileStore], forward or in reverse, as far as the files allow: every
 * expectation is checked first, nothing is written when one fails, and a failure part-way puts back what was written
 * (application-level rollback, not a filesystem transaction: a process that dies mid-write can leave files behind).
 * Pure file logic with no platform state, so it is tested with an in-memory store that fails on demand.
 *
 * Two kinds of staged content are resolved to whole-file bytes before those checks: a [FileSnapshot.Patch] is applied
 * over the file version its digest names, and a [DerivedFile] is checked by digest and rebuilt when written.
 */
class AssetTransactionEngine(val store: AssetFileStore) {
    /** Why the transaction cannot be applied now, or null when its starting state holds. */
    fun verify(txn: AssetTransaction, forward: Boolean): AssetCommandResult? {
        val changes = when (val resolved = patchesToBytes(txn)) {
            is Patches.Ready -> resolved.changes
            is Patches.Refused -> return AssetCommandResult.Conflict(resolved.path)
        }
        return verify(txn, changes, forward)
    }

    /** Verifies, then applies; on a failed write restores the files already written. Call inside a write action. */
    fun apply(txn: AssetTransaction, forward: Boolean, beforeFirstWrite: () -> Boolean = { true }): AssetCommandResult {
        val changes = when (val resolved = patchesToBytes(txn)) {
            is Patches.Ready -> resolved.changes
            is Patches.Refused -> return AssetCommandResult.Conflict(resolved.path)
        }
        verify(txn, changes, forward)?.let { return it }
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
                for (c in changes) write(c.path, c.before, c.after, undo)
                for (d in txn.derived) writeDerived(d, forward, undo)
            } else {
                for (d in txn.derived.asReversed()) writeDerived(d, forward, undo)
                for (c in changes.asReversed()) write(c.path, c.after, c.before, undo)
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

    private fun verify(txn: AssetTransaction, changes: List<FileChange>, forward: Boolean): AssetCommandResult? {
        if (forward) {
            txn.createdDirs.firstOrNull { store.dirExists(it) }?.let { return AssetCommandResult.Collision(it) }
            for ((path, expected) in txn.expectedFiles) if (store.read(path) != expected) return AssetCommandResult.Conflict(path)
            for (c in changes) if (store.read(c.path) != c.before) return AssetCommandResult.Conflict(c.path)
            for (d in txn.derived) if (!holds(d, forward)) return AssetCommandResult.Conflict(d.path)
            return null
        }
        for (c in changes) if (store.read(c.path) != c.after) return AssetCommandResult.Conflict(c.path)
        for (d in txn.derived) if (!holds(d, forward)) return AssetCommandResult.Conflict(d.path)
        for (dir in txn.createdDirs) {
            val expected = (txn.changes.map { it.path } + txn.createdDirs).filter { it.substringBeforeLast('/', "") == dir }
                .map { it.substringAfterLast('/') }.toSet()
            if (store.children(dir).toSet() != expected) return AssetCommandResult.Conflict(dir)
        }
        txn.guard()?.let { return AssetCommandResult.Blocked(it) }
        return null
    }

    /**
     * Every [FileSnapshot.Patch] of [txn] applied over the file version it names, as ordinary byte changes; refused
     * with the path when one names a file that is not the version it was taken from.
     */
    private fun patchesToBytes(txn: AssetTransaction): Patches {
        if (txn.changes.none { it.before is FileSnapshot.Patch || it.after is FileSnapshot.Patch }) {
            return Patches.Ready(txn.changes)
        }
        val resolved = txn.changes.map { c ->
            val before = c.before
            val after = c.after
            if (before !is FileSnapshot.Patch && after !is FileSnapshot.Patch) return@map c
            // the file version both rectangles belong to: whichever side holds whole bytes, else the file itself
            val base = (before as? FileSnapshot.Bytes ?: after as? FileSnapshot.Bytes)?.toByteArray()
                ?: (store.read(c.path) as? FileSnapshot.Bytes)?.toByteArray()
                ?: return Patches.Refused(c.path)
            val resolvedBefore = patchOver(before, base) ?: return Patches.Refused(c.path)
            val resolvedAfter = patchOver(after, base) ?: return Patches.Refused(c.path)
            FileChange(c.path, resolvedBefore, resolvedAfter)
        }
        return Patches.Ready(resolved)
    }

    /** [side] with its rectangle written over [base] when it is a patch (and checked against the digest it names). */
    private fun patchOver(side: FileSnapshot, base: ByteArray): FileSnapshot? {
        if (side !is FileSnapshot.Patch) return side
        val bytes = side.patch.applyTo(base)
        return FileSnapshot.Bytes(bytes).takeIf { sha256Hex(bytes) == side.patch.fileSha256 }
    }

    /** Whether the file of a [DerivedFile] holds the digest the given direction starts from. */
    private fun holds(d: DerivedFile, forward: Boolean): Boolean {
        val expected = if (forward) d.beforeSha256 else d.afterSha256
        val current = store.read(d.path)
        return when (expected) {
            null -> current == FileSnapshot.Absent
            else -> current is FileSnapshot.Bytes && sha256Hex(current.toByteArray()) == expected
        }
    }

    /**
     * Writes a derived file in the direction [forward], or puts back what was there by rebuilding the other side; the
     * rebuilt bytes must hash to that side's digest, which is what makes the rebuild a check rather than a guess.
     */
    private fun writeDerived(d: DerivedFile, forward: Boolean, undo: ArrayDeque<Pair<String, () -> Unit>>) {
        // the inverse is registered first, like a write: it rebuilds (or removes) even when this fails half way
        undo.addFirst(d.path to { restoreDerived(d, forward) })
        val target = if (forward) d.afterSha256 else d.beforeSha256
        if (target == null) { // the other side is "no file": undoing a creation removes what it made
            if (store.read(d.path) != FileSnapshot.Absent) store.delete(d.path)
            return
        }
        val bytes = d.rebuild(forward)
        require(sha256Hex(bytes) == target) { "${d.path}: the rebuilt file hashes to something else" }
        store.write(d.path, bytes)
    }

    private fun restoreDerived(d: DerivedFile, forward: Boolean) {
        val target = if (forward) d.beforeSha256 else d.afterSha256
        if (target == null) {
            if (store.read(d.path) != FileSnapshot.Absent) store.delete(d.path)
            return
        }
        val bytes = d.rebuild(!forward)
        require(sha256Hex(bytes) == target) { "${d.path}: the rebuilt file hashes to something else" }
        store.write(d.path, bytes)
    }

    private fun write(path: String, from: FileSnapshot, to: FileSnapshot, undo: ArrayDeque<Pair<String, () -> Unit>>) {
        // the inverse is registered first: a write that fails half way is then restored too
        undo.addFirst(path to { restore(path, from) })
        when (to) {
            FileSnapshot.Absent -> store.delete(path)
            is FileSnapshot.Bytes -> store.write(path, to.toByteArray())
            is FileSnapshot.Patch -> error("$to: a mask patch reached the write unresolved")
        }
    }

    private fun restore(path: String, snapshot: FileSnapshot) {
        when (snapshot) {
            FileSnapshot.Absent -> if (store.read(path) != FileSnapshot.Absent) store.delete(path)
            is FileSnapshot.Bytes -> store.write(path, snapshot.toByteArray())
            is FileSnapshot.Patch -> error("$snapshot: a mask patch reached the write unresolved")
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

/** The outcome of turning a transaction's mask patches into whole-file bytes: usable changes, or the path that refused. */
private sealed interface Patches {
    data class Ready(val changes: List<FileChange>) : Patches

    data class Refused(val path: String) : Patches
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
     *
     * With [handBack], the undo action is given to it instead of being registered, and the VFS is not refreshed: a
     * caller that runs this inside its own command, with a later step that may fail, registers the action once every
     * step succeeded (or [revert]s), and calls [flush] after its command.
     */
    fun execute(
        txn: AssetTransaction,
        affected: () -> List<VirtualFile> = { emptyList() },
        isCancelled: () -> Boolean = { false },
        onDone: () -> Unit = {},
        handBack: ((AssetFileUndoAction) -> Unit)? = null,
    ): AssetCommandResult {
        var result: AssetCommandResult = AssetCommandResult.Cancelled
        WriteCommandAction.runWriteCommandAction(project, txn.name, null, {
            result = engine.apply(txn, forward = true) { !isCancelled() }
            if (result == AssetCommandResult.Done) {
                val action = AssetFileUndoAction(txn, engine, affected())
                if (handBack != null) handBack(action) else UndoManager.getInstance(project).undoableActionPerformed(action)
            }
        })
        // the VFS learns of the files after the command, so the platform records nothing for them itself
        if (handBack == null) store.flush(async = false)
        if (result == AssetCommandResult.Done) onDone()
        return result
    }

    /** Takes back a transaction [execute] applied whose undo action was handed back and not registered. UI thread. */
    fun revert(txn: AssetTransaction): AssetCommandResult {
        var result: AssetCommandResult = AssetCommandResult.Cancelled
        WriteCommandAction.runWriteCommandAction(project, txn.name, null, { result = engine.apply(txn, forward = false) })
        return result
    }

    /** Refreshes the VFS for the files written since the last flush; call outside any command. */
    fun flush() = store.flush(async = false)
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
