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
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.filetype.AbyssusProjectSettings
import net.nevinsky.abyssus.plugin.filetype.ProjectSettingsListener
import net.nevinsky.abyssus.plugin.physics.PhysicsComponentKinds

/** Told after the components the editor can modify changed. */
fun interface ComponentSchemasListener {
    fun schemasChanged()

    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Abyssus component schemas", ComponentSchemasListener::class.java)
    }
}

/**
 * The component editor of a project's scenes. It edits built-ins and, for enabled native projects, the fixed physics kinds. Game components remain opaque.
 */
@Service(Service.Level.PROJECT)
class ComponentSchemas(private val project: Project) : Disposable {
    private val editor = ComponentEditor(EditorBundle)
    private val physicsEditor = ComponentEditor(EditorBundle, PhysicsComponentKinds(EditorBundle).kinds)
    private val settings = project.service<AbyssusProjectSettings>()

    init {
        project.messageBus.connect(this).subscribe(ProjectSettingsListener.TOPIC, ProjectSettingsListener { _, _ -> reload() })
    }

    /** The component editor for [sceneFile]'s scene. */
    fun editorFor(sceneFile: VirtualFile): ComponentEditor =
        if (ProjectLayout.abssFor(sceneFile)?.let { settings.getSettings(it).physicsEnabled } == true) physicsEditor else editor

    /** Tells the listeners to read the components again. */
    fun reload() {
        if (!project.isDisposed) project.messageBus.syncPublisher(ComponentSchemasListener.TOPIC).schemasChanged()
    }

    override fun dispose() = Unit

    companion object {
        fun of(project: Project): ComponentSchemas = project.service()
    }
}
