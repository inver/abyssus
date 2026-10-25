/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

/** The arrangement [entityId] of the scene [file] whose control points are being edited. */
data class PathEditTarget(val file: VirtualFile, val entityId: String)

fun interface PathEditListener {
    /** [target] is the arrangement now being edited, or null when no edit is under way. */
    fun changed(target: PathEditTarget?)

    companion object {
        @JvmField
        val TOPIC: Topic<PathEditListener> = Topic.create("Path arrangement control point edit", PathEditListener::class.java)
    }
}

/**
 * Which arrangement of the project has its control points edited: Edit in the Properties panel starts it, Apply or a
 * selection change ends it, and the Scene views and the panel follow through [PathEditListener.TOPIC]. One at a time.
 */
@Service(Service.Level.PROJECT)
class PathEditSession(private val project: Project) {
    @Volatile
    var current: PathEditTarget? = null
        private set

    fun start(file: VirtualFile, entityId: String) = set(PathEditTarget(file, entityId))

    /** Ends the edit of [file]'s arrangement; an edit of another file is left alone. */
    fun stop(file: VirtualFile? = null) {
        if (file == null || current?.file == file) set(null)
    }

    private fun set(target: PathEditTarget?) {
        if (current == target) return
        current = target
        if (!project.isDisposed) project.messageBus.syncPublisher(PathEditListener.TOPIC).changed(target)
    }
}
