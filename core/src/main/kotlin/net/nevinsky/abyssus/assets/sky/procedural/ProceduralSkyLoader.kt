/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.sky.procedural

import net.nevinsky.abyssus.assets.files.AssetMeta
import net.nevinsky.abyssus.assets.loading.AssetLoader
import net.nevinsky.abyssus.core.AssetMetaLoader
import net.nevinsky.abyssus.core.FileLoader

class ProceduralSkyLoader(private val fileLoader: FileLoader, private val metaLoader: AssetMetaLoader) :
    AssetLoader<PreparedProceduralSky, ProceduralSky> {
    override fun loadPrepared(meta: AssetMeta<Any>): PreparedProceduralSky? {
        val additional = meta.typedAdditional<ProceduralSkyMeta>()
        return PreparedProceduralSky(
            additional.params,
            fileLoader.loadFileContent(meta.name, additional.shaderVert),
            fileLoader.loadFileContent(meta.name, additional.shaderFrag)
        )
    }

    override fun prepare(name: String): PreparedProceduralSky? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        return loadPrepared(meta)
    }

    override fun build(prepared: PreparedProceduralSky) = ProceduralSky(prepared)

    override fun discard(prepared: PreparedProceduralSky) = Unit
}

/** The parameters and GLSL source of a procedural sky; nothing here holds GPU or file resources. */
class PreparedProceduralSky(val params: AtmosphereParams, val vertex: String, val fragment: String)
