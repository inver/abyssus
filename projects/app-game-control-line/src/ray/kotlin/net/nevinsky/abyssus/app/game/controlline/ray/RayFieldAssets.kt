/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ray

import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.standard.StandardAssetLoaders
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.*
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.Executor

/** What the field's ray assets are doing: still reading, failed (with the asset names), or ready. */
sealed interface RayFieldAssetState {
    data class Preparing(val assets: List<String>) : RayFieldAssetState
    data class Failed(val failures: Map<String, Throwable>) : RayFieldAssetState
    class Ready(
        val models: Map<String, RayModelSnapshot>, val terrains: Map<String, RayTerrainSnapshot>, val sky: RaySkySnapshot?,
    ) : RayFieldAssetState
}

/**
 * The CPU snapshots of the field's models, terrains and sky, read off the GL thread by the editor's snapshot stores
 * (nothing here touches GL). The scene asks for the names it draws with [update]; [poll] says whether they are ready.
 */
class RayFieldAssets(projectDir: File, json: JsonProcessor, log: Logger, executor: Executor) : AutoCloseable {
    private val files = FileLoader(projectDir.absoluteFile)
    private val metas = AssetMetaLoader(json, files, log)
    private val loaders = StandardAssetLoaders(files, metas, ShaderStorage().withAssets(files), log = log)

    private val models = RaySnapshotStore<RayModelSnapshot, RayModelSource>(
        executor, metas, ModelRaySnapshotLoader(files, AssimpModelLoader()), "model",
    )
    private val terrains = RaySnapshotStore<RayTerrainSnapshot, Nothing>(
        executor, metas, TerrainRaySnapshotLoader(loaders.terrain, loaders.texture), "terrain",
    )
    private val skies = RaySnapshotStore<RaySkySnapshot, Nothing>(
        executor, metas, SkyLoader(SkyboxRaySnapshotLoader(loaders.skybox), HdrSkyRaySnapshotLoader(loaders.hdrSky)), "sky",
    )

    private val modelLeases = linkedMapOf<String, RaySnapshotLease<RayModelSnapshot>>()
    private val terrainLeases = linkedMapOf<String, RaySnapshotLease<RayTerrainSnapshot>>()
    private var skyName: String? = null
    private var skyLease: RaySnapshotLease<RaySkySnapshot>? = null

    /** Keeps exactly these assets leased: new names start reading, names no longer drawn are released. */
    fun update(modelNames: Set<String>, terrainNames: Set<String>, sky: String?) {
        reconcile(modelLeases, modelNames, models::acquire)
        reconcile(terrainLeases, terrainNames, terrains::acquire)
        if (sky != skyName) {
            skyLease?.close()
            skyName = sky
            skyLease = sky?.let(skies::acquire)
        }
    }

    fun poll(): RayFieldAssetState {
        val failures = modelLeases.mapNotNull { (name, lease) -> lease.failure?.let { "model:$name" to it } } +
            terrainLeases.mapNotNull { (name, lease) -> lease.failure?.let { "terrain:$name" to it } }
        if (failures.isNotEmpty()) return RayFieldAssetState.Failed(failures.toMap())
        val readyModels = modelLeases.mapNotNull { (name, lease) -> lease.snapshot?.let { name to it } }.toMap()
        val readyTerrains = terrainLeases.mapNotNull { (name, lease) -> lease.snapshot?.let { name to it } }.toMap()
        val pending = modelLeases.keys.filter { it !in readyModels }.map { "model:$it" } +
            terrainLeases.keys.filter { it !in readyTerrains }.map { "terrain:$it" }
        // a sky that is still reading, or has no image (a procedural sky), never blocks the view: the background shows
        return if (pending.isNotEmpty()) RayFieldAssetState.Preparing(pending)
        else RayFieldAssetState.Ready(readyModels, readyTerrains, skyLease?.snapshot)
    }

    override fun close() {
        modelLeases.values.forEach(AutoCloseable::close)
        terrainLeases.values.forEach(AutoCloseable::close)
        skyLease?.close()
        modelLeases.clear(); terrainLeases.clear(); skyLease = null; skyName = null
    }

    private fun <S : RaySnapshot> reconcile(
        leases: MutableMap<String, RaySnapshotLease<S>>, names: Set<String>, acquire: (String) -> RaySnapshotLease<S>,
    ) {
        leases.keys.filter { it !in names }.forEach { leases.remove(it)?.close() }
        names.forEach { name -> if (name !in leases) leases[name] = acquire(name) }
    }

    /** One store for every sky kind that has an image; the loader of the asset's own type reads it. */
    private class SkyLoader(
        private val skybox: SkyboxRaySnapshotLoader, private val hdr: HdrSkyRaySnapshotLoader,
    ) : RaySnapshotLoader<RaySkySnapshot, Nothing> {
        override fun load(meta: AssetMeta<Any>): RaySkySnapshot? = when (meta.type) {
            MetaType.SKYBOX -> skybox.load(meta)
            MetaType.SKYBOX_HDR -> hdr.load(meta)
            else -> null
        }
    }
}
