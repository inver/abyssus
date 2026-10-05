/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.sky.hdr

import net.nevinsky.abyssus.assets.ShaderSource
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.loading.AssetLoader
import net.nevinsky.abyssus.core.AssetMetaLoader
import org.lwjgl.util.tinyexr.EXRImage


/**
 * `SKYBOX_HDR` assets: the `.hdr` is chosen and decoded off the GL thread, then the environment is built on the GPU
 * one step per frame ([HdrEnvironmentBuild]).
 */
class HdrSkyLoader(
    private val metaLoader: AssetMetaLoader,
    private val exrLoader: ExrLoader,
    private val shaders: ShaderSource,
    private val curve: ToneCurve,
) : AssetLoader<PreparedHdrSky, HdrSky> {

    override fun prepare(files: AssetFiles, name: String): PreparedHdrSky? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        val additional = meta.typedAdditional<HdrSkyMeta>()
        val image = exrLoader.loadExr(name, additional.file)
        return PreparedHdrSky(name, additional.file!!, image)
    }

    override fun upload(prepared: PreparedHdrSky): Boolean =
        (prepared.build ?: HdrEnvironmentBuild(prepared.image, shaders).also { prepared.build = it }).step()

    override fun build(prepared: PreparedHdrSky): HdrSky {
        val environment = checkNotNull(prepared.build) { "HDR sky '${prepared.name}' was never uploaded" }.finish()
        prepared.build = null
        return HdrSky(environment, shaders, curve)
    }

    /** Releases a half-done build; nothing to do before the first upload step (the decoded image is plain memory). */
    override fun discard(prepared: PreparedHdrSky) {
        prepared.build?.dispose()
        prepared.build = null
    }
}

/** A decoded HDR sky waiting for its GPU build; [build] is created and advanced on the GL thread only. */
class PreparedHdrSky(val name: String, val file: String, val image: EXRImage) {
    internal var build: HdrEnvironmentBuild? = null
}
