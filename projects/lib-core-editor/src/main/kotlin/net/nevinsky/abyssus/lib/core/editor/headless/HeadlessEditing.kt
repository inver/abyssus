/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.headless

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.format.FormatRejection
import net.nevinsky.abyssus.lib.core.format.UnsupportedDocumentFormat
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.components.ComponentEditor
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.editor.document.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.editor.document.DocumentKind
import net.nevinsky.abyssus.lib.core.editor.document.DocumentTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.editor.document.documentDisplayMessage
import net.nevinsky.abyssus.lib.core.editor.meta.AssetFieldDescriptions
import net.nevinsky.abyssus.lib.core.editor.meta.AssetMetaEditor
import net.nevinsky.abyssus.lib.core.editor.meta.EditOutcome
import net.nevinsky.abyssus.lib.core.editor.meta.FieldValue
import net.nevinsky.abyssus.lib.core.editor.meta.message
import net.nevinsky.abyssus.lib.core.editor.pick.SceneTransformWriter
import net.nevinsky.abyssus.lib.core.editor.pick.TransformEdit
import net.nevinsky.abyssus.lib.runtime.schema.ComponentSchema

/** Why a document or an edit was refused: [message] is the text the plugin shows, [rejection] the format problem if any. */
data class Refusal(val message: String, val rejection: FormatRejection? = null)

/** What a headless edit made of a document's text. */
sealed interface HeadlessEdit {
    /** The edited text: the input with only the edited values changed, key order and number text kept. */
    data class Edited(val text: String) : HeadlessEdit

    /** Nothing differs; the input text stays as it is. */
    data object Unchanged : HeadlessEdit

    /** Nothing was produced; [refusal] says why. */
    data class Refused(val refusal: Refusal) : HeadlessEdit
}

/**
 * Validating and editing native `.scene`, `.abss` and asset `meta.json` documents as text, with no IDE: the same
 * admission, mutations and printing the plugin's writes use, so the same input gives byte-identical output and the
 * same refusal. Scene components are edited under [schemas]; reasons are read from [messages].
 */
class HeadlessEditing(
    private val messages: EditorMessages = ResourceEditorMessages(),
    schemas: List<ComponentSchema> = emptyList(),
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
) {
    private val documents = DocumentTextEditor(format)
    private val components = ComponentEditor(messages, schemas)
    private val assetMeta = AssetMetaEditor(AssetFieldDescriptions(), format)

    /** Null when [text] is a supported native document of [kind], else why it is refused. */
    fun validate(text: String, kind: DocumentKind): Refusal? {
        val root = runCatchingKeepingCancellation { SceneJson().parse(text) }.getOrElse { return refusal(it) }
        return format.validate(root, kind)?.let { refusal(UnsupportedDocumentFormat(kind, it)) }
    }

    /** Applies the transform a gizmo drag writes ([TransformEdit]) to entity [entityId] of [sceneText]. */
    fun transform(sceneText: String, entityId: String, edit: TransformEdit): HeadlessEdit =
        edit(sceneText, DocumentKind.SCENE) { root -> SceneTransformWriter().apply(root, entityId, edit) }

    /**
     * Sets [field] of component [kind] of entity [entityId] in [sceneText] to [value], as the Properties panel does.
     * [assets] and [assetsByType] list the asset folders a render or schema asset field may name (null: unchecked).
     */
    fun setComponentField(
        sceneText: String,
        entityId: String,
        kind: String,
        field: String,
        value: String,
        assets: Set<String>? = null,
        assetsByType: Map<String, Set<String>>? = null,
    ): HeadlessEdit {
        var rejected: String? = null
        val outcome = edit(sceneText, DocumentKind.SCENE) { root ->
            when (val result = components.update(root, entityId, kind, field, value, assets, assetsByType)) {
                EditResult.Changed -> true
                EditResult.Unchanged -> false
                is EditResult.Rejected -> false.also { rejected = result.reason }
            }
        }
        return rejected?.let { HeadlessEdit.Refused(Refusal(it)) } ?: outcome
    }

    /**
     * Sets property [key] of the asset `meta.json` [metaText] to [value]; [expected] is the value the caller last read
     * (an edit made elsewhere since is refused as a conflict), as the Properties panel does.
     */
    fun setAssetProperty(metaText: String, key: String, expected: FieldValue, value: FieldValue): HeadlessEdit {
        var refusal: Refusal? = null
        val outcome = edit(metaText, DocumentKind.ASSET) { root ->
            when (val result = assetMeta.edit(root, key, expected, value)) {
                EditOutcome.Changed -> true
                EditOutcome.NoChange -> false
                is EditOutcome.Rejected -> false.also { refusal = Refusal(result.error.message(messages)) }
                is EditOutcome.Conflict -> false.also { refusal = Refusal(messages.message("assetFieldConflict")) }
            }
        }
        return refusal?.let { HeadlessEdit.Refused(it) } ?: outcome
    }

    private fun edit(text: String, kind: DocumentKind, mutate: (JsonNode) -> Boolean): HeadlessEdit =
        when (val outcome = documents.edit(text, kind, mutate)) {
            is TextEditOutcome.Edited -> HeadlessEdit.Edited(outcome.text)
            TextEditOutcome.Unchanged -> HeadlessEdit.Unchanged
            is TextEditOutcome.Refused -> HeadlessEdit.Refused(refusal(outcome.cause))
        }

    private fun refusal(cause: Throwable): Refusal =
        Refusal(cause.documentDisplayMessage(messages), (cause as? UnsupportedDocumentFormat)?.reason)
}
