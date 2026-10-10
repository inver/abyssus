/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.assetfiles

import net.nevinsky.abyssus.lib.core.editor.foliage.MaskRect
import java.security.MessageDigest

/** The lower-case hex SHA-256 of [bytes]; the one digest both sides of a derived file or a mask patch are named by. */
internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * The bytes of one dirty rectangle of a mask file ([width] apart, z-major like the file itself), together with the
 * digest of the whole file version they belong to: [fileSha256] names the version whose texels in [rect] are [values].
 * A stroke keeps one of these per undo step instead of a full mask (2048² is 4 MB), and the engine checks both the file
 * it patches and the result it produces by that digest.
 */
class MaskPatch(
    val width: Int,
    val rect: MaskRect,
    val values: ByteArray,
    val fileSha256: String,
) {
    init {
        require(width > 0) { "a mask patch of width $width" }
        require(!rect.isEmpty) { "a mask patch of the empty rectangle" }
        require(rect.minX >= 0 && rect.minZ >= 0 && rect.maxX < width && rect.maxZ < width) {
            "a mask patch $rect outside a ${width}x$width mask"
        }
        require(values.size == rect.width * rect.height) {
            "a mask patch $rect holding ${values.size} bytes, expected ${rect.width * rect.height}"
        }
    }

    /** [base], the full bytes of the file version [fileSha256] names, with this rectangle written over it. */
    fun applyTo(base: ByteArray): ByteArray {
        val out = base.copyOf()
        var at = 0
        for (z in rect.minZ..rect.maxZ) {
            val row = z * width
            for (x in rect.minX..rect.maxX) {
                out[row + x] = values[at]
                at++
            }
        }
        return out
    }

    override fun equals(other: Any?) = other is MaskPatch && other.width == width && other.rect == rect &&
        other.fileSha256 == fileSha256 && other.values.contentEquals(values)

    override fun hashCode() = (31 * (31 * width + rect.hashCode()) + values.contentHashCode()) * 31 +
        fileSha256.hashCode()

    override fun toString() = "MaskPatch($rect of $width, ${values.size} bytes)"
}

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

    /** A rectangle of a mask file rather than the whole file; resolved to [Bytes] by the engine before it is written. */
    class Patch(val patch: MaskPatch) : FileSnapshot {
        override fun equals(other: Any?) = other is Patch && other.patch == patch
        override fun hashCode() = patch.hashCode()
        override fun toString() = patch.toString()
    }
}

/**
 * A file the operation builds itself instead of one the caller staged: a foliage bake, which generation is
 * deterministic enough to be rebuilt rather than kept (an 18 MB bake would not scale in the undo stack).
 * [beforeSha256] is the digest the file must have now (null: it must not exist), [afterSha256] the digest it must have
 * afterwards, and [rebuild] produces the bytes of one side, checked against that side's digest.
 */
data class DerivedFile(
    val path: String,
    val beforeSha256: String?,
    val afterSha256: String,
    val rebuild: (forward: Boolean) -> ByteArray,
)

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
 * - [derived]: files the operation builds itself, as digests and a rebuild rather than bytes (see [DerivedFile]).
 * - [guard]: asks, when an Undo of the operation is about to run, whether something now depends on what it made; a text
 *   is the reason to refuse.
 *
 * A [FileChange]'s snapshots may be [FileSnapshot.Patch] instead of [FileSnapshot.Bytes]: the engine then checks the
 * file by the patch's digest and writes the rectangle only, which is how a brush stroke keeps a small undo step.
 */
class AssetTransaction(
    val name: String,
    val changes: List<FileChange>,
    val expectedFiles: Map<String, FileSnapshot> = emptyMap(),
    val createdDirs: List<String> = emptyList(),
    val derived: List<DerivedFile> = emptyList(),
    val guard: () -> String? = { null },
) {
    init {
        require(changes.isNotEmpty() || createdDirs.isNotEmpty() || derived.isNotEmpty()) { "an empty transaction" }
        require(changes.map { it.path }.toSet().size == changes.size) { "a file is changed twice" }
        require(derived.map { it.path }.toSet().size == derived.size) { "a derived file is built twice" }
        require(derived.map { it.path }.toSet().intersect(changes.map { it.path }).isEmpty()) {
            "a file is both changed and derived"
        }
    }

    /** Every path the operation reads or writes, for undo's affected-file list. */
    val paths: List<String> get() = (changes.map { it.path } + expectedFiles.keys + derived.map { it.path }).distinct()
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
