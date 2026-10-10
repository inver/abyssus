/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.Prepared
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader

/**
 * A foliage asset read off the GL thread: its [meta], the [terrain] it stands on (null when that asset is missing or
 * unreadable), one [masks] entry per layer (a full mask when the file is missing), the [bake] read from
 * `foliage.data` (null when it is missing or not a readable bake), the [fingerprint] of the current inputs and the
 * asset names in [dependencies]. [stale] is true when the bake does not match the fingerprint: the view then
 * generates copies from the settings instead.
 */
class PreparedFoliage(
    val meta: FoliageMeta,
    val terrain: TerrainData?,
    val masks: Map<Int, ByteArray>,
    val bake: FoliageBake?,
    val fingerprint: ByteArray,
    val dependencies: Set<String>,
) {
    val stale: Boolean get() = bake == null || !bake.fingerprint.contentEquals(fingerprint)

    val copyCount: Int get() = bake?.copyCount ?: 0
}

/**
 * Foliage assets. [prepare] reads and validates the meta with the shared native checks, then the masks and the bake;
 * it runs off the GL thread. The terrain and every layer model whose folder holds a readable meta are declared in
 * [dependencies], so the storage loads them first; one whose folder is missing is left out and drops only its own
 * copies when the drawable is built.
 */
class FoliageLoader(
    private val fileLoader: FileLoader,
    private val metaLoader: AssetMetaLoader,
    private val terrains: TerrainLoader,
    private val fingerprint: FoliageFingerprint = FoliageFingerprint(),
    private val dataFile: FoliageDataFile = FoliageDataFile(),
    private val masks: FoliageMaskFile = FoliageMaskFile(fileLoader),
) : AssetLoader<Unit, PreparedFoliage, FoliageDrawable> {

    override fun loadPrepared(meta: AssetMeta<Any>): Prepared<Unit, PreparedFoliage>? {
        val additional = meta.additional as? FoliageMeta ?: return null
        return Prepared(prepare(meta.name, additional))
    }

    override fun prepare(name: String): Prepared<Unit, PreparedFoliage>? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        if (meta.type != MetaType.FOLIAGE) return null
        val additional = meta.additional as? FoliageMeta ?: return null
        return Prepared(prepare(name, additional))
    }

    private fun prepare(name: String, meta: FoliageMeta): PreparedFoliage {
        val terrain = terrains.prepare(meta.terrain)?.staged?.data
        val layerMasks = meta.layers.associate { it.id to masks.read(name, it.id, meta.maskResolution) }
        val bake = fileLoader.findAssetFile(name, meta.dataFileName())
            ?.let { file -> runCatchingKeepingCancellation { file.readBytes() }.getOrNull() }
            ?.let(dataFile::read)
        val declared = (listOf(meta.terrain) + meta.layers.flatMap { layer -> layer.models.map { it.asset } })
            .filter { it.isNotBlank() && metaLoader.loadBaseMeta(it) != null }
            .toSet()
        return PreparedFoliage(meta, terrain, layerMasks, bake, fingerprint.of(meta, terrain, layerMasks), declared)
    }

    override fun dependencies(staged: PreparedFoliage): Set<String> = staged.dependencies

    override fun build(staged: PreparedFoliage, assets: BuiltAssets) = FoliageDrawable(staged, assets)

    override fun discard(model: Unit) = Unit
}


