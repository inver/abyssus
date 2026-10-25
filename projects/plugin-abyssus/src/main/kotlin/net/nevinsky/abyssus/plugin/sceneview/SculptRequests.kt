/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

/** The terrain [entityId] of the scene [file] that has the Sculpt Terrain brush on. */
data class SculptRequestTarget(val file: VirtualFile, val entityId: String)

fun interface SculptRequestListener {
    /** [target] is the terrain now being sculpted, or null when no sculpting is under way. */
    fun changed(target: SculptRequestTarget?)

    companion object {
        @JvmField
        val TOPIC: Topic<SculptRequestListener> = Topic.create("Sculpt Terrain brush", SculptRequestListener::class.java)
    }
}

/**
 * Which terrain of the project is being sculpted: the Sculpt Terrain toggle in the Properties panel starts it, and it ends
 * with the toggle, a selection change or play, whichever the Scene view sees first. The views and the panel follow through
 * [SculptRequestListener.TOPIC]. One at a time.
 */
@Service(Service.Level.PROJECT)
class SculptRequests(private val project: Project) {
    @Volatile
    var current: SculptRequestTarget? = null
        private set

    fun start(file: VirtualFile, entityId: String) = set(SculptRequestTarget(file, entityId))

    /** Ends the sculpting of [file]; one of another file is left alone. */
    fun stop(file: VirtualFile? = null) {
        if (file == null || current?.file == file) set(null)
    }

    private fun set(target: SculptRequestTarget?) {
        if (current == target) return
        current = target
        if (!project.isDisposed) project.messageBus.syncPublisher(SculptRequestListener.TOPIC).changed(target)
    }
}
