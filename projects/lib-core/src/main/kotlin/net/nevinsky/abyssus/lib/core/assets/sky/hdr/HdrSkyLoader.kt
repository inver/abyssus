/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.hdr

import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.loading.StagedMetaAssetLoader
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.Prepared
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage

/**
 * `SKYBOX_HDR` assets: the `.exr` is decoded off the GL thread, then the environment is built on the GPU
 * one step per frame ([HdrEnvironmentBuild]).
 */
class HdrSkyLoader(
    metaLoader: AssetMetaLoader,
    private val exrLoader: ExrLoader,
    private val shaders: ShaderStorage,
    private val curve: ToneCurve,
) : StagedMetaAssetLoader<PreparedHdrSky, HdrSky>(metaLoader) {

    override fun read(meta: AssetMeta<Any>): PreparedHdrSky {
        val additional = meta.typedAdditional<HdrSkyMeta>()
        val image = exrLoader.loadExr(meta.name, additional.file)
        return PreparedHdrSky(meta.name, additional.file!!, image)
    }

    override fun upload(staged: PreparedHdrSky): Boolean =
        (staged.build ?: HdrEnvironmentBuild(staged.image, shaders).also { staged.build = it }).step()

    override fun build(staged: PreparedHdrSky, assets: BuiltAssets): HdrSky {
        val environment = checkNotNull(staged.build) { "HDR sky '${staged.name}' was never uploaded" }.finishHdr()
        staged.build = null
        return HdrSky(environment, shaders, curve)
    }


    /** Releases a half-done build; nothing to do before the first upload step (the decoded image is plain JVM memory). */
    override fun discardStaged(staged: PreparedHdrSky) {
        staged.build?.dispose()
        staged.build = null
    }
}

/** A decoded HDR sky waiting for its GPU build; [build] is created and advanced on the GL thread only. */
class PreparedHdrSky(val name: String, val file: String, val image: HdrImage) {
    internal var build: HdrEnvironmentBuild? = null
}
