/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.filetype

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation

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
 */
fun editSceneJson(project: Project, file: VirtualFile, commandName: String, mutate: (JsonNode) -> Boolean): Boolean {
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return false
    val kind = documentKind(file) ?: return false
    val format = net.nevinsky.abyssus.assets.format.AbyssusDocumentFormat()
    val original = document.text
    val root = runCatchingKeepingCancellation { SceneJson.parse(original).also { format.requireSupported(it, kind) } }.getOrNull() ?: return false
    if (!mutate(root) || format.validate(root, kind) != null || document.text != original) return false
    val text = SceneJson.inStyleOf(original, root)
    if (text == original) return false
    WriteCommandAction.runWriteCommandAction(project, commandName, null, {
        document.setText(text)
        FileDocumentManager.getInstance().saveDocument(document)
    })
    if (!project.isDisposed) project.messageBus.syncPublisher(AbyssusSceneEdited.TOPIC).sceneEdited(file)
    return true
}
