/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.headless

import net.nevinsky.abyssus.lib.core.editor.components.EnabledTarget
import net.nevinsky.abyssus.lib.core.editor.components.SetEnabledAction
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.format.FormatRejection
import net.nevinsky.abyssus.lib.core.format.UnsupportedDocumentFormat
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.components.ComponentEditor
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.editor.components.AssetEntities
import net.nevinsky.abyssus.lib.core.editor.components.EntityRemoval
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneParser
import net.nevinsky.abyssus.lib.core.editor.scene.SceneEdit
import net.nevinsky.abyssus.lib.core.editor.scene.SceneTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageComponentEdits
import net.nevinsky.abyssus.lib.core.editor.document.documentDisplayMessage
import net.nevinsky.abyssus.lib.core.editor.meta.AssetFieldDescriptions
import net.nevinsky.abyssus.lib.core.editor.meta.AssetMetaEditor
import net.nevinsky.abyssus.lib.core.editor.meta.EditOutcome
import net.nevinsky.abyssus.lib.core.editor.meta.AssetFieldValue
import net.nevinsky.abyssus.lib.core.editor.meta.message
import net.nevinsky.abyssus.lib.core.editor.pick.SceneTransformWriter
import net.nevinsky.abyssus.lib.core.editor.pick.TransformEdit

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
 * Validating and editing `.scene`, `.abss` and asset `meta.json` documents as text, with no IDE: the same admission,
 * mutations and printing the plugin's writes use, so the same input gives byte-identical output and the same refusal.
 * Scene components are edited by the built-in kinds; reasons are read from [messages].
 */
