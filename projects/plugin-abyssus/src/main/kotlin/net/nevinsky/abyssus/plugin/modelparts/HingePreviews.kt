/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.modelparts

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import net.nevinsky.abyssus.lib.core.editor.pick.HingePreview

/** Told that the hinge preview of the scene [file] changed: [preview] is null when it ended. */
fun interface HingePreviewListener {
    fun previewChanged(file: VirtualFile, preview: HingePreview?)

    companion object {
        @JvmField
        val TOPIC: Topic<HingePreviewListener> =
            Topic.create("Abyssus hinge preview", HingePreviewListener::class.java)
    }
}

/**
 * The channel from the Properties preview slider to the Scene views (OpenSpec `model-part-hinges`, "Deflection
 * preview"): the slider [set]s a preview for a scene, each open view of that scene draws it. Nothing here is written to
 * a file.
 */
@Service(Service.Level.PROJECT)
class HingePreviews(private val project: Project) {
    fun set(file: VirtualFile, preview: HingePreview?) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                project.messageBus.syncPublisher(HingePreviewListener.TOPIC).previewChanged(file, preview)
            }
        }
    }

    /** Calls [changed] for previews of [file] until [parent] is disposed. */
    fun listen(file: VirtualFile, parent: Disposable, changed: (HingePreview?) -> Unit) {
        project.messageBus.connect(parent).subscribe(
            HingePreviewListener.TOPIC,
            HingePreviewListener { scene, preview -> if (scene == file) changed(preview) },
        )
    }

    companion object {
        fun of(project: Project): HingePreviews = project.service()
    }
}
