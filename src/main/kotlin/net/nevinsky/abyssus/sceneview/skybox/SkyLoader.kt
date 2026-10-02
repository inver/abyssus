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

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.sceneview.AssetLoader
import net.nevinsky.abyssus.sceneview.MetaType
import net.nevinsky.abyssus.sceneview.ProjectAssetFiles

/** What a scene's `skyboxName` was loaded as: a cube of six faces or a procedural sky. */
sealed interface PreparedSky {
    class Cube(val prepared: PreparedSkybox) : PreparedSky
    class Procedural(val prepared: PreparedProceduralSky) : PreparedSky
}

/** Loads whichever kind of sky the named asset folder holds, by its `meta.json` type; a [SkyboxCube] or a [ProceduralSky]. */
class SkyLoader(
    private val cube: AssetLoader<PreparedSkybox, SkyboxCube> = SkyboxLoader(),
    private val procedural: AssetLoader<PreparedProceduralSky, ProceduralSky> = ProceduralSkyLoader(),
) : AssetLoader<PreparedSky, Disposable> {
    override fun prepare(files: ProjectAssetFiles, name: String): PreparedSky? {
        val asset = files.loadAsset(ProceduralSkyMeta::class.java, name) ?: return null
        return if (asset.meta.type == MetaType.SKYBOX_PROCEDURAL) procedural.prepare(files, name)?.let(PreparedSky::Procedural)
        else cube.prepare(files, name)?.let(PreparedSky::Cube)
    }

    override fun upload(prepared: PreparedSky): Boolean = when (prepared) {
        is PreparedSky.Cube -> cube.upload(prepared.prepared)
        is PreparedSky.Procedural -> procedural.upload(prepared.prepared)
    }

    override fun build(prepared: PreparedSky): Disposable = when (prepared) {
        is PreparedSky.Cube -> cube.build(prepared.prepared)
        is PreparedSky.Procedural -> procedural.build(prepared.prepared)
    }

    override fun discard(prepared: PreparedSky) = when (prepared) {
        is PreparedSky.Cube -> cube.discard(prepared.prepared)
        is PreparedSky.Procedural -> procedural.discard(prepared.prepared)
    }
}
