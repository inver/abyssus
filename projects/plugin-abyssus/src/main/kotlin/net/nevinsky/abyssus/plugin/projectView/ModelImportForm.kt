/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.assimp.UpAxis
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.AnimationInfo
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ApproximatedItem
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.FitSize
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.FrameOrigin
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ImportSettings
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ImportSettingsRules
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ImportTransform
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.LeftOutItem
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.LengthUnit
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ModelSource
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.SettingsProblem
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.TransformedModel
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.io.File
import java.util.Locale

/** Why Add to scene is off. */
enum class PlacementBlock { NO_SCENE_VIEW, PLAYING, UNREADABLE }

/** The scene Create may add the model to, by name, or why it cannot. */
sealed interface PlacementOption {
    data class Available(val sceneName: String) : PlacementOption
    data class Disabled(val reason: PlacementBlock) : PlacementOption
}

/** How the size is set: the unit only, or a fit of the largest extent or the height. */
enum class FitMode { ORIGINAL, LARGEST_EXTENT, HEIGHT }

/** Where a shown unit or up axis comes from, as the dialog marks it. */
enum class ValueSource { FILE, FORMAT, DEFAULT, CHOSEN }

/** Why Create is disabled. */
enum class FormProblem { SOURCE_LOADING, SOURCE_UNREADABLE, FOLDER_EMPTY, FOLDER_INVALID, FOLDER_TAKEN, SIZE_NOT_POSITIVE }

/**
 * The state of the Import Model dialog, without Swing: the settings, which values came from the file, the source and
 * what it leaves out, the animation to preview, the placement option and whether Create may run. The source is opened
 * once ([accept]); a settings change only recomputes the transform ([preview]). EDT, except [preview], which reads
 * only immutable state and may run on a pooled thread with a copy of the settings.
 */
