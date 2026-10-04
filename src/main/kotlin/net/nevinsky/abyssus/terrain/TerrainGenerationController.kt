/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.terrain

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.assetfiles.AssetFileCommand
import net.nevinsky.abyssus.assetfiles.AssetTransaction
import net.nevinsky.abyssus.assetfiles.FileChange
import net.nevinsky.abyssus.assetfiles.FileSnapshot
import net.nevinsky.abyssus.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.assets.terrain.generation.MismatchReason
import net.nevinsky.abyssus.assets.terrain.generation.RecipeStatus
import net.nevinsky.abyssus.assets.terrain.generation.TerrainGenerationDraft
import net.nevinsky.abyssus.assets.terrain.generation.TerrainGenerationSettings
import net.nevinsky.abyssus.assets.terrain.generation.TerrainGenerator
import net.nevinsky.abyssus.assets.terrain.generation.TerrainHeightEncoder
import net.nevinsky.abyssus.assets.terrain.generation.TerrainRecipe
import net.nevinsky.abyssus.assets.terrain.generation.TerrainRecipeCodec
import net.nevinsky.abyssus.assets.terrain.generation.sha256Hex
import java.io.File
import kotlin.random.Random
import net.nevinsky.abyssus.filetype.documentDisplayMessage as displayMessage

/**
 * The logic behind the terrain regeneration controls for one existing terrain: the draft (settings, latest request,
 * preview, the source it must still match), background generation, and Apply. No Swing: the view reads its state and is
 * told through [onChange]. Everything but the generation itself runs on the UI thread ([ui]); the generation runs through
 * [background] and checks cancellation per row, so a newer request, Cancel, a changed selection or disposal stops it.
 */
