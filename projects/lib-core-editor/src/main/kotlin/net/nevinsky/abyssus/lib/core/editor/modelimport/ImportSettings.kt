/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelimport

import net.nevinsky.abyssus.lib.gdx.assimp.UpAxis
import java.util.Locale
import kotlin.math.abs

/** A source file format the model import reads; [id] is how `source.json` names it. */
enum class SourceFormat(val id: String, val extension: String) {
    OBJ("OBJ", "obj"),
    FBX("FBX", "fbx"),
    THREE_DS("3DS", "3ds"),
    DAE("DAE", "dae"),
    GLTF("GLTF", "gltf"),
    GLB("GLB", "glb"),
}

/** The format of [fileName] by its extension; `null` for any other file, including a Blender `.blend`. */
fun sourceFormatOf(fileName: String): SourceFormat? {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return SourceFormat.entries.firstOrNull { it.extension == extension }
}

/** Whether [fileName] is a Blender file, which is refused with a hint to export glTF instead. */
fun isBlenderFile(fileName: String): Boolean = fileName.lowercase(Locale.ROOT).endsWith(".blend")

/** A source unit, in metres per unit; [id] is how the dialog and `source.json` name it. */
enum class LengthUnit(val id: String, val metres: Float) {
    M("m", 1f),
    CM("cm", 0.01f),
    MM("mm", 0.001f),
    IN("in", 0.0254f),
    FT("ft", 0.3048f),
}

/** The unit of [metres] per unit, or `null` when it is none of the offered units. */
fun lengthUnitOf(metres: Float?): LengthUnit? =
    metres?.let { m -> LengthUnit.entries.firstOrNull { abs(it.metres - m) <= it.metres * 1e-4f } }

/** How the imported model is sized. */
sealed interface FitSize {
    /** The chosen unit only. */
    data object Original : FitSize

    /** Scaled so its largest extent is [metres]. */
    data class LargestExtent(val metres: Double) : FitSize

    /** Scaled so its height (Y extent) is [metres]. */
    data class Height(val metres: Double) : FitSize
}

/** What the user chose: the asset [folderName], the source [unit] and [upAxis] (Y or Z), and the [fit]. */
data class ImportSettings(
    val folderName: String,
    val unit: LengthUnit,
    val upAxis: UpAxis,
    val fit: FitSize = FitSize.Original,
)

/** Why settings cannot be used for Create. */
enum class SettingsProblem {
    FOLDER_EMPTY,

    /** A path separator, a reserved name or a character a file system refuses. */
    FOLDER_INVALID,

    /** The project already has an asset folder of that name. */
    FOLDER_TAKEN,

    SIZE_NOT_POSITIVE,

    /** Only Y and Z are offered. */
    UP_AXIS_UNSUPPORTED,
}

/** The rules of [ImportSettings]: folder names, the default name, and validation. */
class ImportSettingsRules {
    /**
     * `model_` plus the file name without its extension, lower case, with every run of characters other than
     * letters, digits, `-` and `_` replaced by `_`; made unique among [taken] by `_2`, `_3`...
     */
    fun defaultFolderName(sourceFileName: String, taken: Set<String>): String {
        val stem = sourceFileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val clean = stem.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_')
        val base = "model_" + clean.ifEmpty { "model" }
        var name = base
        var n = 2
        while (isTaken(name, taken)) name = base + "_" + n++
        return name
    }

    /** Every problem of [settings]; empty when Create may run. [taken] holds the names of the project's asset folders. */
    fun problems(settings: ImportSettings, taken: Set<String>): List<SettingsProblem> {
        val problems = ArrayList<SettingsProblem>()
        folderProblem(settings.folderName, taken)?.let(problems::add)
        if (settings.upAxis == UpAxis.X) problems += SettingsProblem.UP_AXIS_UNSUPPORTED
        val size = when (val fit = settings.fit) {
            FitSize.Original -> null
            is FitSize.LargestExtent -> fit.metres
            is FitSize.Height -> fit.metres
        }
        if (size != null && !(size > 0.0 && size.isFinite())) problems += SettingsProblem.SIZE_NOT_POSITIVE
        return problems
    }

    fun folderProblem(name: String, taken: Set<String>): SettingsProblem? = when {
        name.isBlank() -> SettingsProblem.FOLDER_EMPTY
        !validFolderName(name) -> SettingsProblem.FOLDER_INVALID
        isTaken(name, taken) -> SettingsProblem.FOLDER_TAKEN
        else -> null
    }

    private fun validFolderName(name: String): Boolean {
        if (name == "." || name == ".." || name != name.trim() || name.endsWith('.')) return false
        if (name.any { it < ' ' || it in INVALID_FOLDER_CHARACTERS }) return false
        return name.substringBefore('.').uppercase(Locale.ROOT) !in RESERVED_FOLDER_NAMES
    }

    /** Case-insensitive, as macOS and Windows file systems compare names. */
    private fun isTaken(name: String, taken: Set<String>): Boolean = taken.any { it.equals(name, ignoreCase = true) }
}

private const val INVALID_FOLDER_CHARACTERS = "/\\<>:\"|?*"
private val RESERVED_FOLDER_NAMES = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }
