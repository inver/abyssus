/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.schema

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import net.nevinsky.abyssus.lib.core.editor.components.ComponentEditor
import net.nevinsky.abyssus.plugin.EditorBundle

/** Told after the components the editor can modify changed. */
fun interface ComponentSchemasListener {
    fun schemasChanged()

    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Abyssus component schemas", ComponentSchemasListener::class.java)
    }
}

/**
 * The component editor of a project's scenes. It edits the built-in components only: game and physics components stay
 * as the scene file holds them until the editor has a way to describe them.
 */
@Service(Service.Level.PROJECT)
class ComponentSchemas(private val project: Project) : Disposable {
    private val editor = ComponentEditor(EditorBundle)

    /** The component editor for [sceneFile]'s scene. */
    @Suppress("UNUSED_PARAMETER")
    fun editorFor(sceneFile: VirtualFile): ComponentEditor = editor

    /** Tells the listeners to read the components again. */
    fun reload() {
        if (!project.isDisposed) project.messageBus.syncPublisher(ComponentSchemasListener.TOPIC).schemasChanged()
    }

    override fun dispose() = Unit

    companion object {
        fun of(project: Project): ComponentSchemas = project.service()
    }
}
