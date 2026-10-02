/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview.skybox

import net.nevinsky.abyssus.sceneview.AssetLoader
import net.nevinsky.abyssus.sceneview.MetaType
import net.nevinsky.abyssus.sceneview.ProjectAssetFiles

/** Procedural sky assets: the metadata and both GLSL files read off the GL thread, compiled when built. */
class ProceduralSkyLoader : AssetLoader<PreparedProceduralSky, ProceduralSky> {
    /** Null for a folder that is not a `SKYBOX_PROCEDURAL`; throws when a shader file it names is missing. */
    override fun prepare(files: ProjectAssetFiles, name: String): PreparedProceduralSky? {
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
