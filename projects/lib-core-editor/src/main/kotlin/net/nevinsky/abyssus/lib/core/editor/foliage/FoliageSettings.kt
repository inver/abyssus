/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageColliderProblem
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageColliderRead
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageColliderReader
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerKind
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.layerKind
import net.nevinsky.abyssus.lib.core.editor.EditorMessages

/** Why a foliage value is refused; the panel shows the localized reason beside the field. */
enum class FoliageError {
    DENSITY, SCALE_RANGE, ALIGNMENT, SLOPE, HEIGHT_RANGE, WEIGHT, DUPLICATE_ID, NOT_A_MODEL,

    /** A layer's collider is not an object, has a member of the wrong type or an unknown shape. */
    COLLIDER_MALFORMED,

    /** An active collider size is not finite and positive. */
    COLLIDER_SIZE,

    /** A collider offset is not finite. */
    COLLIDER_OFFSET,

    /** A layer changed to DETAIL while it still has a collider. */
    COLLIDER_KIND,
}

/** A refused foliage value: the [layerId] it belongs to (null for the whole asset), the [field] key and why. */
class FoliageProblem(
    val layerId: Int?,
    val field: String,
    val error: FoliageError,
    val reason: String,
)

/**
 * What a foliage `meta.json` holds, read for editing: the [settings] (`additional` as the typed `FoliageMeta`) and
 * the [problems] that refuse them. The panel previews and writes only when [valid].
 */
class FoliageRead(val settings: FoliageMeta, val problems: List<FoliageProblem>) {
    val valid: Boolean get() = problems.isEmpty()

    /** The problems of one layer's field, for the reason shown beside it. */
    fun problemOf(layerId: Int, field: String): FoliageProblem? = problems.firstOrNull { it.layerId == layerId && it.field == field }
}

/**
 * Reads a foliage `meta.json` `additional` [JsonNode] into editable [FoliageMeta] settings and validates them.
 * Members that are not of the shape the format documents take their defaults; the validation then reports what must
 * be refused with a reason. [modelAssets] are the project's MODEL asset folders, the only ones a layer may name.
 */
class FoliageSettingsReader(
    private val messages: EditorMessages,
    private val colliders: FoliageColliderReader = FoliageColliderReader(),
    private val binding: net.nevinsky.abyssus.lib.core.editor.document.FoliageSettingsBinding = net.nevinsky.abyssus.lib.core.editor.document.FoliageSettingsBinding(),
) {

    fun read(node: JsonNode?, modelAssets: Set<String>): FoliageRead {
        val settings = binding.read(node ?: return FoliageRead(FoliageMeta(), emptyList()))
        return read(settings, modelAssets)
    }

    fun read(settings: FoliageMeta, modelAssets: Set<String>): FoliageRead {
        return FoliageRead(settings, problems(settings, modelAssets))
    }

    /** The reason shown for [error]; the text comes from `AbyssusEditorBundle`. */
    fun reason(error: FoliageError): String = messages.message("foliageError.${error.name}")

    private fun problems(settings: FoliageMeta, modelAssets: Set<String>): List<FoliageProblem> = buildList {
        val seen = mutableSetOf<Int>()
        fun problem(layerId: Int, field: String, error: FoliageError) =
            add(FoliageProblem(layerId, field, error, reason(error)))
        for (layer in settings.layers) {
            if (!seen.add(layer.id)) problem(layer.id, FOLIAGE_ID, FoliageError.DUPLICATE_ID)
            if (!(layer.density >= 0f)) problem(layer.id, FOLIAGE_DENSITY, FoliageError.DENSITY)
            if (layer.scale.min > layer.scale.max) problem(layer.id, FOLIAGE_SCALE, FoliageError.SCALE_RANGE)
            if (!(layer.alignToNormal in 0f..1f)) problem(layer.id, FOLIAGE_ALIGN_TO_NORMAL, FoliageError.ALIGNMENT)
            layer.maxSlope?.let { if (!(it in 0f..90f)) problem(layer.id, FOLIAGE_MAX_SLOPE, FoliageError.SLOPE) }
            val min = layer.minHeight
            val max = layer.maxHeight
            if (min != null && max != null && min > max) problem(layer.id, FOLIAGE_HEIGHT, FoliageError.HEIGHT_RANGE)
            if (layer.layerKind() == FoliageLayerKind.OBJECT) {
                val read = colliders.read(layer.collider)
                if (read is FoliageColliderRead.Invalid) problem(layer.id, FOLIAGE_COLLIDER, colliderError(read.problem))
            }
            for (model in layer.models) {
                if (!(model.weight > 0f)) problem(layer.id, "$FOLIAGE_MODELS[${model.asset}].$FOLIAGE_WEIGHT", FoliageError.WEIGHT)
                if (model.asset !in modelAssets) problem(layer.id, FOLIAGE_MODELS, FoliageError.NOT_A_MODEL)
            }
        }
    }

    private fun colliderError(problem: FoliageColliderProblem): FoliageError = when (problem) {
        FoliageColliderProblem.MALFORMED, FoliageColliderProblem.UNKNOWN_SHAPE -> FoliageError.COLLIDER_MALFORMED
        FoliageColliderProblem.SIZE -> FoliageError.COLLIDER_SIZE
        FoliageColliderProblem.OFFSET -> FoliageError.COLLIDER_OFFSET
    }

    /**
     * The problems an edit from [before] to [after] adds on top of [read]'s: a layer that was OBJECT and is now DETAIL
     * must give up its collider explicitly. A DETAIL layer that already had one (imported data) is left alone.
     */
    fun transitions(before: FoliageMeta, after: FoliageMeta): List<FoliageProblem> {
        val kinds = before.layers.associate { it.id to it.layerKind() }
        return after.layers
            .filter { it.collider != null && it.layerKind() == FoliageLayerKind.DETAIL && kinds[it.id] == FoliageLayerKind.OBJECT }
            .map { FoliageProblem(it.id, FOLIAGE_COLLIDER, FoliageError.COLLIDER_KIND, reason(FoliageError.COLLIDER_KIND)) }
    }




}

