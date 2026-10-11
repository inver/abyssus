/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader

/** How the bake of a [RuntimeFoliage] relates to the metadata, terrain and masks it is read with. */
enum class FoliageBakeState {
    /** The bake's fingerprint equals the one of the current inputs: it is what the editor shows. */
    MATCHING,

    /** The inputs changed after the bake: its saved copies stand under the current heights and model list. */
    STALE,

    /** A density mask could not be read, so the fingerprint cannot be checked; the readable bake is used as it is. */
    UNVERIFIABLE,
}

/**
 * A foliage asset as a game or a physics world uses it: the current [meta] and [terrain], and the [bake] reduced to the
 * copies that can be placed (a copy of a removed layer, of an index no model answers to, of a missing model or with a
 * non-finite value is not in it). Both a renderer and a collision builder take their copies from [bake], so they stand
 * where the same placement inputs put them. Immutable once built, except that the arrays inside are not copied.
 */
class RuntimeFoliage(
    val name: String,
    val meta: FoliageMeta,
    val terrain: TerrainData,
    val bake: FoliageBake,
    val state: FoliageBakeState,
)

/** The outcome of [FoliageRuntimeLoad.read]: [foliage] is null when nothing can be placed, and [warnings] say why. */
class FoliageRuntimeRead(val foliage: RuntimeFoliage?, val warnings: List<String>)

/**
 * The read policy of games and physics worlds for one foliage asset: the metadata (after the shared native
 * admission), the terrain it names, the saved bake and the density masks, all read as they are. It never generates
 * copies and never writes: a bake that no longer matches keeps its saved positions, yaw, scale and model indices under
 * the current terrain heights, layer settings and model list (a v1 bake holds no model identities, so the editor's
 * regenerated distribution cannot be recovered, and a changed model list maps the saved indices onto the new one).
 *
 * Read failures are results, not exceptions: unsupported or missing metadata, a terrain that is missing, unreadable
 * or not the one the scene entity renders, and a missing, truncated or unsupported bake leave [FoliageRuntimeRead.foliage]
 * null. A mask that cannot be read only makes the bake [FoliageBakeState.UNVERIFIABLE]. At most [FOLIAGE_MAX_WARNINGS]
 * distinct problems are reported per read, then one line counts the rest. Stateless and CPU only, so any thread may
 * call it; whoever calls it once per loaded revision logs its warnings once.
 */
