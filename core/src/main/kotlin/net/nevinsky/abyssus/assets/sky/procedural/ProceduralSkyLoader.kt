/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.sky.procedural

import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.loading.AssetLoader
import net.nevinsky.abyssus.core.AssetMetaLoader
import net.nevinsky.abyssus.core.FileLoader

class ProceduralSkyLoader(private val fileLoader: FileLoader, private val metaLoader: AssetMetaLoader) :
    AssetLoader<PreparedProceduralSky, ProceduralSky> {

    override fun prepare(files: AssetFiles, name: String): PreparedProceduralSky? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        val additional = meta.typedAdditional<ProceduralSkyMeta>()
        return PreparedProceduralSky(
            additional.params,
            fileLoader.loadFileContent(name, additional.shaderVert),
            fileLoader.loadFileContent(name, additional.shaderFrag)
        )
    }

    override fun build(prepared: PreparedProceduralSky) = ProceduralSky(prepared)

    override fun discard(prepared: PreparedProceduralSky) = Unit
}

/** The parameters and GLSL source of a procedural sky; nothing here holds GPU or file resources. */
class PreparedProceduralSky(val params: AtmosphereParams, val vertex: String, val fragment: String)
