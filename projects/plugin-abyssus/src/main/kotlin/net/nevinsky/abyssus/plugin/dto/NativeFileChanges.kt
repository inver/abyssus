/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.dto

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.messages.Topic

fun interface NativeChangeListener {
    fun after(events: List<VFileEvent>)
}

/** One VFS registration; message-bus connections retain each consumer's disposal and exception isolation. */
@Service(Service.Level.PROJECT)
class NativeFileChanges(private val project: Project) : Disposable {
    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val native = events.filter(::nativeCandidate)
                if (native.isNotEmpty()) project.messageBus.syncPublisher(NATIVE).after(native)
                project.messageBus.syncPublisher(RESOURCES).after(events)
            }
        })
    }

    /** Resource consumers retain their own folder predicate; native consumers still apply their project/folder rules. */
    fun subscribe(parent: Disposable, nativeOnly: Boolean = true, after: (List<VFileEvent>) -> Unit) {
        project.messageBus.connect(parent).subscribe(if (nativeOnly) NATIVE else RESOURCES, NativeChangeListener(after))
    }

    private fun nativeCandidate(event: VFileEvent): Boolean {
        if (event.file == null || event.file?.isDirectory == true || isNativeDocumentPath(event.path)) return true
        if (event is VFilePropertyChangeEvent && event.propertyName == VirtualFile.PROP_NAME) {
            val parent = event.path.substringBeforeLast('/', "")
            return listOf(event.oldValue, event.newValue).filterIsInstance<String>().any { isNativeDocumentPath("$parent/$it") }
        }
        return false
    }

    override fun dispose() = Unit

    private companion object {
        val NATIVE = Topic.create("Abyssus native file candidates", NativeChangeListener::class.java)
        val RESOURCES = Topic.create("Abyssus resource file changes", NativeChangeListener::class.java)
    }
}
