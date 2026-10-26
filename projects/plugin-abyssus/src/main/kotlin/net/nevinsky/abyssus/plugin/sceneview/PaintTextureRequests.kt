/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

/** The terrain [entityId] of the scene [file] that has the Paint Texture brush on. */
data class PaintTextureRequestTarget(val file: VirtualFile, val entityId: String)

fun interface PaintTextureRequestListener {
    /** [target] is the terrain now being painted, or null when no painting is under way. */
    fun changed(target: PaintTextureRequestTarget?)

    companion object {
        @JvmField
        val TOPIC: Topic<PaintTextureRequestListener> = Topic.create("Paint Texture brush", PaintTextureRequestListener::class.java)
    }
}

/**
 * Which terrain of the project is being painted: the Paint Texture toggle in the Properties panel starts it, and it ends
 * with the toggle, a selection change or play, whichever the Scene view sees first. The views and the panel follow through
 * [PaintTextureRequestListener.TOPIC]. One at a time.
 */
@Service(Service.Level.PROJECT)
class PaintTextureRequests(private val project: Project) {
    @Volatile
    var current: PaintTextureRequestTarget? = null
        private set

    fun start(file: VirtualFile, entityId: String) = set(PaintTextureRequestTarget(file, entityId))

    /** Ends the painting of [file]; one of another file is left alone. */
    fun stop(file: VirtualFile? = null) {
        if (file == null || current?.file == file) set(null)
    }

    private fun set(target: PaintTextureRequestTarget?) {
        if (current == target) return
        current = target
        if (!project.isDisposed) project.messageBus.syncPublisher(PaintTextureRequestListener.TOPIC).changed(target)
    }
}
