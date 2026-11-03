/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainGenerator
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainHeightEncoder
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainPreview
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainRecipe
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainRecipeCodec
import net.nevinsky.abyssus.lib.core.editor.terrain.sha256Hex

/**
 * The content every file of a terrain's height layer should have after an operation: `terrain.data`, the recipe and the
 * three sculpt files. A null is a file that should not exist. The plugin compares this with the files as they are
 * and stages the differences as one undoable transaction; nothing here touches the disk.
 */
class SculptFileSet(
    val terrain: ByteArray,
    val recipe: ByteArray?,
    val manifest: ByteArray?,
    val delta: ByteArray?,
    val base: ByteArray?,
)

/**
 * Plans the file contents of the operations that change a terrain's heights or sculpting: Regenerate, committing a
 * stroke, Reset sculpting and Forget sculpting. Pure CPU work on bytes, safe on any thread.
 */
class SculptPlanner(
    private val generator: TerrainGenerator,
    private val encoder: TerrainHeightEncoder,
    private val recipes: TerrainRecipeCodec,
    private val manifests: SculptManifestCodec,
) {
    /**
     * Regenerate with [preview] (the new base). A sculpted terrain keeps its delta unless [discard]: `terrain.data` is
     * then `base' + delta` (one float add per vertex), the delta bytes are untouched, a stored base is replaced by the
     * recipe's and the manifest fingerprints the new composed heights. With [discard], or when the terrain is not
     * sculpted, the heights are exactly the new base and no sculpt file remains. An unsafe [state] is refused.
     */
    fun regenerate(files: SculptFiles, state: SculptState, preview: TerrainPreview, discard: Boolean): SculptFileSet {
        require(state !is SculptState.Unsafe) { "an unsafe sculpt state cannot be regenerated" }
        require(preview.size == files.size && preview.resolution == files.resolution) { "the preview is of other geometry" }
        val baseBytes = encoder.encode(preview.heights)
        val recipe = TerrainRecipe(preview.settings, preview.size, preview.resolution, sha256Hex(baseBytes))
        val recipeBytes = recipes.encode(recipe).toByteArray()
        if (state !is SculptState.Sculpted || discard) {
            return SculptFileSet(baseBytes, recipeBytes, manifest = null, delta = null, base = null)
        }
        val deltaBytes = checkNotNull(files.deltaBytes) { "a sculpted terrain has its delta file" }
        val composed = encoder.encode(composeSculpt(preview.heights, state.delta))
        val manifest = SculptManifest(SculptBaseKind.RECIPE, files.resolution, sha256Hex(deltaBytes), sha256Hex(composed))
        return SculptFileSet(composed, recipeBytes, manifests.encode(manifest).toByteArray(), deltaBytes, base = null)
    }

    /**
     * Reset sculpting: `terrain.data` returns to the base (regenerated from the recipe, or the stored base's own bytes)
     * and the delta, manifest and stored base go. The recipe is untouched. Only a sculpted [state] can be reset.
     */
    fun reset(files: SculptFiles, state: SculptState): SculptFileSet {
        require(state is SculptState.Sculpted) { "only a sculpted terrain can be reset" }
        val base = if (state.kind == SculptBaseKind.STORED) checkNotNull(files.baseBytes) else encoder.encode(state.base)
        return SculptFileSet(base, files.recipeText?.toByteArray(), manifest = null, delta = null, base = null)
    }

    /**
     * Forget sculpting: the sculpt files go and `terrain.data` and the recipe stay exactly as they are, so the current
     * heights become an unsculpted terrain. Offered for an unsafe state; a terrain with no manifest has nothing to forget.
     */
    fun forget(files: SculptFiles, state: SculptState): SculptFileSet {
        require(state !is SculptState.None) { "there is no sculpting to forget" }
        return SculptFileSet(files.terrainBytes, files.recipeText?.toByteArray(), manifest = null, delta = null, base = null)
    }

    /**
     * Commits a sculpting stroke held in [draft] (already [SculptDraft.recompose]d) over the terrain whose files are
     * [files] and whose state was [state]: `terrain.data` takes the composed heights and the delta and manifest are written.
     * The first stroke of an unsculpted terrain decides the base: a recipe that still reproduces the heights stays the
     * base (no base file, the recipe is not rewritten); otherwise the heights from before the stroke are written as the
     * stored base. An unsafe [state] is refused.
     */
    fun commitStroke(files: SculptFiles, state: SculptState, draft: SculptDraft): SculptFileSet {
        require(state !is SculptState.Unsafe) { "an unsafe sculpt state cannot be sculpted" }
        require(draft.resolution == files.resolution) { "the draft is of another resolution" }
        val terrain = encoder.encode(draft.composed)
        val delta = encoder.encode(draft.delta)
        val recipeBytes = files.recipeText?.toByteArray()
        val kind = when {
            state is SculptState.Sculpted -> state.kind
            recipeReproduces(files) -> SculptBaseKind.RECIPE
            else -> SculptBaseKind.STORED
        }
        val base = if (kind != SculptBaseKind.STORED) null else if (state is SculptState.Sculpted) files.baseBytes else files.terrainBytes
        val manifest = SculptManifest(kind, files.resolution, sha256Hex(delta), sha256Hex(terrain), base?.let { sha256Hex(it) })
        return SculptFileSet(terrain, recipeBytes, manifests.encode(manifest).toByteArray(), delta, base)
    }

    /** True when the recipe regenerates exactly the heights in `terrain.data`, so it can stand as the base. */
    private fun recipeReproduces(files: SculptFiles): Boolean {
        val text = files.recipeText ?: return false
        val recipe = (recipes.decode(text) as? TerrainRecipeCodec.Decoded.Recipe)?.recipe ?: return false
        if (recipe.size != files.size || recipe.resolution != files.resolution) return false
        val heights = runCatchingKeepingCancellation { generator.generate(files.resolution, files.size, recipe.settings) }
            .getOrNull() ?: return false
        val bytes = encoder.encode(heights)
        return sha256Hex(bytes) == recipe.heightsSha256 && bytes.contentEquals(files.terrainBytes)
    }
}
