/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import net.nevinsky.abyssus.dto.AssetReadResult
import net.nevinsky.abyssus.dto.ConfigFileReader
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.dto.ProjectReader
import net.nevinsky.abyssus.dto.SceneReader
import java.util.concurrent.ConcurrentHashMap

/**
 * The parsed asset files the Abyssus view shows, re-read when a file's [ConfigFileReader.stamp] changes. Lives with the
 * project; files that are deleted or moved are forgotten.
 */
@Service(Service.Level.PROJECT)
class AssetReadCache(
    val project: Project,
    sceneReader: ConfigFileReader<*>?,
    projectReader: ConfigFileReader<*>?,
) : Disposable {
    constructor(project: Project) : this(project, null, null)

    private val sceneReader by lazy { sceneReader ?: service<SceneReader>() }
    private val projectReader by lazy { projectReader ?: project.service<ProjectReader>() }

    private data class Entry(val stamp: Long, val result: AssetReadResult<*>)

    private val cache = ConcurrentHashMap<VirtualFile, Entry>()

    init {
        project.messageBus
            .connect(this)
            .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    for (e in events) {
                        if (e is VFileDeleteEvent || e is VFileMoveEvent) {
                            e.file?.let(cache::remove)
                        }
                    }
                }
            })
    }

    /** Null for a file no reader handles. */
    fun read(file: VirtualFile): AssetReadResult<*>? {
        val reader = readerFor(file.extension) ?: return null
        val stamp = reader.stamp(file)
        cache[file]?.takeIf { it.stamp == stamp }?.let { return it.result }
        return reader.read(file).also { cache[file] = Entry(stamp, it) }
    }

    private fun readerFor(extension: String?): ConfigFileReader<*>? = when (extension) {
        ProjectLayout.SCENE_EXTENSION -> sceneReader
        ProjectLayout.PROJECT_EXTENSION -> projectReader
        else -> null
    }

    override fun dispose() = cache.clear()

    companion object {
        fun of(project: Project): AssetReadCache = project.service()
    }
}
