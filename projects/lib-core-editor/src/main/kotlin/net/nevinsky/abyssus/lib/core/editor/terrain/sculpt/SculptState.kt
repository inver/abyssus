/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainGenerator
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainRecipeCodec
import net.nevinsky.abyssus.lib.core.editor.terrain.sha256Hex

/** The files beside a terrain's `terrain.data` that [SculptStateReader] looks at; null where a file does not exist. */
class SculptFiles(
    val size: Int,
    val resolution: Int,
    val terrainBytes: ByteArray,
    val recipeText: String?,
    val manifestText: String?,
    val deltaBytes: ByteArray?,
    val baseBytes: ByteArray?,
)

/** Why a sculpt state cannot be trusted. */
enum class SculptProblem {
    MANIFEST_MALFORMED,
    MANIFEST_UNSUPPORTED,
    RESOLUTION,
    DELTA_MISSING,
    DELTA_MISMATCH,
    COMPOSED_MISMATCH,
    BASE_MISSING,
    BASE_MISMATCH,
    RECIPE_UNUSABLE,
}

/** What a terrain's sculpting looks like on disk. */
sealed interface SculptState {
    /** There is no manifest: the terrain is not sculpted. */
    data object None : SculptState

    /**
     * The terrain is sculpted and every fingerprint holds: its heights are [base] plus [delta], the [manifest]
     * says where the base came from.
     */
    class Sculpted(val manifest: SculptManifest, val base: FloatArray, val delta: FloatArray) : SculptState {
        val kind: SculptBaseKind get() = manifest.baseKind
    }

    /** A file is missing, changed or unreadable; [detail] adds what the reader found. Sculpting is not offered. */
    data class Unsafe(val problem: SculptProblem, val detail: String = "") : SculptState
}

/**
 * Reads a terrain's [SculptState] from its files: a recipe base is regenerated and checked against the recipe's
 * `heightsSha256`, a stored base is read and checked against the manifest, and the delta and `terrain.data` must
 * match their manifest fingerprints. Pure CPU work, safe on any thread.
 */
class SculptStateReader(
    private val generator: TerrainGenerator,
    private val recipes: TerrainRecipeCodec,
    private val manifests: SculptManifestCodec,
) {
    fun read(files: SculptFiles): SculptState {
        val text = files.manifestText ?: return SculptState.None
        val manifest = when (val decoded = manifests.decode(text)) {
            is SculptManifestCodec.Decoded.Malformed -> return SculptState.Unsafe(SculptProblem.MANIFEST_MALFORMED, decoded.reason)
            is SculptManifestCodec.Decoded.Unsupported -> return SculptState.Unsafe(SculptProblem.MANIFEST_UNSUPPORTED, decoded.what)
            is SculptManifestCodec.Decoded.Manifest -> decoded.manifest
        }
        if (manifest.resolution != files.resolution) return SculptState.Unsafe(SculptProblem.RESOLUTION, "${manifest.resolution}")
        val deltaBytes = files.deltaBytes ?: return SculptState.Unsafe(SculptProblem.DELTA_MISSING)
        val delta = decodeSculptGrid(deltaBytes, files.resolution)
        if (delta == null || sha256Hex(deltaBytes) != manifest.deltaSha256) return SculptState.Unsafe(SculptProblem.DELTA_MISMATCH)
        if (sha256Hex(files.terrainBytes) != manifest.composedSha256) return SculptState.Unsafe(SculptProblem.COMPOSED_MISMATCH)
        val base = when (manifest.baseKind) {
            SculptBaseKind.STORED -> storedBase(files, manifest)
            SculptBaseKind.RECIPE -> recipeBase(files)
        }
        return when (base) {
            is BaseResult.Failed -> base.state
            is BaseResult.Heights -> SculptState.Sculpted(manifest, base.heights, delta)
        }
    }

    private sealed interface BaseResult {
        class Heights(val heights: FloatArray) : BaseResult
        class Failed(val state: SculptState.Unsafe) : BaseResult
    }

    private fun storedBase(files: SculptFiles, manifest: SculptManifest): BaseResult {
        val bytes = files.baseBytes ?: return BaseResult.Failed(SculptState.Unsafe(SculptProblem.BASE_MISSING))
        val heights = decodeSculptGrid(bytes, files.resolution)
        if (heights == null || sha256Hex(bytes) != manifest.baseSha256) {
            return BaseResult.Failed(SculptState.Unsafe(SculptProblem.BASE_MISMATCH))
        }
        return BaseResult.Heights(heights)
    }

    private fun recipeBase(files: SculptFiles): BaseResult {
        fun unusable(detail: String) = BaseResult.Failed(SculptState.Unsafe(SculptProblem.RECIPE_UNUSABLE, detail))
        val recipeText = files.recipeText ?: return unusable("missing")
        val recipe = when (val decoded = recipes.decode(recipeText)) {
            is TerrainRecipeCodec.Decoded.Failed -> return unusable(decoded.status.toString())
            is TerrainRecipeCodec.Decoded.Recipe -> decoded.recipe
        }
        if (recipe.size != files.size || recipe.resolution != files.resolution) return unusable("geometry")
        val heights = runCatchingKeepingCancellation { generator.generate(files.resolution, files.size, recipe.settings) }
            .getOrElse { return unusable(it.message.orEmpty()) }
        if (sha256Hex(encodeSculptGrid(heights)) != recipe.heightsSha256) return unusable("heights")
        return BaseResult.Heights(heights)
    }
}
