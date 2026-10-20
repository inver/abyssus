/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView.scene

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.SceneEntry
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.projectView.AbyssusTreeAction
import net.nevinsky.abyssus.plugin.projectView.DropdownAction
import net.nevinsky.abyssus.plugin.projectView.DtoEntryNode
import java.util.*

class SceneDropdownAction : DropdownAction {
    override val actionIds = listOf("Abyssus.RenameScene", "Abyssus.Menu.Add")

    override fun appliesTo(node: Any?): Boolean {
        val entry = (node as? DtoEntryNode)?.value ?: return false
        return entry.value is SceneEntry && entry.source?.takeIf {
            it.isValid && it.extension == ProjectLayout.PROJECT_EXTENSION &&
                    runCatchingKeepingCancellation {
                        AbyssusDocumentFormat().validate(
                            SceneJson().parse(textOf(it)),
                            DocumentKind.PROJECT,
                        ) == null
                    }.getOrDefault(false)
        } != null
    }
}

class ScenesDropdownAction : DropdownAction {
    override val actionIds = listOf("Abyssus.NewScene")

    override fun appliesTo(node: Any?) = scenesFolderProjectFile(node) != null
}

/** The project file (`.abss`) a selected Scenes folder row belongs to, or null for any other node. */
internal fun scenesFolderProjectFile(node: Any?): VirtualFile? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    if (entry.value !is List<*> || entry.name != "scenes") {
        return null
    }
    return entry.source?.takeIf { it.isValid && it.extension == ProjectLayout.PROJECT_EXTENSION }
}

/** A problem with the scene file name [name] in [scenes] (the `scenes` folder, or null when it does not exist yet), or null. */
internal fun sceneNameProblem(scenes: VirtualFile?, name: String): String? = when {
    name.isBlank() -> AbyssusBundle.message("newSceneNameEmpty")
    name.any { it == '/' || it == '\\' } || name == "." || name == ".." -> AbyssusBundle.message("newSceneNameInvalid")
    scenes?.findChild("$name.${ProjectLayout.SCENE_EXTENSION}") != null -> AbyssusBundle.message(
        "newSceneNameTaken",
        name
    )

    else -> null
}

/** The text of a new, empty native scene. */
internal fun emptySceneText(name: String, id: String = UUID.randomUUID().toString()): String =
    """
    {
      "format": "abyssus",
      "formatVersion": 1,
      "id": ${quoted(id)},
      "name": ${quoted(name)},
      "ecs": {}
    }
    """.trimIndent() + "\n"

private fun quoted(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** Right-click on the Scenes folder: create an empty scene beside the others and open it in the Abyssus view. */
open class NewSceneAction : AbyssusTreeAction<VirtualFile>() {
    init {
        templatePresentation.text = AbyssusBundle.message("newSceneAction")
    }

    override fun targetOf(node: Any?): VirtualFile? = scenesFolderProjectFile(node)

    override fun isEnabled(e: AnActionEvent, target: VirtualFile) = target.isWritable

    override fun perform(project: Project, target: VirtualFile, e: AnActionEvent) {
        val dir = target.parent?.findChild(ProjectLayout.SCENES_DIR)
        val name = Messages.showInputDialog(
            project, AbyssusBundle.message("newScenePrompt"), AbyssusBundle.message("newSceneAction"), null, "",
            object : InputValidator {
                override fun checkInput(input: String?) = input != null && sceneNameProblem(dir, input.trim()) == null
                override fun canClose(input: String?) = checkInput(input)
            },
        )?.trim() ?: return
        val created = createScene(project, target, name) ?: return
        FileEditorManager.getInstance(project).openFile(created, true)
    }
}

/** Creates `scenes/<name>.scene` beside [abss] as one undoable command; null when it could not be written. EDT. */
fun createScene(project: Project, abss: VirtualFile, name: String): VirtualFile? {
    val root = abss.parent ?: return null
    return runCatchingKeepingCancellation {
        WriteCommandAction.writeCommandAction(project).withName(AbyssusBundle.message("newSceneAction"))
            .compute<VirtualFile, Exception> {
                val dir = root.findChild(ProjectLayout.SCENES_DIR) ?: root.createChildDirectory(
                    abss,
                    ProjectLayout.SCENES_DIR
                )
                sceneNameProblem(dir, name)?.let { throw IllegalStateException(it) }
                dir.createChildData(abss, "$name.${ProjectLayout.SCENE_EXTENSION}").also {
                    it.setBinaryContent(emptySceneText(name).toByteArray(Charsets.UTF_8))
                }
            }
    }.getOrElse {
        Messages.showErrorDialog(project, it.message.orEmpty(), AbyssusBundle.message("newSceneAction"))
        null
    }
}