class FoliageRuntimeLoad(
    private val fileLoader: FileLoader,
    private val metaLoader: AssetMetaLoader,
    private val terrains: TerrainLoader,
    private val fingerprint: FoliageFingerprint = FoliageFingerprint(),
    private val dataFile: FoliageDataFile = FoliageDataFile(),
    private val masks: FoliageMaskFile = FoliageMaskFile(fileLoader),
) {

    /**
     * The foliage asset [name]. When [terrain] names the terrain asset the owning scene entity renders, a foliage
     * asset standing on another terrain is refused.
     */
    fun read(name: String, terrain: String? = null): FoliageRuntimeRead {
        val problems = Problems()
        val foliage = readInto(name, terrain, problems)
        return FoliageRuntimeRead(foliage, problems.messages())
    }

    private fun readInto(name: String, expectedTerrain: String?, problems: Problems): RuntimeFoliage? {
        val base = metaLoader.loadBaseMeta(name)
        if (base == null || base.type != MetaType.FOLIAGE) {
            problems.add("foliage '$name' is missing or its metadata is unsupported; it is skipped")
            return null
        }
        val meta = base.additional as? FoliageMeta ?: run {
            problems.add("foliage '$name' has no readable foliage settings; it is skipped")
            return null
        }
        if (expectedTerrain != null && expectedTerrain != meta.terrain) {
            problems.add("foliage '$name' stands on terrain '${meta.terrain}', not on the entity's terrain '$expectedTerrain'; it is skipped")
            return null
        }
        val ground = runCatchingKeepingCancellation { terrains.prepare(meta.terrain)?.staged?.data }.getOrNull()
        if (ground == null) {
            problems.add("foliage '$name': terrain '${meta.terrain}' is missing or unreadable; it is skipped")
            return null
        }
        val file = fileLoader.findAssetFile(name, meta.dataFileName())
        if (file == null || !file.isFile) {
            problems.add("foliage '$name' has no bake (${meta.dataFileName()}); it is skipped until it is baked in the editor")
            return null
        }
        val bake = runCatchingKeepingCancellation { file.readBytes() }.getOrNull()?.let(dataFile::read)
        if (bake == null) {
            problems.add("foliage '$name': ${meta.dataFileName()} is truncated or not a supported bake; it is skipped")
            return null
        }
        val state = stateOf(name, meta, ground, bake, problems)
        return RuntimeFoliage(name, meta, ground, accepted(name, meta, bake, problems), state)
    }

    private fun stateOf(name: String, meta: FoliageMeta, ground: TerrainData, bake: FoliageBake, problems: Problems): FoliageBakeState {
        val layerMasks = HashMap<Int, ByteArray>()
        for (layer in meta.layers) {
            val mask = runCatchingKeepingCancellation { masks.read(name, layer.id, meta.maskResolution) }.getOrNull()
            if (mask == null) {
                problems.add("foliage '$name': the density mask of layer ${layer.id} cannot be read, so its bake cannot be checked; the bake is used as saved")
                return FoliageBakeState.UNVERIFIABLE
            }
            layerMasks[layer.id] = mask
        }
        if (fingerprint.of(meta, ground, layerMasks).contentEquals(bake.fingerprint)) return FoliageBakeState.MATCHING
        problems.add("foliage '$name' was changed after its last bake; the saved copies are used with the current terrain and layer settings until it is baked again")
        return FoliageBakeState.STALE
    }

    /** [bake] without the copies that cannot be placed under [meta]. */
    private fun accepted(name: String, meta: FoliageMeta, bake: FoliageBake, problems: Problems): FoliageBake {
        val layers = ArrayList<FoliageLayerBake>(bake.layers.size)
        for (saved in bake.layers) {
            val layer = meta.layers.firstOrNull { it.id == saved.id }
            if (layer == null) {
                problems.add("foliage '$name': the baked layer ${saved.id} no longer exists and its copies are dropped")
                continue
            }
            val usable = layer.models.map { it.asset.isNotBlank() && metaLoader.loadBaseMeta(it.asset) != null }
            usable.forEachIndexed { index, ok ->
                if (!ok) problems.add("foliage '$name': model '${layer.models[index].asset}' of layer ${layer.id} is missing; its copies are dropped")
            }
            var unresolved = false
            var invalid = false
            val chunks = saved.chunks.map { copies ->
                copies.filter { copy ->
                    val finite = copy.x.isFinite() && copy.z.isFinite() && copy.yaw.isFinite() && copy.scale.isFinite()
                    when {
                        !finite -> false.also { invalid = true }
                        copy.model >= usable.size -> false.also { unresolved = true }
                        else -> usable[copy.model]
                    }
                }
            }
            if (unresolved) problems.add("foliage '$name': layer ${layer.id} has copies of a model index the layer no longer has; they are dropped")
            if (invalid) problems.add("foliage '$name': layer ${layer.id} has copies with non-finite values; they are dropped")
            layers += FoliageLayerBake(saved.id, saved.modelCount, saved.chunksX, saved.chunksZ, chunks)
        }
        return FoliageBake(bake.fingerprint, bake.chunkSize, layers)
    }

    /** The distinct problems of one read, in order, capped. */
    private class Problems {
        private val seen = LinkedHashSet<String>()
        private var dropped = 0

        fun add(message: String) {
            if (message in seen) return
            if (seen.size < FOLIAGE_MAX_WARNINGS) seen += message else dropped++
        }

        fun messages(): List<String> =
            if (dropped == 0) seen.toList() else seen.toList() + "and $dropped more foliage problems"
    }
}

/** The most distinct problems one [FoliageRuntimeLoad] read reports before it only counts the rest. */
const val FOLIAGE_MAX_WARNINGS = 16