/** The documented keys and defaults of a foliage `additional` block. */
const val FOLIAGE_TERRAIN = "terrain"
const val FOLIAGE_DATA_FILE_KEY = "dataFile"
const val FOLIAGE_MASK_RESOLUTION = "maskResolution"
const val FOLIAGE_LAYERS = "layers"
const val FOLIAGE_ID = "id"
const val FOLIAGE_KIND = "kind"
const val FOLIAGE_MODELS = "models"
const val FOLIAGE_ASSET = "asset"
const val FOLIAGE_WEIGHT = "weight"
const val FOLIAGE_DENSITY = "density"
const val FOLIAGE_SCALE = "scale"
const val FOLIAGE_MIN = "min"
const val FOLIAGE_MAX = "max"
const val FOLIAGE_ALIGN_TO_NORMAL = "alignToNormal"
const val FOLIAGE_MIN_HEIGHT = "minHeight"
const val FOLIAGE_MAX_HEIGHT = "maxHeight"
const val FOLIAGE_HEIGHT = "height"
const val FOLIAGE_MAX_SLOPE = "maxSlope"
const val FOLIAGE_DRAW_DISTANCE = "drawDistance"
const val FOLIAGE_SEED = "seed"
const val FOLIAGE_COLLIDER = "collider"

const val FOLIAGE_MASK_RESOLUTION_DEFAULT = 256
/** The mask resolution New Foliage... starts with; the accepted range is [FOLIAGE_MASK_RESOLUTION_MIN] through [FOLIAGE_MASK_RESOLUTION_MAX]. */
const val FOLIAGE_MASK_RESOLUTION_DIALOG_DEFAULT = 512
const val FOLIAGE_MASK_RESOLUTION_MIN = 16
const val FOLIAGE_MASK_RESOLUTION_MAX = 2048
const val FOLIAGE_DENSITY_DEFAULT = 0.05f
const val FOLIAGE_SCALE_MIN_DEFAULT = 0.8f
const val FOLIAGE_SCALE_MAX_DEFAULT = 1.2f
const val FOLIAGE_ALIGN_DEFAULT = 0.5f
const val FOLIAGE_DRAW_DISTANCE_DEFAULT = 80f
