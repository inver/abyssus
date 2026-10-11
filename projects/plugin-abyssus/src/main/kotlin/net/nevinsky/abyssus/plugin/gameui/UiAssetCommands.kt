/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.editor.document.DocumentKind
import net.nevinsky.abyssus.lib.core.editor.document.DocumentTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.editor.terrain.checkFolderName
import net.nevinsky.abyssus.lib.core.editor.terrain.uniqueAssetUuid
import net.nevinsky.abyssus.lib.core.editor.ui.UiAssetTemplate
import net.nevinsky.abyssus.lib.core.editor.ui.UiEditError
import net.nevinsky.abyssus.lib.core.editor.ui.UiEditResult
import net.nevinsky.abyssus.lib.core.editor.ui.UiDocumentEdits
import net.nevinsky.abyssus.lib.core.editor.ui.UiRegistryEdits
import net.nevinsky.abyssus.lib.core.editor.ui.UiTextEditor
import net.nevinsky.abyssus.lib.core.editor.ui.UiTransform
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiIssue
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusCore
import net.nevinsky.abyssus.plugin.assetfiles.*
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.filetype.editSceneJson
import net.nevinsky.abyssus.plugin.projectView.selectAssetInAbyssusView
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import java.io.File
import java.util.UUID

/** What editing an existing UI `meta.json` did. Existing metadata is always written through `editSceneJson`. */
sealed interface UiEditOutcome {
    data class Edited(val result: UiEditResult) : UiEditOutcome
    data object Unchanged : UiEditOutcome

    /** Nothing was written; [issues] lists why the document is not editable UI, when that is the reason. */
    data class Rejected(val error: UiEditError, val subject: String? = null, val issues: List<UiIssue> = emptyList()) : UiEditOutcome

    /** The document changed since the gesture began ([baseText]), or since this call read it. */
    data object Stale : UiEditOutcome
    data object ReadOnly : UiEditOutcome
    data object Unavailable : UiEditOutcome
}

/**
 * Applies a pure UI edit to [file] (an asset `meta.json`) as one undoable command. [baseText] is the document text the
 * edit was computed from (a drag's start); when the document differs the edit is refused instead of overwriting a newer
 * change. The edit sees the same admission rules as [UiTextEditor]. EDT.
 */
fun editUiMeta(
    project: Project, file: VirtualFile, commandName: String = AbyssusBundle.message("commandEditUiAsset"),
    baseText: String? = null, editor: UiTextEditor = UiTextEditor(),
    op: (UiDocumentEdits, JsonNode) -> UiEditResult,
): UiEditOutcome {
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return UiEditOutcome.Unavailable
    if (!file.isWritable) return UiEditOutcome.ReadOnly
    if (baseText != null && document.text != baseText) return UiEditOutcome.Stale
    var transform: UiTransform = UiTransform.Unchanged
    val written = editSceneJson(project, file, commandName) { root ->
        transform = editor.transform(root, op)
        transform is UiTransform.Changed
    }
    return when (val t = transform) {
        is UiTransform.Rejected -> UiEditOutcome.Rejected(t.error, t.subject, t.issues)
        is UiTransform.Changed -> if (written) UiEditOutcome.Edited(t.result) else UiEditOutcome.Stale
        UiTransform.Unchanged -> UiEditOutcome.Unchanged
    }
}

/** Adds [name] to the project file's `uiAssetNames` as one undoable command. Returns whether the file was written. EDT. */
fun registerUiAsset(project: Project, abss: VirtualFile, name: String, registry: UiRegistryEdits = UiRegistryEdits()): Boolean =
    abss.isWritable && editSceneJson(project, abss, AbyssusBundle.message("commandRegisterUiAsset")) { registry.register(it, name) }

/** Removes [name] from `uiAssetNames`; the asset folder is untouched. Returns whether the file was written. EDT. */
fun unregisterUiAsset(project: Project, abss: VirtualFile, name: String, registry: UiRegistryEdits = UiRegistryEdits()): Boolean =
    abss.isWritable && editSceneJson(project, abss, AbyssusBundle.message("commandUnregisterUiAsset")) { registry.unregister(it, name) }

class StagedUiAsset(val name: String, val uuid: String, val transaction: AssetTransaction)

/**
 * Stages a new UI asset as one [AssetTransaction]: the asset folder with its `meta.json`, and the project file with the
 * asset registered. Everything is read here as immutable text and checked again when the command starts, so a concurrent
 * edit of the project file is a conflict, never an overwrite. The project file must be saved (the files are written
 * beside the editor's document). Writes belong to [AssetFileCommand]. EDT.
 */
