/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assets.sky.procedural

import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.core.assets.loading.BuiltAssets

class ProceduralSkyLoader(private val fileLoader: FileLoader, private val metaLoader: AssetMetaLoader) :
    AssetLoader<PreparedProceduralSky, ProceduralSky> {
    override fun loadPrepared(meta: AssetMeta<Any>): PreparedProceduralSky? {
        val additional = meta.typedAdditional<ProceduralSkyMeta>()
        return PreparedProceduralSky(
            additional.params,
            fileLoader.loadAssetFileContent(meta.name, additional.vertex),
            fileLoader.loadAssetFileContent(meta.name, additional.fragment)
        )
    }

    override fun prepare(name: String): PreparedProceduralSky? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        return loadPrepared(meta)
    }

    override fun build(prepared: PreparedProceduralSky, assets: BuiltAssets) = ProceduralSky(prepared)

    override fun discard(prepared: PreparedProceduralSky) = Unit
}

/** The parameters and GLSL source of a procedural sky; nothing here holds GPU or file resources. */
class PreparedProceduralSky(val params: AtmosphereParams, val vertex: String, val fragment: String)