class TerrainGenerationController(
    private val project: Project,
    private val folder: VirtualFile,
    initial: TerrainSource.Ready,
    private val generator: TerrainGenerator,
    private val encoder: TerrainHeightEncoder,
    private val recipes: TerrainRecipeCodec,
    background: (Runnable) -> Unit,
    ui: (Runnable) -> Unit,
    private val readCurrent: () -> TerrainSource,
    private val random: Random = Random.Default,
) {
    var onChange: () -> Unit = {}

    private var source: TerrainSource.Ready = initial
    var draft: TerrainGenerationDraft = newDraft(recipes.draftSettings(initial.recipe), initial)
        private set

    /** What the user should read under the controls: a reason, a status or an error; null when there is nothing to say. */
    var message: String? = null
        private set

    private val runner = TerrainPreviewRunner(generator, background, ui)
    private var disposed = false

    val resolution: Int get() = source.resolution
    val recipe: RecipeStatus get() = source.recipe
    val settings: TerrainGenerationSettings get() = draft.settings
    val generating: Boolean get() = draft.generating
    val folderName: String get() = folder.name

    private fun newDraft(settings: TerrainGenerationSettings, from: TerrainSource.Ready) =
        TerrainGenerationDraft(settings, from.size, from.resolution, from.source)

    /** Settings problems that stop a preview, localized; empty when it can start. */
    val problems: List<String> get() = settingsProblems(draft.settings)

    val canPreview: Boolean get() = !disposed && problems.isEmpty()

    /** True only for a finished preview of exactly the current settings, of the terrain as it still is. */
    val canApply: Boolean get() = !disposed && !draft.generating && draft.applicable(currentSnapshot()) != null

    private fun currentSnapshot() = (readCurrent() as? TerrainSource.Ready)?.source ?: source.source.copy(folderExists = false)

    fun update(settings: TerrainGenerationSettings) {
        draft.updateSettings(settings)
        runner.invalidate()
        message = null
        onChange()
    }

    fun randomizeSeed() {
        draft.randomizeSeed(random)
        runner.invalidate()
        message = null
        onChange()
    }

    /** Starts a background preview of the current settings; nothing is written. */
    fun preview() {
        if (!canPreview) return
        val fresh = readCurrent()
        if (fresh is TerrainSource.Unusable) {
            runner.invalidate()
            draft.sourceChanged()
            message = fresh.reason
            onChange()
            return
        }
        if (fresh is TerrainSource.Ready) sourceRead(fresh)
        // said before the start: an executor that runs the work at once reports its outcome from inside start()
        message = AbyssusBundle.message("terrainGenerating")
        val started = runner.start(draft) { outcome ->
            message = when (outcome) {
                PreviewOutcome.Ready -> AbyssusBundle.message("terrainPreviewReady")
                is PreviewOutcome.Failed -> AbyssusBundle.message("terrainPreviewFailed", outcome.message)
            }
            onChange()
        }
        if (started) {
            if (draft.generating) onChange()
        } else {
            message = null
        }
    }

    /** Discards the pending request and preview, and puts the settings back to what the terrain's recipe (or the defaults) say. */
    fun cancel() {
        runner.invalidate()
        draft.cancel()
        draft = newDraft(recipes.draftSettings(source.recipe), source)
        message = null
        onChange()
    }

    /** The panel re-read the terrain: a changed source (heights, recipe or metadata text) invalidates the draft's preview. */
    fun sourceRead(fresh: TerrainSource.Ready) {
        if (fresh.source == source.source) return
        runner.invalidate()
        val keep = draft.settings
        draft.cancel()
        source = fresh
        draft = newDraft(keep, fresh)
        message = AbyssusBundle.message("terrainSourceChanged")
        onChange()
    }

    fun dispose() {
        disposed = true
        runner.dispose()
        draft.cancel()
    }

    /**
     * Replaces the terrain's heights with the preview and writes the recipe beside it, as one undoable command that
     * leaves resolution, metadata and every other file alone. Does nothing without a matching preview.
     */
    fun apply(command: AssetFileCommand? = null, onDone: () -> Unit = {}): AssetCommandResult? {
        val current = readCurrent() as? TerrainSource.Ready
        if (current == null) {
            message = AbyssusBundle.message("terrainSourceChanged")
            onChange()
            return null
        }
        val preview = draft.applicable(current.source)
        if (preview == null) {
            message = AbyssusBundle.message(if (draft.preview == null) "terrainNeedsPreview" else "terrainSourceChanged")
            onChange()
            return null
        }
        val heights = encoder.encode(preview.heights)
        val recipe = TerrainRecipe(preview.settings, current.size, current.resolution, sha256Hex(heights))
        fun change(path: String, before: FileSnapshot, after: FileSnapshot) = if (before == after) null else FileChange(path, before, after)
        val changes = listOfNotNull(
            change(current.dataPath, current.heights, FileSnapshot.Bytes(heights)),
            change(current.recipePath, current.recipeBytes, FileSnapshot.Bytes(recipes.encode(recipe).toByteArray())),
        )
        if (changes.isEmpty()) { // the same heights and recipe are already there
            message = null
            onChange()
            return AssetCommandResult.Done
        }
        val txn = AssetTransaction(
            AbyssusBundle.message("commandRegenerateTerrain"),
            changes = changes,
            expectedFiles = mapOf(current.metaPath to current.metaBytes),
        )
        val projectDir = File(folder.path).parentFile.parentFile
        val runner = command ?: AssetFileCommand(project, LocalAssetFileStore(projectDir))
        val result = runner.execute(
            txn,
            affected = { listOfNotNull(folder.findChild("meta.json"), LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(folder.path, current.dataName))) },
            onDone = onDone,
        )
        message = when (result) {
            AssetCommandResult.Done -> null
            is AssetCommandResult.Conflict -> AbyssusBundle.message("terrainApplyConflict", result.path.substringAfterLast('/'))
            is AssetCommandResult.Collision -> AbyssusBundle.message("terrainApplyConflict", result.path.substringAfterLast('/'))
            is AssetCommandResult.Blocked -> result.reason
            AssetCommandResult.Cancelled -> AbyssusBundle.message("terrainApplyCancelled")
            is AssetCommandResult.Failed -> AbyssusBundle.message("terrainApplyFailed", result.cause.displayMessage()) +
                if (result.leftover.isEmpty()) "" else " " + AbyssusBundle.message("terrainApplyLeftover", result.leftover.joinToString())
        }
        if (result != AssetCommandResult.Done) {
            if (result is AssetCommandResult.Conflict || result is AssetCommandResult.Collision) draft.sourceChanged()
            onChange()
        }
        return result
    }

    /** The localized text for the recipe's status. */
    fun recipeText(): String = when (val r = source.recipe) {
        RecipeStatus.Missing -> AbyssusBundle.message("terrainRecipeMissing")
        is RecipeStatus.Matching -> AbyssusBundle.message("terrainRecipeMatching")
        is RecipeStatus.Malformed -> AbyssusBundle.message("terrainRecipeMalformed", r.reason)
        is RecipeStatus.Unsupported -> AbyssusBundle.message("terrainRecipeUnsupported", r.what)
        is RecipeStatus.Mismatch -> AbyssusBundle.message(
            "terrainRecipeMismatch",
            r.reasons.joinToString { AbyssusBundle.message("terrainMismatch" + it.name.lowercase().replaceFirstChar(Char::uppercase)) },
        )
    }
}
