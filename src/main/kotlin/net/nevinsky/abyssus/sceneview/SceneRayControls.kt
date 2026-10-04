/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

/**
 * Links the runtime Ray Tracing switch of the open Scene views to anything that wants to flip it, chiefly the Abyssus
 * Properties panel. Views register their [RayControl] by scene file; [request] applies a change to every open view of that
 * scene. With no view open, switching it on opens the scene's Scene View and applies the request as soon as that view
 * registers. Nothing is persisted, so the switch is per running view and never writes the scene. AWT thread only.
 */
@Service(Service.Level.PROJECT)
class SceneRayControls @JvmOverloads constructor(
    internal val project: Project,
    private val open: (Project, VirtualFile) -> Unit = { p, f -> openSceneView(p, f) },
) {
    private val controls = LinkedHashMap<VirtualFile, MutableList<RayControl>>()
    private val pending = LinkedHashSet<VirtualFile>()
    private val listeners = mutableListOf<Pair<VirtualFile, () -> Unit>>()

    /** Registers [control] as a view of [file] until [parent] (the view) is disposed. A request made earlier is applied now. */
    internal fun register(file: VirtualFile, control: RayControl, parent: Disposable) {
        controls.getOrPut(file) { mutableListOf() } += control
        control.addListener(parent) { changed(file) }
        Disposer.register(parent) {
            controls[file]?.let { list ->
                list -= control
                if (list.isEmpty()) controls.remove(file)
            }
            changed(file)
        }
        if (pending.remove(file)) control.setRequested(true)
        changed(file)
    }

    /** The mode of the most recently opened view of [file], or null when no view with ray tracing is open. */
    fun mode(file: VirtualFile): RayModeSnapshot? = controls[file]?.lastOrNull()?.mode

    /** True while an on request waits for the Scene View it opened to appear. */
    fun isPending(file: VirtualFile): Boolean = file in pending

    /** Switches ray tracing on or off for every open view of [file]; with none open, an on request opens one. */
    fun request(file: VirtualFile, enabled: Boolean) {
        val open = controls[file].orEmpty()
        if (open.isNotEmpty()) {
            open.toList().forEach { it.setRequested(enabled) }
        } else if (enabled) {
            pending += file
            changed(file)
            open(project, file)
        } else {
            pending -= file
            changed(file)
        }
    }

    /** Checks the GPU again in every open view of [file] that failed. */
    fun retry(file: VirtualFile) = controls[file].orEmpty().toList().forEach { it.retry() }

    /** Calls [listener] after the state for [file] changes (a view opens or closes, or its mode changes), until [parent] is disposed. */
    fun addListener(file: VirtualFile, parent: Disposable, listener: () -> Unit) {
        val entry = file to listener
        listeners += entry
        Disposer.register(parent) { listeners -= entry }
    }

    private fun changed(file: VirtualFile) = listeners.toList().filter { it.first == file }.forEach { it.second() }
}
