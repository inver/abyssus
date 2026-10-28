/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.filetype

import net.nevinsky.abyssus.lib.core.editor.document.DocumentEdit
import net.nevinsky.abyssus.lib.core.editor.document.DocumentTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

/** Told after a plugin edit wrote [file] (a scene or project file), so views of it can refresh. */
fun interface AbyssusSceneEdited {
    fun sceneEdited(file: VirtualFile)

    companion object {
        @JvmField
        val TOPIC: Topic<AbyssusSceneEdited> = Topic.create("Abyssus scene edited", AbyssusSceneEdited::class.java)
    }
}

/**
 * Parses [file]'s document, lets [mutate] edit the tree (returning false to abort), and saves it in the file's own
 * style as one undoable command named [commandName]. Publishes [AbyssusSceneEdited.TOPIC] once the command is done.
 * Returns whether anything was written. This is the only way the plugin writes a scene or project file.
 *
 * The command is undone from the scene's own editor.
 */
fun editSceneJson(project: Project, file: VirtualFile, commandName: String, mutate: DocumentEdit<Boolean>): Boolean =
    editSceneJson(project, file, commandName, false, mutate)

/**
 * [editSceneJson] that, with [global], is also a project-level command: Undo and Redo reach it from any focus, such as
 * the Abyssus tree that started it, not only from the scene's editor.
 */
fun editSceneJson(project: Project, file: VirtualFile, commandName: String, global: Boolean, mutate: DocumentEdit<Boolean>): Boolean {
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return false
    val kind = documentKind(file) ?: return false
    val original = document.text
    // the text transform is the editing library's, shared with callers that have no IDE
    val text = (DocumentTextEditor().edit(original, kind, mutate) as? TextEditOutcome.Edited)?.text ?: return false
    if (document.text != original) {
        return false
    }
    WriteCommandAction.runWriteCommandAction(project, commandName, null, {
        document.setText(text)
        FileDocumentManager.getInstance().saveDocument(document)
        if (global) CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
    })
    if (!project.isDisposed) {
        project.messageBus.syncPublisher(AbyssusSceneEdited.TOPIC).sceneEdited(file)
    }
    return true
}
