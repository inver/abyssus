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

package net.nevinsky.abyssus.assets

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.loading.AssetLoader
import net.nevinsky.abyssus.assets.loading.SceneAssets
import net.nevinsky.abyssus.assets.model.ModelLoader
import net.nevinsky.abyssus.assets.sky.SkyLoader
import net.nevinsky.abyssus.assets.sky.cube.SkyboxLoader
import net.nevinsky.abyssus.assets.sky.hdr.HdrPreview
import net.nevinsky.abyssus.assets.sky.hdr.HdrSkyFiles
import net.nevinsky.abyssus.assets.sky.hdr.HdrSkyLoader
import net.nevinsky.abyssus.assets.sky.hdr.RadianceDecoder
import net.nevinsky.abyssus.assets.sky.hdr.ToneCurve
import net.nevinsky.abyssus.assets.sky.procedural.ProceduralSkyLoader
import net.nevinsky.abyssus.assets.terrain.TerrainDataReader
import net.nevinsky.abyssus.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import java.io.File
import java.util.concurrent.Executor

/**
 * Builds the asset loading graph from what the caller provides: [json] for `meta.json`, [log] for problems, [executor]
 * for the off-GL-thread `prepare` step and [skyShaders] for the sky programs (`/shader/sky` in this module). Holds no
 * state of its own: every [assets] it hands out owns its caches, so two scene views or two tests never share one.
 */
class AssetLoading(
    val json: JsonProcessor,
    val log: AssetLog,
    private val executor: Executor,
    skyShaders: ShaderSource,
) {
    val decoder = RadianceDecoder()
    val hdrFiles = HdrSkyFiles()
    val toneCurve = ToneCurve()
    val hdrPreview = HdrPreview(decoder, toneCurve)

    val models = ModelLoader(AssimpModelLoader())
    val terrains = TerrainLoader(TerrainDataReader())
    val skies = SkyLoader(
        SkyboxLoader(skyShaders),
        ProceduralSkyLoader(),
        HdrSkyLoader(decoder, hdrFiles, skyShaders, toneCurve, log),
    )

    /** The asset files of the project in [projectDir]. */
    fun files(projectDir: File): AssetFiles = AssetFiles(projectDir, json)

    /** A new per-project cache over [loader]: prepares on the executor, builds on the GL thread, logs to [log]. */
    fun <P : Any, T : Disposable> assets(loader: AssetLoader<P, T>): SceneAssets<P, T> = SceneAssets(executor, loader, ::files, log)
}
