/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.ui.probeImageSize
import net.nevinsky.abyssus.lib.core.editor.ui.UiEditResult
import net.nevinsky.abyssus.lib.core.ui.model.uiImageWithinLimits
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.assetfiles.*
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.undo.DocumentSaveUndoableAction
import java.io.File

/** A resource file set staged for import into a UI asset: the new files as one transaction and the declaration to add. */
class StagedUiResource(
    val id: String, val kind: String, val path: String,
    val transaction: AssetTransaction,
    /** The `meta.json` text the import was staged from; the import is refused when the document differs at the start. */
    val metaBefore: String,
)

private const val MAX_IMPORT_BYTES = 32L * 1024 * 1024

/**
 * Stages importing an image or a text bitmap font into a UI asset. Source files are validated here (type, readability,
 * pixel limits, the font's pages) and copied under the asset folder with collision-safe names, so nothing existing is
 * overwritten and a font's files keep the names its `.fnt` refers to. The declaration is added to `meta.json` by
 * [importUiResource] through the lossless document edit, never by rewriting the file. EDT.
 */
class UiResourceImporter {
    fun stageImage(meta: VirtualFile, source: File, requestedId: String? = null): StagedUiResource {
        val (folder, existing, text) = context(meta)
        require(source.isFile && source.canRead()) { AbyssusBundle.message("uiImportNotAFile", source.name) }
        require(source.length() <= MAX_IMPORT_BYTES) { AbyssusBundle.message("uiImportTooLarge", source.name) }
        val size = probeImageSize(source)
        require(size != null && uiImageWithinLimits(size.first, size.second)) { AbyssusBundle.message("uiImportBadImage", source.name) }
        val name = uniqueName(folder, "images", safe(source.name))
        val path = "images/$name"
        val dirs = if (File(folder, "images").isDirectory) emptyList() else listOf("images")
        return StagedUiResource(uniqueId(existing, requestedId ?: name.substringBeforeLast('.')), "image", path,
            AssetTransaction(AbyssusBundle.message("uiCommandImportResource"),
                listOf(FileChange(path, FileSnapshot.Absent, FileSnapshot.Bytes(source.readBytes()))), createdDirs = dirs), text)
    }

