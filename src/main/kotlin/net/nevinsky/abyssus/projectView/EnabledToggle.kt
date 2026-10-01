package net.nevinsky.abyssus.projectView

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.dto.DtoValue
import net.nevinsky.abyssus.dto.Json
import net.nevinsky.abyssus.filetype.SceneJson

private fun JsonNode.child(key: String): JsonNode? = when (this) {
    is ObjectNode -> get(key)
    is ArrayNode -> key.toIntOrNull()?.let { if (it in 0 until size()) get(it) else null }
    else -> null
}

private fun JsonNode.at(keys: List<String>): JsonNode? = keys.fold(this as JsonNode?) { e, k -> e?.child(k) }

/** Parses [file]'s document, lets [mutate] edit the tree (returning false to abort), and saves it undoably. */
private fun editJson(project: Project, file: VirtualFile, mutate: (JsonNode) -> Boolean): Boolean {
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return false
    val root = runCatching { Json.parse(document.text) }.getOrNull() ?: return false
    if (!mutate(root)) return false
    val text = SceneJson.inStyleOf(document.text, root)
    WriteCommandAction.runWriteCommandAction(project) {
        document.setText(text)
        FileDocumentManager.getInstance().saveDocument(document)
    }
    ProjectView.getInstance(project).getProjectViewPaneById(AbyssusProjectViewPane.ID)?.updateFromRoot(true)
    return true
}

/**
 * Flips the `<x>Enabled` boolean that gates [entry] in the file it was read from.
 * Returns false when the entry has no toggle or the file no longer matches what was read.
 */
fun toggleEnabled(project: Project, entry: DtoEntry): Boolean {
    val file = entry.source ?: return false
    val toggle = entry.toggleName ?: return false
    val current = entry.enabled ?: return false
    return editJson(project, file) { root ->
        val target = root.at(entry.parentKeys) as? ObjectNode ?: return@editJson false
        if (target.get(toggle)?.isBoolean != true) return@editJson false
        target.set<JsonNode>(toggle, BooleanNode.valueOf(!current))
        true
    }
}

/** The `.scene` file behind a scene entry listed under a project, or null for any other entry. */
fun sceneFileOf(entry: DtoEntry): VirtualFile? =
    ((entry.value as? DtoValue.Obj)?.source)?.takeIf { it.extension == "scene" && (entry.value as DtoValue.Obj).label != null }

fun sceneName(entry: DtoEntry): String? =
    ((entry.value as? DtoValue.Obj)?.properties?.firstOrNull { it.name == "name" }?.value as? DtoValue.Scalar)?.value as? String

/** Sets the top-level `name` of a scene file. */
fun renameScene(project: Project, file: VirtualFile, newName: String): Boolean =
    editJson(project, file) { root ->
        val scene = root as? ObjectNode ?: return@editJson false
        scene.set<JsonNode>("name", TextNode.valueOf(newName))
        true
    }
