/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.foliage

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDataFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.foliageMaskFileName
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_MODELS
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageMetaEdits
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageProblem
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageRead
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageScatter
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageSettingsReader
import net.nevinsky.abyssus.lib.core.editor.foliage.ScatterOutcome
import net.nevinsky.abyssus.lib.core.editor.foliage.ScatterRefusal
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileCommand
import net.nevinsky.abyssus.plugin.assetfiles.AssetTransaction
import net.nevinsky.abyssus.plugin.assetfiles.DerivedFile
import net.nevinsky.abyssus.plugin.assetfiles.FileChange
import net.nevinsky.abyssus.plugin.assetfiles.FileSnapshot
import net.nevinsky.abyssus.plugin.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.plugin.assetfiles.sha256Hex
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import java.io.File

/**
 * The logic behind the foliage section of the properties panel for one existing foliage asset: the draft settings,
 * the preview (the copy counts, a refusal and the bake Apply would write), and Apply. No Swing: the view reads its
 * state and is told through [onChange]. The preview runs through [background] and is published through [ui], so a
 * newer edit, Cancel, a changed selection or disposal drops an older one, exactly like the terrain controls.
 *
 * The draft lives in the project's [FoliageDrafts], which every Scene view reads, so an edit reaches them without a
 * file write; Apply, Cancel and a selection change clear it.
 */
