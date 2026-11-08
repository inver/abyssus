/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.filetype

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import net.nevinsky.abyssus.lib.core.dto.ProjectDto
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.editor.scene.SceneEdit
import net.nevinsky.abyssus.plugin.AbyssusPluginCtx

/** Told after a plugin edit wrote [file] (a scene or project file), so views of it can refresh. */
fun interface AbyssusSceneEdited {
    fun sceneEdited(file: VirtualFile)

    companion object {
        @JvmField
        val TOPIC: Topic<AbyssusSceneEdited> = Topic.create("Abyssus scene edited", AbyssusSceneEdited::class.java)
    }
}

/** Told after the parts of a model used by [file]'s scene were read in the background, so the tree can show them. */
fun interface AbyssusModelPartsRead {
    fun partsRead(file: VirtualFile)

    companion object {
        @JvmField
        val TOPIC: Topic<AbyssusModelPartsRead> =
            Topic.create("Abyssus model parts read", AbyssusModelPartsRead::class.java)
    }
}

/**
 * Parses [file]'s scene, lets [mutate] edit it (returning false to abort), and saves it as one undoable command named
 * [commandName]. Publishes [AbyssusSceneEdited.TOPIC] once the command is done. Returns whether anything was written.
 * Scene files are written only here; project and asset files go through [editProjectDocument] and [editTextDocument].
 *
 * The command is undone from the scene's own editor.
 */
fun editSceneDocument(project: Project, file: VirtualFile, commandName: String, mutate: SceneEdit): Boolean =
    editSceneDocument(project, file, commandName, false, mutate)

/**
 * [editSceneDocument] that, with [global], is also a project-level command: Undo and Redo reach it from any focus, such
 * as the Abyssus tree that started it, not only from the scene's editor.
 */
fun editSceneDocument(
    project: Project,
    file: VirtualFile,
    commandName: String,
    global: Boolean,
    mutate: SceneEdit,
): Boolean {
    val scenes = service<AbyssusPluginCtx>().documents.scenes
    return editTextDocument(project, file, commandName, global) { scenes.edit(it, mutate) }
}

/**
 * Edits the settings of the scene in [file] (name, ambient light, fog, sky): [change] returns the changed copy, or null
 * to abort. One undoable command, like [editSceneDocument].
 */
fun editSceneSettings(
    project: Project,
    file: VirtualFile,
    commandName: String,
    change: (SceneDto) -> SceneDto?,
): Boolean {
    val scenes = service<AbyssusPluginCtx>().documents.scenes
    return editTextDocument(project, file, commandName) { scenes.replace(it, change) }
}

/** Edits the `.abss` [file] as a [ProjectDto]: [change] returns the edited copy, or null to abort. */
fun editProjectDocument(
    project: Project,
    file: VirtualFile,
    commandName: String,
    change: (ProjectDto) -> ProjectDto?,
): Boolean {
    val projects = service<AbyssusPluginCtx>().documents.projects
    return editTextDocument(project, file, commandName, false) { projects.edit(it, change) }
}

/**
 * Replaces the text of [file] by what [transform] makes of it, as one undoable command named [commandName]; nothing is
 * written when it is not [TextEditOutcome.Edited] or the text changed meanwhile. Returns whether anything was written.
 */
fun editTextDocument(
    project: Project,
    file: VirtualFile,
    commandName: String,
    global: Boolean = false,
    transform: (String) -> TextEditOutcome,
): Boolean {
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return false
    val original = document.text
    val text = (transform(original) as? TextEditOutcome.Edited)?.text ?: return false
    if (document.text != original) {
        return false
    }
    WriteCommandAction.runWriteCommandAction(
        project,
        commandName,
        null,
        {
            document.setText(text)
            FileDocumentManager.getInstance().saveDocument(document)
            if (global) {
                CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
            }
        },
    )
    if (!project.isDisposed) {
        project.messageBus.syncPublisher(AbyssusSceneEdited.TOPIC).sceneEdited(file)
    }
    return true
}

/**
 * Changes an asset's `meta.json` and, with [scene] and [sceneEdit], the scene that goes with it as one undoable command
 * named [commandName]: a pivot edit moves the children bound to the node together with the meta, and one Undo takes both
 * back. Both new texts are made first; nothing is written when [metaTransform] or the scene edit refuses or a text
 * changed meanwhile. A scene that [sceneEdit] leaves unchanged is not written. The command is global, so Undo reaches it
 * from the view, the tree or Properties. Returns whether anything was written. The scene is still changed only through
 * the text transform of the scene editor, as [editSceneDocument] does.
 */
fun editAssetMetaAndScene(
    project: Project,
    meta: VirtualFile,
    scene: VirtualFile?,
    commandName: String,
    metaTransform: (String) -> TextEditOutcome,
    sceneEdit: SceneEdit?,
): Boolean {
    val documents = FileDocumentManager.getInstance()
    val metaDocument = documents.getDocument(meta) ?: return false
    val sceneDocument = scene?.let { documents.getDocument(it) ?: return false }
    val metaBefore = metaDocument.text
    val sceneBefore = sceneDocument?.text
    val metaText = (metaTransform(metaBefore) as? TextEditOutcome.Edited)?.text ?: return false
    var sceneText: String? = null
    if (sceneDocument != null && sceneEdit != null) {
        when (val outcome = service<AbyssusPluginCtx>().documents.scenes.edit(sceneBefore.orEmpty(), sceneEdit)) {
            is TextEditOutcome.Edited -> sceneText = outcome.text
            TextEditOutcome.Unchanged -> Unit
            is TextEditOutcome.Refused -> return false
        }
    }
    if (metaDocument.text != metaBefore || (sceneDocument != null && sceneDocument.text != sceneBefore)) {
        return false
    }
    WriteCommandAction.runWriteCommandAction(
        project,
        commandName,
        null,
        {
            metaDocument.setText(metaText)
            documents.saveDocument(metaDocument)
            if (sceneDocument != null && sceneText != null) {
                sceneDocument.setText(sceneText)
                documents.saveDocument(sceneDocument)
            }
            CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
        },
    )
    if (!project.isDisposed && scene != null && sceneText != null) {
        project.messageBus.syncPublisher(AbyssusSceneEdited.TOPIC).sceneEdited(scene)
    }
    return true
}