class HeadlessEditing(
    private val json: JsonProcessor,
    private val messages: EditorMessages = ResourceEditorMessages(),
    registry: ComponentRegistry = ComponentRegistry(),
) {
    private val scenes = SceneParser(json, registry)
    private val sceneEditor = SceneTextEditor(scenes, json)
    private val components = ComponentEditor(messages)
    private val assetMeta = AssetMetaEditor(AssetFieldDescriptions())
    private val foliageEdits = FoliageComponentEdits()
    private val enabledAction = SetEnabledAction()
    private val entityRemoval = EntityRemoval(messages)
    private val assetEntities = AssetEntities(messages)

    /** Null when [text] is a readable document of [kind], else why it is refused. */
    fun validate(text: String, kind: DocumentKind): Refusal? =
        runCatchingKeepingCancellation {
            if (kind == DocumentKind.SCENE) scenes.parse(text) else json.readObject(text)
        }.exceptionOrNull()?.let(::refusal)

    /** Applies the transform a gizmo drag writes ([TransformEdit]) to entity [entityId] of [sceneText]. */
    fun transform(sceneText: String, entityId: Long, edit: TransformEdit): HeadlessEdit =
        editScene(sceneText) { scene -> SceneTransformWriter().apply(scene, entityId, edit) }

    /**
     * Sets [field] of component [kind] of entity [entityId] in [sceneText] to [value], as the Properties panel does.
     * [assets] and [assetsByType] list the asset folders a render or schema asset field may name (null: unchecked).
     */
    fun setComponentField(
        sceneText: String,
        entityId: Long,
        kind: String,
        field: String,
        value: String,
        assets: Set<String>? = null,
        assetsByType: Map<String, Set<String>>? = null,
    ): HeadlessEdit {
        var rejected: String? = null
        val outcome = editScene(sceneText) { scene ->
            when (val result = components.update(scene, entityId, kind, field, value, assets, assetsByType)) {
                EditResult.Changed -> true
                EditResult.Unchanged -> false
                is EditResult.Rejected -> false.also { rejected = result.reason }
            }
        }
        return rejected?.let { HeadlessEdit.Refused(Refusal(it)) } ?: outcome
    }

    /**
     * Adds the asset [asset] to [sceneText] as a new entity at ([x], [y], [z]), as Add Asset does. A model with [meshId]
     * draws only that mesh; without it the whole model. [terrainSize] centres a terrain on the point.
     */
    fun placeAsset(
        sceneText: String,
        asset: BaseMeta,
        x: Float,
        y: Float,
        z: Float,
        meshId: String? = null,
        terrainSize: Float? = null,
    ): HeadlessEdit {
        var rejected: String? = null
        val outcome = editScene(sceneText) { scene ->
            when (val added = assetEntities.add(scene, asset, Vector3(x, y, z), terrainSize, meshId).result) {
                EditResult.Changed -> true
                EditResult.Unchanged -> false
                is EditResult.Rejected -> false.also { rejected = added.reason }
            }
        }
        return rejected?.let { HeadlessEdit.Refused(Refusal(it)) } ?: outcome
    }

    /**
     * Disables or enables an entity or one of its components in [sceneText], as the Properties check box and the tree
     * eye do. Unchanged when the target is missing, cannot be switched, or already has that state.
     */
    fun setEnabled(sceneText: String, target: EnabledTarget, enabled: Boolean): HeadlessEdit =
        editScene(sceneText) { scene -> enabledAction.apply(scene, target, enabled) }

    /** Gives the terrain entity [entityId] of [sceneText] a foliage component naming [foliage], as Add Foliage does. */
    fun setFoliage(sceneText: String, entityId: Long, foliage: String): HeadlessEdit =
        editScene(sceneText) { scene -> foliageEdits.attach(scene, entityId, foliage) }

    /**
     * Removes the entities [ids] from [sceneText]. Refused, with the reference named, when a remaining entity still
     * refers to one of them; ids the scene does not have are ignored.
     */
    fun removeEntities(sceneText: String, ids: Set<Long>): HeadlessEdit {
        var blocked: String? = null
        val outcome = editScene(sceneText) { scene ->
            val removal = foliageEdits.remove(scene, ids)
            blocked = removal.blockedBy
            removal.changed
        }
        return blocked?.let { HeadlessEdit.Refused(Refusal(it)) } ?: outcome
    }

    /**
     * Removes the single entity [entityId] from [sceneText], as the tree's Remove Entity does. Refused, with the
     * relationship named, when the entity is missing, still referred to, or an arrangement that owns copies.
     */
    fun removeEntity(sceneText: String, entityId: Long): HeadlessEdit {
        var rejected: String? = null
        val outcome = editScene(sceneText) { scene ->
            when (val result = entityRemoval.remove(scene, entityId)) {
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
    fun setAssetProperty(metaText: String, key: String, expected: AssetFieldValue, value: AssetFieldValue): HeadlessEdit {
        var refusal: Refusal? = null
        val root = runCatchingKeepingCancellation { json.readObject(metaText) }
            .getOrElse { return HeadlessEdit.Refused(refusal(it)) }
        val changed = when (val result = assetMeta.edit(root, key, expected, value)) {
            EditOutcome.Changed -> true
            EditOutcome.NoChange -> false
            is EditOutcome.Rejected -> false.also { refusal = Refusal(result.error.message(messages)) }
            is EditOutcome.Conflict -> false.also { refusal = Refusal(messages.message("assetFieldConflict")) }
        }
        refusal?.let { return HeadlessEdit.Refused(it) }
        if (!changed) return HeadlessEdit.Unchanged
        // asset documents are still edited as a tree (gate G6)
        val printed = json.pretty(root)
        return if (printed == metaText) HeadlessEdit.Unchanged else HeadlessEdit.Edited(printed)
    }

    private fun editScene(text: String, edit: SceneEdit): HeadlessEdit =
        when (val outcome = sceneEditor.edit(text, edit)) {
            is TextEditOutcome.Edited -> HeadlessEdit.Edited(outcome.text)
            TextEditOutcome.Unchanged -> HeadlessEdit.Unchanged
            is TextEditOutcome.Refused -> HeadlessEdit.Refused(refusal(outcome.cause))
        }

    private fun refusal(cause: Throwable): Refusal =
        Refusal(cause.documentDisplayMessage(messages), (cause as? UnsupportedDocumentFormat)?.reason)
}
