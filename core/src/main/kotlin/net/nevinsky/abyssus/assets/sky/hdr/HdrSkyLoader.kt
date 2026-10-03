/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.sky.hdr

import net.nevinsky.abyssus.assets.loading.AssetLoader
import net.nevinsky.abyssus.assets.ShaderSource
import net.nevinsky.abyssus.assets.AssetLog
import net.nevinsky.abyssus.assets.files.MetaBase
import net.nevinsky.abyssus.assets.files.MetaType
import net.nevinsky.abyssus.assets.files.AssetFiles
import java.io.File
import java.util.*

/** The `meta.json` of an `SKYBOX_HDR` asset; `additional` is read only for text values that may name the image. */
class HdrSkyMeta(
    version: Int,
    lastModified: Long,
    type: MetaType,
    additional: Map<String, Any?>?,
    uuid: UUID? = null,
) : MetaBase<Map<String, Any?>?>(version, lastModified, type, additional, uuid)

/** A decoded HDR sky waiting for its GPU build; [build] is created and advanced on the GL thread only. */
class PreparedHdrSky(val name: String, val file: File, val image: HdrImage) {
    internal var build: HdrEnvironmentBuild? = null
}

/**
 * `SKYBOX_HDR` assets: the `.hdr` is chosen and decoded off the GL thread, then the environment is built on the GPU
 * one step per frame ([HdrEnvironmentBuild]).
 */
class HdrSkyLoader(
    private val decoder: RadianceDecoder,
    private val hdrFiles: HdrSkyFiles,
    private val shaders: ShaderSource,
    private val curve: ToneCurve,
    private val log: AssetLog,
) : AssetLoader<PreparedHdrSky, HdrSky> {
    /**
     * Null for a missing folder or a folder that is not an `SKYBOX_HDR`; throws, with the reason, for a folder without
     * a `.hdr` and for an image that cannot be decoded.
     */
    override fun prepare(files: AssetFiles, name: String): PreparedHdrSky? {
        val asset = files.loadAsset(HdrSkyMeta::class.java, name) ?: return null
        if (asset.meta.type != MetaType.SKYBOX_HDR) return null
        val dir = asset.baseDir
        val named = asset.meta.additional.orEmpty().values.filterIsInstance<String>()
        val choice = hdrFiles.choose(dir.list()?.toList().orEmpty(), named)
            ?: throw IllegalStateException("HDR sky '$name' has no .hdr file")
        choice.warning?.let { log.warn("HDR sky '$name': $it", null) }
        val file = files.file(dir, choice.file) ?: throw IllegalStateException("HDR sky '$name': cannot read '${choice.file}'")
        val image = try {
            decoder.read(file)
        } catch (e: RadianceFormatException) {
            throw IllegalStateException("HDR sky '$name': '${choice.file}' ${e.message}", e)
        }
        return PreparedHdrSky(name, file, image)
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
