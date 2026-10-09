/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import net.nevinsky.abyssus.lib.gdx.assets.AssetIndex
import net.nevinsky.abyssus.lib.gdx.assets.AssetMeta
import net.nevinsky.abyssus.lib.gdx.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.Prepared
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.gdx.io.FileLoader
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
    shaders: ShaderStorage = ShaderStorage(),
    private val log: Logger = NOPLogger.NOP_LOGGER,
    private val index: AssetIndex = AssetIndex(fileLoader, metaLoader),
) : AssetLoader<Unit, PreparedProceduralSky, ProceduralSky> {
    /** The sky's own shader files come from its asset folder; the cloud shaders from the resources and defaults. */
    private val shaders = shaders.withAssets(fileLoader)

    override fun loadPrepared(meta: AssetMeta<Any>): Prepared<Unit, PreparedProceduralSky> = Prepared(read(meta))

    private fun read(meta: AssetMeta<Any>): PreparedProceduralSky {
        val additional = meta.typedAdditional<ProceduralSkyMeta>()
        val vertex = shader(meta.name, additional.vertex)
        val fragment = shader(meta.name, additional.fragment)
        val reference = additional.cloudsReference
        val clouds = reference?.let { index.folder(it) }
        if (reference != null && clouds == null) {
            log.warn("Sky '${meta.name}': no asset has the clouds uuid '$reference'; drawing it without clouds")
        }
        return PreparedProceduralSky(additional.params, vertex, fragment, meta.name, clouds)
    }

    private fun shader(asset: String, file: String?): String {
        require(!file.isNullOrBlank()) { "Empty file name" }
        return shaders.read(file, asset).also { check(it.isNotBlank()) { "Asset '$asset' has no file '$file'" } }
    }

    override fun prepare(name: String): Prepared<Unit, PreparedProceduralSky>? =
        metaLoader.loadBaseMeta(name)?.let(::loadPrepared)

    override fun dependencies(staged: PreparedProceduralSky): Set<String> = setOfNotNull(staged.clouds)

    override fun build(staged: PreparedProceduralSky, assets: BuiltAssets) = ProceduralSky(staged, assets, shaders, log)

    override fun discard(model: Unit) = Unit
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
