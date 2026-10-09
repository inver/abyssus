/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.gdx.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import java.util.concurrent.ConcurrentHashMap

/**
 * A scene's text read as the plugin needs it: [root] is the JSON tree (with its original number text) and [scene] the
 * bound DTO. Both are shared between callers: read them, never change them (an edit parses the document afresh).
 */
class ParsedScene(val root: JsonNode, val scene: SceneDto)

/**
 * The parsed text of scene files as the editors show it (unsaved text included), keyed by the document's modification
 * stamp, so asking again about an unchanged scene (the IDE does that on every action update) does not parse it again.
 * An unreadable text is remembered too, so it is not re-read either. Entries go when the file is deleted or moved.
 */
@Service(Service.Level.PROJECT)
class SceneDocumentCache(project: Project, private val parse: ((String) -> ParsedScene)?) : Disposable {
    constructor(project: Project) : this(project, null)

    private class Entry(val stamp: Long, val document: Boolean, val parsed: ParsedScene?)

    private val entries = ConcurrentHashMap<VirtualFile, Entry>()

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                for (e in events) if (e is VFileDeleteEvent || e is VFileMoveEvent) e.file?.let(entries::remove)
            }
        })
    }

    /** The scene in [file], or null when the file is gone or its text is not a readable scene. */
    fun read(file: VirtualFile): ParsedScene? {
        if (!file.isValid) return null
        val document = FileDocumentManager.getInstance().getCachedDocument(file)
        val stamp = document?.modificationStamp ?: file.modificationStamp
        entries[file]?.takeIf { it.stamp == stamp && it.document == (document != null) }?.let { return it.parsed }
        val text = document?.text ?: String(file.contentsToByteArray(), file.charset)
        val parsed = runCatchingKeepingCancellation { parse?.invoke(text) ?: parsedScene(service<SceneReader>(), text) }.getOrNull()
        entries[file] = Entry(stamp, document != null, parsed)
        return parsed
    }

    /** The files with an entry; for tests. */
    internal fun cachedFiles(): Set<VirtualFile> = entries.keys.toSet()

    override fun dispose() = entries.clear()

    companion object {
        /** Binds [text] with [reader] and parses it as a JSON tree; throws when either cannot read it. */
        fun parsedScene(reader: SceneReader, text: String): ParsedScene {
            val scene = reader.parse(text)
            return ParsedScene(SceneJson().parse(text), scene)
        }

        fun of(project: Project): SceneDocumentCache = project.service()
    }
}