class NewUiAssetFactory(
    private val processor: JsonProcessor,
    private val template: UiAssetTemplate = UiAssetTemplate(),
    private val registry: UiRegistryEdits = UiRegistryEdits(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val randomUuid: () -> UUID = UUID::randomUUID,
) {
    fun stage(abss: VirtualFile, rawName: String): StagedUiAsset = ReadAction.compute<StagedUiAsset, RuntimeException> {
        val projectDir = File(abss.parent.path)
        val assetsDir = File(projectDir, ASSETS_DIR)
        val name = rawName.trim()
        require(abss.isWritable) { AbyssusBundle.message("uiProjectReadOnly") }
        require(!FileDocumentManager.getInstance().isFileModified(abss)) { AbyssusBundle.message("uiProjectUnsaved") }
        val projectText = textOf(abss)
        AbyssusDocumentFormat().requireSupported(SceneJson().parseObject(projectText), DocumentKind.PROJECT)
        checkFolderName(assetsDir, name)?.let { throw IllegalArgumentException(AbyssusBundle.message("newTerrainNameError.$it")) }
        val uuid = uniqueAssetUuid(processor, assetsDir, randomUuid)
        val meta = template.create(uuid, clock())
        val registered = (DocumentTextEditor().edit(projectText, DocumentKind.PROJECT) { registry.register(it, name) } as? TextEditOutcome.Edited)?.text
        val base = "$ASSETS_DIR/$name"
        val changes = buildList {
            add(FileChange("$base/$META_FILE", FileSnapshot.Absent, FileSnapshot.Bytes(meta.toByteArray(Charsets.UTF_8))))
            if (registered != null) add(FileChange(abss.name, FileSnapshot.Bytes(projectText.toByteArray(abss.charset)), FileSnapshot.Bytes(registered.toByteArray(abss.charset))))
        }
        StagedUiAsset(name, uuid.toString(), AssetTransaction(
            AbyssusBundle.message("commandNewUiAsset"), changes,
            createdDirs = (if (assetsDir.isDirectory) emptyList() else listOf(ASSETS_DIR)) + base,
            guard = { AssetReferenceGuard(projectDir).blocker(name, uuid.toString()) ?: uiNavigationBlocker(projectDir, name) },
        ))
    }
}

/**
 * A sentence naming the first other UI asset whose actions navigate to the UI asset [name], or null. Saved files and
 * unsaved editor text are both read (unsaved wins), as in [AssetReferenceGuard]. EDT.
 */
fun uiNavigationBlocker(projectDir: File, name: String): String? {
    val decoder = UiCollectionDecoder()
    val assets = File(projectDir, ASSETS_DIR).listFiles { f -> f.isDirectory && f.name != name }.orEmpty().sortedBy { it.name }
    for (dir in assets) {
        val file = File(dir, META_FILE)
        val unsaved = LocalFileSystem.getInstance().findFileByIoFile(file)?.let { FileDocumentManager.getInstance().getCachedDocument(it)?.text }
        val text = unsaved ?: runCatchingKeepingCancellation { file.takeIf { it.isFile }?.readText() }.getOrNull() ?: continue
        val tree = runCatchingKeepingCancellation { SceneJson().parse(text) }.getOrNull() ?: continue
        val collection = (decoder.decode(tree) as? UiAdmission.Admitted)?.collection ?: continue
        val navigates = collection.screens.any { screen ->
            val stack = ArrayDeque(listOf(screen.root))
            var found = false
            while (stack.isNotEmpty() && !found) {
                val w = stack.removeLast()
                found = w.actions.values.any { it.assetName == name }
                stack.addAll(w.children)
            }
            found
        }
        if (navigates) return AbyssusBundle.message("uiAssetInUse", dir.name, name)
    }
    return null
}

private fun reportUiFailure(project: Project, reason: String) = Messages.showErrorDialog(
    project, AbyssusBundle.message("newUiAssetFailed", reason), AbyssusBundle.message("commandNewUiAsset"))

/** Stages, executes one asset transaction and selects the new asset only after it succeeded. EDT. */
fun createUiAsset(
    project: Project, abss: VirtualFile, name: String,
    report: (String) -> Unit = { reportUiFailure(project, it) },
    select: (String) -> Unit = { created -> ApplicationManager.getApplication().invokeLater(
        { if (!project.isDisposed) selectAssetInAbyssusView(project, abss, created) }, ModalityState.nonModal()) },
): AssetCommandResult? {
    val staged = runCatchingKeepingCancellation {
        NewUiAssetFactory(service<AbyssusCore>().documents.json).stage(abss, name)
    }.getOrElse { report(it.documentDisplayMessage()); return null }
    val result = AssetFileCommand(project, LocalAssetFileStore(File(abss.parent.path)))
        .execute(staged.transaction, onDone = { select(staged.name) })
    val reason = when (result) {
        AssetCommandResult.Done -> null
        is AssetCommandResult.Collision -> AbyssusBundle.message("newTerrainNameError.EXISTS")
        is AssetCommandResult.Conflict -> AbyssusBundle.message("uiAssetConflict", result.path)
        is AssetCommandResult.Blocked -> result.reason
        AssetCommandResult.Cancelled -> AbyssusBundle.message("uiAssetCancelled")
        is AssetCommandResult.Failed -> result.cause.documentDisplayMessage()
    }
    reason?.let(report)
    return result
}
