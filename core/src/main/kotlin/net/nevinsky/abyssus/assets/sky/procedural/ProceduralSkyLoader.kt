/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.sky.procedural

import net.nevinsky.abyssus.assets.loading.AssetLoader
import net.nevinsky.abyssus.assets.files.MetaType
import net.nevinsky.abyssus.assets.files.AssetFiles

/** Procedural sky assets: the metadata and both GLSL files read off the GL thread, compiled when built. */
class ProceduralSkyLoader : AssetLoader<PreparedProceduralSky, ProceduralSky> {
    /** Null for a folder that is not a `SKYBOX_PROCEDURAL`; throws when a shader file it names is missing. */
    override fun prepare(files: AssetFiles, name: String): PreparedProceduralSky? {
        val asset = files.loadAsset(ProceduralSkyMeta::class.java, name) ?: return null
        if (asset.meta.type != MetaType.SKYBOX_PROCEDURAL) return null
        val additional = asset.meta.additional
        fun source(field: String, file: String?) =
            files.loadFile(name, file)?.readText() ?: throw IllegalStateException("Sky '$name' has no $field shader file '$file'")
        return PreparedProceduralSky(additional.params, source("vertex", additional.vertex), source("fragment", additional.fragment))
    }

    override fun build(prepared: PreparedProceduralSky) = ProceduralSky(prepared)

    override fun discard(prepared: PreparedProceduralSky) = Unit
}

/** The parameters and GLSL source of a procedural sky; nothing here holds GPU or file resources. */
class PreparedProceduralSky(val params: AtmosphereParams, val vertex: String, val fragment: String)
