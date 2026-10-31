/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

/** The terrain [entityId] of the scene [file] that has a terrain brush on. */
data class BrushRequestTarget(val file: VirtualFile, val entityId: String)

fun interface BrushRequestListener {
    /** [target] is the terrain the brush is now on, or null when the brush is off. */
    fun changed(target: BrushRequestTarget?)
}

fun brushTopic(name: String): Topic<BrushRequestListener> = Topic.create(name, BrushRequestListener::class.java)

/**
 * Which terrain of the project has a brush on: the brush toggle in the Properties panel starts it, and it ends with the
 * toggle, a selection change or play, whichever the Scene view sees first. The views and the panel follow through [topic].
 * One at a time.
 */
abstract class BrushRequests(private val project: Project, private val topic: Topic<BrushRequestListener>) {
    @Volatile
    var current: BrushRequestTarget? = null
        private set

    fun start(file: VirtualFile, entityId: String) = set(BrushRequestTarget(file, entityId))

    /** Ends the brush on [file]; one of another file is left alone. */
    fun stop(file: VirtualFile? = null) {
        if (file == null || current?.file == file) set(null)
    }

    private fun set(target: BrushRequestTarget?) {
        if (current == target) return
        current = target
        if (!project.isDisposed) project.messageBus.syncPublisher(topic).changed(target)
    }
}
