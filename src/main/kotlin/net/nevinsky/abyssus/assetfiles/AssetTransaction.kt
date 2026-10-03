/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assetfiles

/** The content of one file at one moment: absent, or an immutable copy of its bytes. Equal when the bytes are equal. */
sealed interface FileSnapshot {
    data object Absent : FileSnapshot

    class Bytes(bytes: ByteArray) : FileSnapshot {
        private val data = bytes.copyOf()

        val size: Int get() = data.size

        /** A copy; the snapshot itself never changes. */
        fun toByteArray(): ByteArray = data.copyOf()

        override fun equals(other: Any?) = other is Bytes && other.data.contentEquals(data)
        override fun hashCode() = data.contentHashCode()
        override fun toString() = "Bytes($size)"
    }
}

/** [path] (relative to the transaction's root, `/` separated) goes from [before] to [after]. */
class FileChange(val path: String, val before: FileSnapshot, val after: FileSnapshot) {
    init {
        require(before != after) { "$path: before and after are equal" }
    }
}

/**
 * Every file and folder one terrain operation touches, as immutable snapshots staged before anything is written.
 *
 * - [expectedFiles]: files that must hold exactly this content (or be absent) when the command starts, for state the
 *   operation depends on but does not write (the `meta.json` a preview was made from). [changes] are checked too.
 * - [createdDirs]: folders that must not exist and are made, parents first; removed again (deepest first) by Undo, and
 *   only when nothing else is in them.
 * - [guard]: asks, when an Undo of the operation is about to run, whether something now depends on what it made; a text
 *   is the reason to refuse.
 */
class AssetTransaction(
    val name: String,
    val changes: List<FileChange>,
    val expectedFiles: Map<String, FileSnapshot> = emptyMap(),
    val createdDirs: List<String> = emptyList(),
    val guard: () -> String? = { null },
) {
    init {
        require(changes.isNotEmpty() || createdDirs.isNotEmpty()) { "an empty transaction" }
        require(changes.map { it.path }.toSet().size == changes.size) { "a file is changed twice" }
    }

    /** Every path the operation reads or writes, for undo's affected-file list. */
    val paths: List<String> get() = (changes.map { it.path } + expectedFiles.keys).distinct()
}

/** The ways a command can end without writing, or after rolling back. */
sealed interface AssetCommandResult {
    data object Done : AssetCommandResult

    /** Something the operation depends on changed since the preview or snapshot: [path] differs from what was staged. */
    data class Conflict(val path: String) : AssetCommandResult

    /** [path] is a folder a new asset would use, and it exists already. */
    data class Collision(val path: String) : AssetCommandResult

    /** Undo is refused because something now depends on what the operation made; [reason] says what. */
    data class Blocked(val reason: String) : AssetCommandResult

    /** Cancelled before the first write; nothing changed. */
    data object Cancelled : AssetCommandResult

    /** A write failed; every file was put back ([rolledBack]) unless [leftover] lists what could not be restored. */
    data class Failed(val cause: Throwable, val leftover: List<String>) : AssetCommandResult {
        val rolledBack: Boolean get() = leftover.isEmpty()
    }
}