class ModelImportForm(
    val sourceFile: File,
    private val takenFolders: Set<String>,
    val placement: PlacementOption,
    private val rules: ImportSettingsRules = ImportSettingsRules(),
    private val transform: ImportTransform = ImportTransform(),
) {
    var folderName: String = rules.defaultFolderName(sourceFile.name, takenFolders)
    var unit: LengthUnit = LengthUnit.M
    var upAxis: UpAxis = UpAxis.Y
    var fitMode: FitMode = FitMode.ORIGINAL
    var sizeText: String = "1.0"

    /** Whether Create also places the model; only possible while [placement] is available. */
    var addToScene: Boolean = placement is PlacementOption.Available
        set(value) {
            field = value && placement is PlacementOption.Available
        }

    /** The animation the preview plays; every animation is written either way. */
    var animation: String? = null

    var source: ModelSource? = null
        private set
    var sourceError: String? = null
        private set

    /** Takes the result of opening the source: pre-fills the unit and up axis it states, and picks the first animation. */
    fun accept(result: Result<ModelSource>) {
        result.onSuccess { opened ->
            source = opened
            sourceError = null
            unit = opened.frame.defaultUnit()
            upAxis = opened.frame.defaultUpAxis()
            animation = opened.animations.firstOrNull()?.name
        }.onFailure { e ->
            source = null
            sourceError = e.message ?: e.javaClass.simpleName
        }
    }

    /** Where the shown unit comes from: the file or its format while it is the stated one, else default or chosen. */
    val unitSource: ValueSource get() = valueSource(source?.frame?.unit, unit, LengthUnit.M)

    /** Where the shown up axis comes from; a stated X up counts as nothing stated. */
    val upSource: ValueSource get() = valueSource(source?.frame?.upAxis?.takeIf { it != UpAxis.X }, upAxis, UpAxis.Y)

    private fun <T> valueSource(stated: T?, shown: T, default: T): ValueSource = when {
        stated != null && stated == shown ->
            if (source?.frame?.origin == FrameOrigin.FORMAT) ValueSource.FORMAT else ValueSource.FILE
        stated == null && shown == default -> ValueSource.DEFAULT
        else -> ValueSource.CHOSEN
    }

    /** Whether the file states X up, which is not offered: Y is shown instead, with a note. */
    val statesXUp: Boolean get() = source?.frame?.upAxis == UpAxis.X

    val animations: List<AnimationInfo> get() = source?.animations.orEmpty()
    val leftOut: List<LeftOutItem> get() = source?.leftOut.orEmpty()
    val approximated: List<ApproximatedItem> get() = source?.approximated.orEmpty()

    /** The target size in metres, or null when the text is not a number above zero. */
    private fun size(): Double? = sizeText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }

    private fun fit(): FitSize? = when (fitMode) {
        FitMode.ORIGINAL -> FitSize.Original
        FitMode.LARGEST_EXTENT -> size()?.let { FitSize.LargestExtent(it) }
        FitMode.HEIGHT -> size()?.let { FitSize.Height(it) }
    }

    /** Every reason Create is disabled; empty when it may run. */
    fun problems(): List<FormProblem> {
        val problems = ArrayList<FormProblem>()
        when {
            sourceError != null -> problems += FormProblem.SOURCE_UNREADABLE
            source == null -> problems += FormProblem.SOURCE_LOADING
        }
        when (rules.folderProblem(folderName.trim(), takenFolders)) {
            SettingsProblem.FOLDER_EMPTY -> problems += FormProblem.FOLDER_EMPTY
            SettingsProblem.FOLDER_INVALID -> problems += FormProblem.FOLDER_INVALID
            SettingsProblem.FOLDER_TAKEN -> problems += FormProblem.FOLDER_TAKEN
            else -> {}
        }
        if (fit() == null) problems += FormProblem.SIZE_NOT_POSITIVE
        return problems
    }

    val createEnabled: Boolean get() = problems().isEmpty()

    /** The settings Create uses; null while a setting is invalid. */
    fun settings(): ImportSettings? {
        val fit = fit() ?: return null
        val settings = ImportSettings(folderName.trim(), unit, upAxis, fit)
        return settings.takeIf { rules.problems(it, takenFolders).isEmpty() }
    }

    /** The settings the preview shows: the folder name does not matter to it. */
    fun previewSettings(): ImportSettings? = fit()?.let { ImportSettings("preview", unit, upAxis, it) }

    /** The model as Create would write it with [settings]; reuses the opened source, never reads the file again. */
    fun preview(settings: ImportSettings? = previewSettings()): TransformedModel? {
        val opened = source ?: return null
        return transform.apply(opened.data, settings ?: return null)
    }

    /** The status line: the size in metres, and the number of animations and textures. */
    fun status(size: Vector3): String = AbyssusBundle.message(
        "importModelStatus",
        String.format(Locale.ROOT, "%.2f", size.x), String.format(Locale.ROOT, "%.2f", size.y), String.format(Locale.ROOT, "%.2f", size.z),
        animations.size, source?.textureCount() ?: 0,
    )

    /** Releases the source; the dialog calls it when it closes without Create. */
    fun close() {
        source?.close()
        source = null
    }
}

/** The dialog's text for a [FormProblem]. */
fun FormProblem.message(form: ModelImportForm): String = when (this) {
    FormProblem.SOURCE_LOADING -> AbyssusBundle.message("importModelLoading")
    FormProblem.SOURCE_UNREADABLE -> AbyssusBundle.message("importModelUnreadable", form.sourceError ?: "")
    FormProblem.FOLDER_EMPTY -> AbyssusBundle.message("newTerrainNameError.BLANK")
    FormProblem.FOLDER_INVALID -> AbyssusBundle.message("newTerrainNameError.INVALID_CHARACTER")
    FormProblem.FOLDER_TAKEN -> AbyssusBundle.message("newTerrainNameError.EXISTS")
    FormProblem.SIZE_NOT_POSITIVE -> AbyssusBundle.message("importModelSizeError")
}

/** The dialog's text for a disabled placement. */
fun PlacementBlock.message(): String = AbyssusBundle.message("importModelPlacement.$name")
