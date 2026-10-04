/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.loading.AssetLoader
import net.nevinsky.abyssus.assets.loading.SceneAssets
import net.nevinsky.abyssus.assets.model.ModelLoader
import net.nevinsky.abyssus.assets.model.RayModelSnapshotReader
import net.nevinsky.abyssus.assets.model.RayModelSnapshots
import net.nevinsky.abyssus.assets.sky.RaySkySnapshotReader
import net.nevinsky.abyssus.assets.sky.RaySkySnapshots
import net.nevinsky.abyssus.assets.sky.SkyLoader
import net.nevinsky.abyssus.assets.sky.cube.SkyboxLoader
import net.nevinsky.abyssus.assets.sky.hdr.HdrPreview
import net.nevinsky.abyssus.assets.sky.hdr.HdrSkyFiles
import net.nevinsky.abyssus.assets.sky.hdr.HdrSkyLoader
import net.nevinsky.abyssus.assets.sky.hdr.RadianceDecoder
import net.nevinsky.abyssus.assets.sky.hdr.ToneCurve
import net.nevinsky.abyssus.assets.sky.procedural.ProceduralSkyLoader
import net.nevinsky.abyssus.assets.terrain.TerrainDataReader
import net.nevinsky.abyssus.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.assets.terrain.RayTerrainSnapshotReader
import net.nevinsky.abyssus.assets.terrain.RayTerrainSnapshots
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import java.io.File
import java.util.concurrent.Executor

/**
 * Builds the asset loading graph from what the caller provides: [json] for `meta.json`, [log] for problems, [executor]
 * for the off-GL-thread `prepare` step and [skyShaders] for the sky programs (`/shader/sky` in this module). Holds no
 * shared optional CPU model/terrain companions; every [assets] it hands out owns its GPU caches.
 */
class AssetLoading(
    val json: JsonProcessor,
    val log: AssetLog,
    private val executor: Executor,
    skyShaders: ShaderSource,
) {
    val decoder = RadianceDecoder()
    val hdrFiles = HdrSkyFiles()
    val toneCurve = ToneCurve()
    val hdrPreview = HdrPreview(decoder, toneCurve)

    private val modelReader = AssimpModelLoader()
    private val rayModelReader = RayModelSnapshotReader(modelReader)
    val rayModels = RayModelSnapshots(executor, rayModelReader::read, rayModelReader::capture)
    val models = ModelLoader(modelReader, rayModels)
    private val terrainReader = TerrainDataReader()
    private val rayTerrainReader = RayTerrainSnapshotReader(terrainReader)
    val rayTerrains = RayTerrainSnapshots(executor, rayTerrainReader::read, rayTerrainReader::capture)
    val terrains = TerrainLoader(terrainReader, rayTerrains)
    private val raySkyReader = RaySkySnapshotReader(decoder, hdrFiles)
    val raySkies = RaySkySnapshots(executor, raySkyReader::read)
    val skies = SkyLoader(
        SkyboxLoader(skyShaders),
        ProceduralSkyLoader(),
        HdrSkyLoader(decoder, hdrFiles, skyShaders, toneCurve, log),
    )

    /** The asset files of the project in [projectDir]. */
    fun files(projectDir: File): AssetFiles = AssetFiles(projectDir, json)

    /** A new per-project cache over [loader]: prepares on the executor, builds on the GL thread, logs to [log]. */
    fun <P : Any, T : Disposable> assets(loader: AssetLoader<P, T>): SceneAssets<P, T> = SceneAssets(executor, loader, ::files, log)
}
