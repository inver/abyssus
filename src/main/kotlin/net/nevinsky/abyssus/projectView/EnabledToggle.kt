package net.nevinsky.abyssus.projectView

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()

private fun JsonElement.child(key: String): JsonElement? = when (this) {
    is JsonObject -> get(key)
    is JsonArray -> key.toIntOrNull()?.let { if (it in 0 until size()) get(it) else null }
    else -> null
}

/**
 * Flips the `<x>Enabled` boolean that gates [entry] in the file it was read from.
 * Returns false when the entry has no toggle or the file no longer matches what was read.
 */
fun toggleEnabled(project: Project, entry: DtoEntry): Boolean {
    val file = entry.source ?: return false
    val toggle = entry.toggleName ?: return false
    val current = entry.enabled ?: return false
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return false
    val root = runCatching { JsonParser.parseString(document.text) }.getOrNull() ?: return false
    val target = entry.parentKeys.fold(root as JsonElement?) { e, k -> e?.child(k) } as? JsonObject ?: return false
    if (target.get(toggle)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.isBoolean != true) return false
    target.add(toggle, JsonPrimitive(!current))
    val text = gson.toJson(root)
    WriteCommandAction.runWriteCommandAction(project) {
        document.setText(text)
        FileDocumentManager.getInstance().saveDocument(document)
    }
    ProjectView.getInstance(project).getProjectViewPaneById(AbyssusProjectViewPane.ID)?.updateFromRoot(true)
    return true
}
