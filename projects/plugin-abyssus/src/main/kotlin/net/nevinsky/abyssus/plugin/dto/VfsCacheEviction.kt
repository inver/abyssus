/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.dto

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent

/** Drops a file's entry from [cache] when the file is deleted or moved; the subscription ends with [parent]. */
internal fun evictOnDeleteOrMove(project: Project, parent: Disposable, cache: MutableMap<VirtualFile, *>) {
    project.messageBus.connect(parent).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun after(events: List<VFileEvent>) {
            for (e in events) {
                if (e is VFileDeleteEvent || e is VFileMoveEvent) {
                    e.file?.let(cache::remove)
                }
            }
        }
    })
}