    fun stageFont(meta: VirtualFile, fnt: File, requestedId: String? = null): StagedUiResource {
        val (folder, existing, text) = context(meta)
        require(fnt.isFile && fnt.canRead() && fnt.extension.equals("fnt", true)) { AbyssusBundle.message("uiImportBadFont", fnt.name) }
        require(fnt.length() <= MAX_IMPORT_BYTES) { AbyssusBundle.message("uiImportTooLarge", fnt.name) }
        val fntText = runCatchingKeepingCancellation { fnt.readText(Charsets.UTF_8) }.getOrNull()
        require(fntText != null && fntText.startsWith("info ")) { AbyssusBundle.message("uiImportBadFont", fnt.name) }
        val pages = Regex("""^page\s+.*\bfile="([^"]+)"""", RegexOption.MULTILINE).findAll(fntText).map { it.groupValues[1] }.toList()
        require(pages.isNotEmpty()) { AbyssusBundle.message("uiImportFontPage", "(none)") }
        val files = mutableListOf<Pair<String, ByteArray>>()
        for (page in pages) {
            // a page must sit in the font's own folder: its name is kept, so the .fnt keeps referring to it
            val ok = page == safe(page) && !page.contains('/') && !page.contains('\\') && page != ".." && page != "."
            val file = File(fnt.parentFile, page)
            val size = if (ok && file.isFile) probeImageSize(file) else null
            require(size != null && uiImageWithinLimits(size.first, size.second)) { AbyssusBundle.message("uiImportFontPage", page) }
            require(file.length() <= MAX_IMPORT_BYTES) { AbyssusBundle.message("uiImportTooLarge", page) }
            files += page to file.readBytes()
        }
        val folderName = uniqueName(folder, "fonts", safe(fnt.nameWithoutExtension))
        val base = "fonts/$folderName"
        val fntName = safe(fnt.name)
        val changes = (listOf(fntName to fnt.readBytes()) + files.filter { it.first != fntName }).distinctBy { it.first }
            .map { (n, bytes) -> FileChange("$base/$n", FileSnapshot.Absent, FileSnapshot.Bytes(bytes)) }
        val dirs = (if (File(folder, "fonts").isDirectory) emptyList() else listOf("fonts")) + base
        val path = "$base/$fntName"
        return StagedUiResource(uniqueId(existing, requestedId ?: fnt.nameWithoutExtension), "bitmapFont", path,
            AssetTransaction(AbyssusBundle.message("uiCommandImportResource"), changes, createdDirs = dirs), text)
    }

    private data class Context(val folder: File, val resourceIds: Set<String>, val text: String)

    private fun context(meta: VirtualFile): Context {
        require(!FileDocumentManager.getInstance().isFileModified(meta)) { AbyssusBundle.message("uiImportMetaUnsaved") }
        val text = textOf(meta)
        val ids = runCatchingKeepingCancellation {
            net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parse(text)["additional"]["ui"]["resources"]
                .mapNotNull { it["id"]?.textValue() }.toSet()
        }.getOrDefault(emptySet())
        return Context(File(meta.parent.path), ids, text)
    }

    /** A file name with only letters, digits, dots, dashes and underscores. */
    private fun safe(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').ifEmpty { "file" }

    private fun uniqueName(folder: File, subfolder: String, name: String): String {
        val dir = File(folder, subfolder)
        fun taken(n: String) = File(dir, n).exists()
        if (!taken(name)) return name
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
        var i = 2
        while (taken("$stem-$i$ext")) i++
        return "$stem-$i$ext"
    }

    private fun uniqueId(existing: Set<String>, base: String): String {
        val clean = base.ifBlank { "resource" }
        if (clean !in existing) return clean
        var i = 2
        while ("$clean-$i" in existing) i++
        return "$clean-$i"
    }
}

/**
 * Runs a staged import as one undoable command: the files first, then the `resources` declaration through
 * `editUiMeta`, with the document saved around Undo and Redo. If the declaration cannot be written the files are taken
 * back. Refused as a conflict when `meta.json` is no longer the text the import was staged from. EDT.
 */
fun importUiResource(project: Project, meta: VirtualFile, staged: StagedUiResource, store: AssetFileStore = LocalAssetFileStore(File(meta.parent.path))): AssetCommandResult {
    val command = AssetFileCommand(project, store)
    var result: AssetCommandResult = AssetCommandResult.Cancelled
    CommandProcessor.getInstance().executeCommand(project, {
        if (textOf(meta) != staged.metaBefore) { result = AssetCommandResult.Conflict(meta.path); return@executeCommand }
        var undo: AssetFileUndoAction? = null
        result = command.execute(staged.transaction, affected = { listOf(meta) }, handBack = { undo = it })
        if (result != AssetCommandResult.Done) return@executeCommand
        val manager = UndoManager.getInstance(project)
        manager.undoableActionPerformed(DocumentSaveUndoableAction(meta, onUndo = true))
        val edited = editUiMeta(project, meta, staged.transaction.name, baseText = staged.metaBefore) { e, m -> e.addResource(m, staged.id, staged.kind, staged.path) }
        if (edited !is UiEditOutcome.Edited || edited.result !is UiEditResult.Added) {
            result = command.revert(staged.transaction).let { if (it == AssetCommandResult.Done) AssetCommandResult.Conflict(meta.path) else it }
            return@executeCommand
        }
        manager.undoableActionPerformed(undo!!)
        manager.undoableActionPerformed(DocumentSaveUndoableAction(meta, onUndo = false))
    }, staged.transaction.name, null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION)
    command.flush()
    return result
}
