/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.terrain

import net.nevinsky.abyssus.plugin.assetfiles.assetResultMessage
import net.nevinsky.abyssus.plugin.assetfiles.assetFilesAffected
import net.nevinsky.abyssus.lib.core.editor.terrain.HeightmapImage
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainHeightEncoder
import net.nevinsky.abyssus.lib.core.editor.terrain.heightmapImage
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptBaseKind
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptFileSet
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptState
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.composeSculpt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileCommand
import net.nevinsky.abyssus.plugin.assetfiles.AssetTransaction
import net.nevinsky.abyssus.plugin.assetfiles.FileChange
import net.nevinsky.abyssus.plugin.assetfiles.FileSnapshot
import net.nevinsky.abyssus.plugin.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.lib.core.editor.terrain.RecipeStatus
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainGenerationDraft
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainGenerationSettings
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainGenerator
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainRecipe
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainRecipeCodec
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import java.io.File
import kotlin.random.Random

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
    private val sculpt: SculptServices,
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

    val canPreview: Boolean get() = !disposed && problems.isEmpty() && source.sculpt !is SculptState.Unsafe

    /** What the sculpt files say about this terrain. */
    val sculptState: SculptState get() = source.sculpt

    /** True to make the next Apply give the new noise alone and remove the sculpting; the default keeps it. */
    var discardSculpting: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            onChange()
        }

    /** The preview as the next Apply would leave the terrain: the new base, plus the kept sculpting. */
    val previewImage: HeightmapImage?
        get() {
            val preview = draft.preview ?: return null
            val state = source.sculpt as? SculptState.Sculpted
            if (state == null || discardSculpting) return preview.image
            val composed = composeSculpt(preview.heights, state.delta)
            return heightmapImage(composed, preview.resolution, preview.settings.minHeight, preview.settings.maxHeight)
        }

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
        if (!canPreview) {
            if (!disposed && source.sculpt is SculptState.Unsafe) {
                message = AbyssusBundle.message("terrainSculptBlocksRegenerate")
                onChange()
            }
            return
        }
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
        val files = checkNotNull(current.files) { "a ready terrain keeps the files it was read from" }
        val set = sculpt.planner.regenerate(files, current.sculpt, preview, discardSculpting)
        return commit("commandRegenerateTerrain", current, set, command, onDone)
    }

    /** Reset sculpting: the heights return to the base and the sculpt files go, as one undoable command. */
    fun resetSculpting(command: AssetFileCommand? = null, onDone: () -> Unit = {}): AssetCommandResult? {
        val current = readCurrent() as? TerrainSource.Ready ?: return null
        val files = current.files ?: return null
        if (current.sculpt !is SculptState.Sculpted) return null
        return commit("commandResetSculpting", current, sculpt.planner.reset(files, current.sculpt), command, onDone)
    }

    /** Forget sculpting: the sculpt files go and the current heights stay, as one undoable command. */
    fun forgetSculpting(command: AssetFileCommand? = null, onDone: () -> Unit = {}): AssetCommandResult? {
        val current = readCurrent() as? TerrainSource.Ready ?: return null
        val files = current.files ?: return null
        if (current.sculpt is SculptState.None) return null
        return commit("commandForgetSculpting", current, sculpt.planner.forget(files, current.sculpt), command, onDone)
    }

    private fun commit(
        commandKey: String,
        current: TerrainSource.Ready,
        set: SculptFileSet,
        command: AssetFileCommand?,
        onDone: () -> Unit,
    ): AssetCommandResult? {
        val projectDir = File(folder.path).parentFile.parentFile
        val store = LocalAssetFileStore(projectDir)
        val txn = SculptTransactions(store).plan(
            AbyssusBundle.message(commandKey), current.folderName, current.dataName, set,
            expected = mapOf(current.metaPath to current.metaBytes),
        )
        if (txn == null) { // the same files are already there
            message = null
            onChange()
            return AssetCommandResult.Done
        }
        val runner = command ?: AssetFileCommand(project, store)
        val result = runner.execute(
            txn,
            affected = { assetFilesAffected(folder, File(folder.path, current.dataName)) },
            onDone = onDone,
        )
        message = assetResultMessage(
            result, "terrainApplyCancelled",
            conflict = { AbyssusBundle.message("terrainApplyConflict", it.substringAfterLast('/')) },
            collision = { AbyssusBundle.message("terrainApplyConflict", it.substringAfterLast('/')) },
            failed = {
                AbyssusBundle.message("terrainApplyFailed", it.cause.documentDisplayMessage()) +
                    if (it.leftover.isEmpty()) "" else " " + AbyssusBundle.message("terrainApplyLeftover", it.leftover.joinToString())
            },
        )
        if (result != AssetCommandResult.Done) {
            if (result is AssetCommandResult.Conflict || result is AssetCommandResult.Collision) draft.sourceChanged()
            onChange()
        }
        return result
    }

    /** The localized text for the sculpt state; null for a terrain that is not sculpted. */
    fun sculptText(): String? = when (val s = source.sculpt) {
        SculptState.None -> null
        is SculptState.Sculpted -> AbyssusBundle.message(if (s.kind == SculptBaseKind.RECIPE) "terrainSculptedRecipe" else "terrainSculptedStored")
        is SculptState.Unsafe -> AbyssusBundle.message("terrainSculptUnsafe", AbyssusBundle.message("terrainSculptProblem." + s.problem.name) + if (s.detail.isEmpty()) "" else " (${s.detail})")
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