class FoliageController(
    private val project: Project,
    private val folder: VirtualFile,
    initial: FoliageSource.Ready,
    private val drafts: FoliageDrafts,
    private val json: JsonProcessor,
    private val background: (Runnable) -> Unit,
    private val ui: (Runnable) -> Unit,
    private val readCurrent: () -> FoliageSource,
) {
    var onChange: () -> Unit = {}

    private val fingerprints = FoliageFingerprint()
    private val scatter = FoliageScatter(fingerprints)
    private val dataFile = FoliageDataFile()
    private val edits = FoliageMetaEdits()
    private val settingsReader = FoliageSettingsReader(EditorBundle)

    private var source: FoliageSource.Ready = initial
    private var disposed = false

    /** The preview's token: a result of an older edit is dropped when this has moved past it. */
    private var generation = 0

    /** The settings being edited: the draft's when one is open, the stored asset's otherwise. */
    var settings: FoliageMeta = initial.settings
        private set

    /** The problems the edited settings have; empty while every value holds. */
    var problems: List<FoliageProblem> = initial.read.problems
        private set

    /** What the preview is for; null before one finished, or after it failed. */
    var outcome: ScatterOutcome? = null
        private set

    var generating: Boolean = false
        private set

    /** What the user should read under the controls; null when there is nothing to say. */
    var message: String? = null
        private set

    /** The settings of the finished preview, so Apply knows it is for exactly what is shown. */
    private var outcomeSettings: FoliageMeta? = null
    private var outcomeTerrain: TerrainData? = null
    private var outcomeMasks: Map<Int, ByteArray> = emptyMap()

    /** SHA-256 of the bake of the finished preview: what Apply writes `foliage.data` to. */
    private var outcomeDataSha256: String? = null

    /** [settings] diffed onto the text that was read: what Apply writes to `meta.json`. */
    private var editedText: String = initial.metaText

    init {
        generate()
    }

    val terrainName: String get() = source.settings.terrain

    val folderName: String get() = source.folderName

    /** The MODEL assets the model pickers offer, by folder name. */
    val modelChoices: List<String> get() = source.modelAssets.sorted()

    /** True when the bake on disk does not match the settings, masks or terrain as they are now. */
    val stale: Boolean get() = source.stale

    val refusal: ScatterRefusal? get() = outcome?.refusal

    /** The copies the preview holds of [layerId]; null before a preview finished or when it was refused. */
    fun countOf(layerId: Int): Int? = outcome?.counts?.get(layerId)

    /** The copies the preview holds in total, or the count the refusal was measured against. */
    val totalShown: Long get() = outcome?.let { if (it.refusal == null) it.total.toLong() else it.estimate } ?: 0L

    /** The problems of one layer's field, for the reason shown beside it. */
    fun problemOf(layerId: Int, field: String): FoliageProblem? =
        problems.firstOrNull { it.layerId == layerId && it.field == field }

    /** The problems of one layer's models, for the reason shown under them. */
    fun modelsProblemOf(layerId: Int): FoliageProblem? =
        problems.firstOrNull { it.layerId == layerId && (it.field == FOLIAGE_MODELS || it.field.startsWith("$FOLIAGE_MODELS[")) }

    /** True while a finished preview of exactly the current settings, over the files as they are, may be written. */
    val canApply: Boolean get() = !disposed && !generating && problems.isEmpty() && hasChanges &&
        outcome?.refusal == null && outcomeSettings == settings && outcomeDataSha256 != null

    private val hasChanges: Boolean get() = editedText != source.metaText || overriddenMasks().isNotEmpty() ||
        outcomeDataSha256 != source.dataSha256

    /**
     * Applies [transform] to the settings and previews the result without writing anything; the reason to refuse it,
     * or null when it was taken. A value that would add a problem the stored asset does not already have is refused
     * as it stands, so nothing invalid is previewed or written.
     */
    fun edit(transform: (FoliageMeta) -> FoliageMeta): String? {
        if (disposed) return null
        val candidate = transform(settings)
        if (candidate == settings) return null
        val text = when (val outcome = edits.edit(source.metaText, source.settings, candidate)) {
            is TextEditOutcome.Edited -> outcome.text
            TextEditOutcome.Unchanged -> source.metaText
            is TextEditOutcome.Refused -> return outcome.cause.documentDisplayMessage()
        }
        val read = settingsReader.read(additionalOf(text), source.modelAssets)
        val added = read.problems.filter { problem -> source.read.problems.none { it.sameAs(problem) } }
        if (added.isNotEmpty()) return added.first().reason
        commit(text, read)
        return null
    }

    /** Adds a layer with the next free id and the documented defaults. */
    fun addLayer(): String? {
        val model = modelChoices.firstOrNull() ?: return AbyssusBundle.message("foliageNoModels")
        return edit { settings ->
            settings.copy(layers = settings.layers + FoliageLayerMeta(
                id = (settings.layers.maxOfOrNull { it.id } ?: 0) + 1,
                models = listOf(net.nevinsky.abyssus.lib.core.assets.foliage.FoliageModelMeta(asset = model)),
            ))
        }
    }

    fun removeLayer(layerId: Int): String? = edit { settings ->
        settings.copy(layers = settings.layers.filterNot { it.id == layerId })
    }

    /** Moves the layer [layerId] one step up (-1) or down (+1) in the list; the last step does nothing. */
    fun moveLayer(layerId: Int, step: Int): String? = edit { settings ->
        val layers = settings.layers.toMutableList()
        val from = layers.indexOfFirst { it.id == layerId }
        val to = from + step
        if (from < 0 || to !in layers.indices) return@edit settings
        val moved = layers[from]
        layers[from] = layers[to]
        layers[to] = moved
        settings.copy(layers = layers)
    }

    /** Discards the preview and the draft, and puts the settings back to what the file holds. */
    fun cancel() {
        drafts.discard(source.folderName)
        adopt(source)
        message = null
        generate()
        onChange()
    }

    /** The panel re-read the asset: changed files invalidate the preview, an open draft keeps its settings. */
    fun sourceRead(fresh: FoliageSource.Ready) {
        if (source.sameAs(fresh)) return
        val keepDraft = drafts.of(fresh.folderName) != null
        source = fresh
        if (!keepDraft) {
            settings = fresh.settings
            problems = fresh.read.problems
        }
        editedText = editedText()
        message = null
        generate()
        onChange()
    }

    fun dispose() {
        disposed = true
        invalidate()
        drafts.discard(source.folderName)
    }

    /**
     * Writes the settings, the masks the draft changed and the bake the preview showed as one undoable command, whose
     * Undo stack is the one of this asset's `meta.json`. The bake is staged as a derived file (design decision 9): a
     * rebuild regenerates it from the inputs instead of storing up to 18 MB per step, which the deterministic scatter
     * makes exact. Nothing is written without a finished preview of the current settings over the files as they are
     * now; anything else is a reason in [message] and no write.
     */
    fun apply(command: AssetFileCommand? = null, onDone: () -> Unit = {}): AssetCommandResult? {
        val preview = outcome
        val after = outcomeDataSha256
        if (!canApply || preview == null || after == null) {
            message = AbyssusBundle.message("foliageNeedsPreview")
            onChange()
            return null
        }
        val fresh = readCurrent() as? FoliageSource.Ready ?: run {
            message = AbyssusBundle.message("foliageSourceChanged")
            invalidate()
            onChange()
            return null
        }
        val masks = fresh.masks + overriddenMasks()
        val expected = fingerprints.of(settings, fresh.terrain, masks)
        if (fresh.metaText != source.metaText || !expected.contentEquals(preview.bake.fingerprint)) {
            message = AbyssusBundle.message("foliageSourceChanged")
            sourceRead(fresh)
            return null
        }
        val base = "$ASSETS_DIR/${source.folderName}"
        val metaPath = "$base/$META_FILE"
        val changes = ArrayList<FileChange>()
        val metadataChanged = editedText != source.metaText
        for ((layerId, mask) in overriddenMasks()) {
            val name = foliageMaskFileName(layerId)
            val stored = source.masks[layerId].takeIf { File(folder.path, name).isFile }
            if (stored != null && stored.contentEquals(mask)) continue
            changes += FileChange(
                "$base/$name",
                stored?.let { FileSnapshot.Bytes(it) } ?: FileSnapshot.Absent,
                FileSnapshot.Bytes(mask),
            )
        }
        if (!metadataChanged && changes.isEmpty() && after == source.dataSha256) {
            message = null
            onChange()
            return AssetCommandResult.Done
        }
        val backup = runCatchingKeepingCancellation {
            if (source.stale && source.dataSha256 != null) project.service<FoliageUndoCache>().preserve(
                File(folder.path, source.settings.dataFileName()).toPath(), source.dataSha256!!,
            ) else null
        }.getOrElse {
            message = AbyssusBundle.message("foliageApplyFailed", it.documentDisplayMessage())
            onChange()
            return null
        }
        val rebuild = rebuildOf(backup) ?: run {
            backup?.close()
            message = AbyssusBundle.message("foliageApplyFailed", AbyssusBundle.message("foliageOldBakeUnreadable"))
            onChange()
            return null
        }
        val projectDir = File(folder.path).parentFile.parentFile
        val runner = command ?: AssetFileCommand(project, LocalAssetFileStore(projectDir))
        val txn = AssetTransaction(
            AbyssusBundle.message(if (!metadataChanged && changes.isEmpty()) "commandFoliageRebake" else "commandFoliageApply"),
            changes = changes,
            expectedFiles = if (metadataChanged) emptyMap() else mapOf(metaPath to FileSnapshot.Bytes(source.metaText.toByteArray())),
            derived = listOf(DerivedFile("$base/${settings.dataFileName()}", source.dataSha256, after!!, rebuild)),
        )
        var committed = false
        val result = try {
            applyFoliageFiles(project, runner, txn, folder.findChild(META_FILE)!!, source.metaText, editedText).also { result ->
                if (result == AssetCommandResult.Done) {
                committed = true
                drafts.discard(source.folderName)
                message = null
                onDone()
                }
            }
        } finally {
            if (!committed) backup?.close()
        }
        message = when (result) {
            AssetCommandResult.Done -> null
            is AssetCommandResult.Conflict -> AbyssusBundle.message("foliageApplyConflict", result.path.substringAfterLast('/'))
            is AssetCommandResult.Collision -> AbyssusBundle.message("foliageApplyConflict", result.path.substringAfterLast('/'))
            is AssetCommandResult.Blocked -> result.reason
            AssetCommandResult.Cancelled -> AbyssusBundle.message("foliageApplyCancelled")
            is AssetCommandResult.Failed -> AbyssusBundle.message("foliageApplyFailed", result.cause.documentDisplayMessage()) +
                if (result.leftover.isEmpty()) "" else " " + AbyssusBundle.message("foliageApplyLeftover", result.leftover.joinToString())
        }
        if (result != AssetCommandResult.Done) onChange()
        return result
    }

    /**
     * How the bake is written and put back: forward it is regenerated from the settings, masks and terrain the preview
     * was made from, backward it is either regenerated from the files as they were read (a bake that still matches
     * them, which is the usual case) or, when the stored bake was stale and so cannot be regenerated, the bytes of the
     * file that is about to be replaced. Returns null when the old file cannot be put back at all.
     */
    private fun rebuildOf(backup: FoliageUndoCache.Handle?): ((Boolean) -> ByteArray)? {
        val previewTerrain = outcomeTerrain ?: return null
        val previewSettings = outcomeSettings ?: return null
        val previewMasks = outcomeMasks
        val read = source
        // The closure retains only a disk handle for an unreconstructible prior bake.
        return { forward ->
            when {
                forward -> dataFile.write(scatter.scatter(previewTerrain, previewSettings, previewMasks).bake)
                backup != null -> backup.read()
                else -> dataFile.write(scatter.scatter(read.terrain, read.settings, read.masks).bake)
            }
        }
    }

    /** The masks the brush changed, which the panel writes with the settings. */
    private fun overriddenMasks(): Map<Int, ByteArray> = drafts.of(source.folderName)?.masks.orEmpty()

    /** Puts the settings, the problems and the meta text back to what the files hold. */
    private fun adopt(fresh: FoliageSource.Ready) {
        settings = fresh.settings
        problems = fresh.read.problems
        editedText = fresh.metaText
    }

    private fun commit(text: String, read: FoliageRead) {
        settings = read.settings
        problems = read.problems
        editedText = text
        message = null
        drafts.open(source.folderName, source.terrain, source.settings.maskResolution).editSettings(settings)
        generate()
        onChange()
    }

    /** The edited settings as [FoliageMetaEdits] writes them onto the text that was read. */
    private fun editedText(): String = when (val outcome = edits.edit(source.metaText, source.settings, settings)) {
        is TextEditOutcome.Edited -> outcome.text
        else -> source.metaText
    }

    private fun additionalOf(text: String): JsonNode? =
        runCatchingKeepingCancellation { json.readObject(text).get("additional") }.getOrNull()

    /** Counts the copies of the current settings off the EDT; the newest request wins. */
    private fun generate() {
        val token = ++generation
        val terrain = source.terrain
        val settings = settings
        val masks = source.masks + overriddenMasks()
        generating = true
        background(Runnable {
            val result = runCatchingKeepingCancellation { scatter.scatter(terrain, settings, masks) }
            ui(Runnable {
                if (disposed || token != generation) return@Runnable
                generating = false
                result.fold(
                    onSuccess = { counted ->
                        outcome = counted
                        outcomeSettings = settings
                        outcomeTerrain = terrain
                        outcomeMasks = masks
                        outcomeDataSha256 = runCatchingKeepingCancellation {
                            sha256Hex(dataFile.write(counted.bake))
                        }.getOrNull()
                        // the limit notice beside the counts explains a refusal; there is nothing else to say
                        message = null
                    },
                    onFailure = {
                        outcome = null
                        outcomeSettings = null
                        outcomeDataSha256 = null
                        message = AbyssusBundle.message("foliagePreviewFailed", it.documentDisplayMessage())
                    },
                )
                onChange()
            })
        })
    }

    /** Drops a running preview: its result is no longer for what is shown. */
    private fun invalidate() {
        generation++
        generating = false
        outcome = null
        outcomeSettings = null
        outcomeDataSha256 = null
    }

    private fun FoliageProblem.sameAs(other: FoliageProblem) =
        layerId == other.layerId && field == other.field && error == other.error
}
