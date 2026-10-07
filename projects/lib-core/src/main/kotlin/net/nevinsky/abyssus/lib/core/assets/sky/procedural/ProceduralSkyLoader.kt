/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import net.nevinsky.abyssus.lib.core.assets.AssetIndex
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderSource
import net.nevinsky.abyssus.lib.core.io.FileLoader
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger

/**
 * Loads `SKYBOX_PROCEDURAL` skies. Its clouds are another asset: `additional.clouds` names a `CLOUDS` asset by `uuid`,
 * which [prepare] resolves to a folder through [index] (an unknown one is logged to [log] and the sky has no clouds) and
 * names in [dependencies], so the storage loads it first; the sky reads the built clouds from the storage on every draw,
 * as a terrain reads its splat textures. [build] compiles the asset's shaders and gives the sky the cloud shaders of
 * [shaders].
 */
class ProceduralSkyLoader(
    private val fileLoader: FileLoader,
    private val metaLoader: AssetMetaLoader,
    private val shaders: ShaderSource = ShaderSource("/shader/sky"),
    private val log: Logger = NOPLogger.NOP_LOGGER,
    private val index: AssetIndex = AssetIndex(fileLoader, metaLoader),
) : AssetLoader<PreparedProceduralSky, ProceduralSky> {
    override fun loadPrepared(meta: AssetMeta<Any>): PreparedProceduralSky? {
        val additional = meta.typedAdditional<ProceduralSkyMeta>()
        val vertex = fileLoader.loadAssetFileContent(meta.name, additional.vertex)
        val fragment = fileLoader.loadAssetFileContent(meta.name, additional.fragment)
        val reference = additional.cloudsReference
        val clouds = reference?.let { index.folder(it) }
        if (reference != null && clouds == null) {
            log.warn("Sky '${meta.name}': no asset has the clouds uuid '$reference'; drawing it without clouds")
        }
        return PreparedProceduralSky(additional.params, vertex, fragment, meta.name, clouds)
    }

    override fun prepare(name: String): PreparedProceduralSky? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        return loadPrepared(meta)
    }

    override fun dependencies(prepared: PreparedProceduralSky): Set<String> = setOfNotNull(prepared.clouds)

    override fun build(prepared: PreparedProceduralSky, assets: BuiltAssets) = ProceduralSky(prepared, assets, shaders, log)

    override fun discard(prepared: PreparedProceduralSky) = Unit
}

/**
 * The parameters and GLSL source of the procedural sky [name], and the folder of the `CLOUDS` asset it draws ([clouds];
 * null without one); nothing here holds GPU or file resources.
 */
class PreparedProceduralSky(
    val params: AtmosphereParams,
    val vertex: String,
    val fragment: String,
    val name: String = "",
    val clouds: String? = null,
)
