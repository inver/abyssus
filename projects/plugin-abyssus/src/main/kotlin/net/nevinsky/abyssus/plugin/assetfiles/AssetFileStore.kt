/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.assetfiles

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.nio.file.Files

/**
 * The files an [AssetTransaction] is applied to. Paths are relative to the store's root and use `/`. Every method runs
 * on the UI thread; the mutating ones inside a write action. A seam so tests can inject failures between writes.
 */
interface AssetFileStore {
    /** What the file holds now, as the IDE sees it (unsaved text included for a file open in an editor). */
    fun read(path: String): FileSnapshot

    fun dirExists(path: String): Boolean

    /** The names directly inside the folder [path]; empty for an absent folder. */
    fun children(path: String): List<String>

    fun write(path: String, bytes: ByteArray)

    fun delete(path: String)

    fun makeDir(path: String)

    /** Removes the folder [path]; the caller has checked it holds nothing else. */
    fun removeDir(path: String)

    /**
     * Lets the rest of the IDE see what was changed since the last call (the VFS is refreshed). Called after the write
     * command, never inside it; [async] when the caller cannot refresh synchronously (an Undo in progress).
     */
    fun flush(async: Boolean) {}
}

/**
 * An [AssetFileStore] over a project folder on the local disk. Files are changed with `java.io` and the VFS is refreshed
 * afterwards by [flush], outside the write command. This is deliberate: a change made through the VFS API, or a file
 * created by a refresh inside a command, is recorded by the platform's own file undo (through local history) and that
 * record would fight [AssetFileUndoAction] over the same files, or make the command not undoable at all. Unsaved text
 * of a file open in an editor wins when reading.
 */
class LocalAssetFileStore(private val root: File) : AssetFileStore {
    private val dirty = LinkedHashSet<File>()

    private fun io(path: String) = if (path.isEmpty()) root else File(root, path)

    private fun touched(file: File) {
        dirty += file
        file.parentFile?.let { dirty += it }
    }

    override fun read(path: String): FileSnapshot {
        val file = io(path)
        val unsaved = LocalFileSystem.getInstance().findFileByIoFile(file)?.let { vf ->
            val manager = FileDocumentManager.getInstance()
            manager.getCachedDocument(vf)?.takeIf { manager.isDocumentUnsaved(it) }?.text?.toByteArray(vf.charset)
        }
        if (unsaved != null) return FileSnapshot.Bytes(unsaved)
        return if (file.isFile) FileSnapshot.Bytes(file.readBytes()) else FileSnapshot.Absent
    }

    override fun dirExists(path: String) = io(path).isDirectory

    override fun children(path: String): List<String> = io(path).list()?.toList().orEmpty()

    override fun write(path: String, bytes: ByteArray) {
        val file = io(path)
        touched(file)
        file.writeBytes(bytes)
    }

    override fun delete(path: String) {
        val file = io(path)
        touched(file)
        Files.deleteIfExists(file.toPath())
    }

    override fun makeDir(path: String) {
        val dir = io(path)
        touched(dir)
        Files.createDirectory(dir.toPath())
    }

    override fun removeDir(path: String) {
        val dir = io(path)
        touched(dir)
        Files.delete(dir.toPath()) // fails when something is still inside
    }

    override fun flush(async: Boolean) {
        if (dirty.isEmpty()) return
        val files = dirty.toList()
        dirty.clear()
        LocalFileSystem.getInstance().refreshIoFiles(files, async, false, null)
    }
}
