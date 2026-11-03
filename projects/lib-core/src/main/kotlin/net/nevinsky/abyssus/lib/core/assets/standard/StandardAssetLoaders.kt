/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.standard

import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDrawable
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageRuntimeLoad
import net.nevinsky.abyssus.lib.core.assets.foliage.PreparedFoliage
import net.nevinsky.abyssus.lib.core.assets.foliage.RuntimeFoliageLoader
import net.nevinsky.abyssus.lib.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.model.ModelLoader
import net.nevinsky.abyssus.lib.core.assets.model.ModelPreparationTap
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudsLoader
import net.nevinsky.abyssus.lib.core.assets.sky.cube.SkyboxLoader
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.ExrLoader
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrSkyLoader
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.ToneCurve
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSkyLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.assets.texture.TextureLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger

/**
 * The loader of every asset kind a host draws, in one place: the scene view, a standalone game and any other host take
 * [byType] and register it with an `AssetStorage`, so adding an asset kind is one line here. The loaders a host also
 * needs by themselves are exposed as typed properties, so it never makes a second one.
 *
 * Hosts differ in three things only: [modelPreparation] (a tap on the prepared model data), [log] (where loaders report), and
 * [foliage] (the editor reads foliage from its bake cache, a game from the baked data; the default is the game's).
 * Constructors only, no GL: the loaders touch GL in their `build` step on the GL thread, as before.
 */
class StandardAssetLoaders(
    files: FileLoader,
    metas: AssetMetaLoader,
    skyShaders: ShaderStorage,
    assimp: AssimpModelLoader = AssimpModelLoader(),
    toneCurve: ToneCurve = ToneCurve(),
    modelPreparation: ModelPreparationTap? = null,
    log: Logger = NOPLogger.NOP_LOGGER,
    foliage: (TerrainLoader) -> AssetLoader<Unit, PreparedFoliage, FoliageDrawable> = { terrains ->
        RuntimeFoliageLoader(metas, FoliageRuntimeLoad(files, metas, terrains)) { log.warn(it) }
    },
) {
    val model = ModelLoader(metas, assimp, files, modelPreparation, log = log)
    val terrain = TerrainLoader(files, metas)
    val foliage: AssetLoader<Unit, PreparedFoliage, FoliageDrawable> = foliage(terrain)
    val texture = TextureLoader(files, metas)
    val skybox = SkyboxLoader(files, metas, skyShaders)
    val proceduralSky = ProceduralSkyLoader(files, metas, skyShaders, log)
    val clouds = CloudsLoader(metas)
    val hdrSky = HdrSkyLoader(metas, ExrLoader(files), skyShaders, toneCurve)

    /** Every supported kind with its loader; a kind that is not here has no loader in any host. */
    val byType: Map<MetaType, AssetLoader<*, *, *>> = mapOf(
        MetaType.MODEL to model,
        MetaType.TERRAIN to terrain,
        MetaType.FOLIAGE to this.foliage,
        MetaType.TEXTURE to texture,
        MetaType.PIXMAP_TEXTURE to texture,
        MetaType.SKYBOX to skybox,
        MetaType.SKYBOX_PROCEDURAL to proceduralSky,
        MetaType.CLOUDS to clouds,
        MetaType.SKYBOX_HDR to hdrSky,
    )
}
