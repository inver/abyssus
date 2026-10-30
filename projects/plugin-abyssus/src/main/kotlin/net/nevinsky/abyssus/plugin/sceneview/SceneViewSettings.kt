/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CopyOnWriteArrayList

/**
 * View settings of one IDE project, shared by all its scene views: today only whether they show the frame rate. They are
 * IDE preferences kept with the project, never fields of a `.abss`, `.scene` or asset file. Read, written and notified on the EDT.
 */
@Service(Service.Level.PROJECT)
class SceneViewSettings(private val project: Project) {
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    /** Whether each scene view of this project shows its frame rate; off until the user turns it on. */
    var showFps: Boolean
        get() = PropertiesComponent.getInstance(project).getBoolean(SHOW_FPS_KEY, false)
        set(value) {
            if (value == showFps) return
            // saved before anyone is told, so a listener that reads the setting sees the new value
            PropertiesComponent.getInstance(project).setValue(SHOW_FPS_KEY, value, false)
            listeners.forEach { it(value) }
        }

    /** Calls [listener] with the new value after each change, until [parent] is disposed. */
    fun addShowFpsListener(parent: Disposable, listener: (Boolean) -> Unit) {
        listeners += listener
        Disposer.register(parent) { listeners -= listener }
    }

    private companion object {
        const val SHOW_FPS_KEY = "net.nevinsky.abyssus.sceneView.showFps"
    }
}
