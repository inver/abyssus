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

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import java.io.File
import java.util.concurrent.Executor

class TerrainEntity(override val placement: AssetPlacement, val terrain: TerrainMesh, val world: Matrix4) : PlacedEntity<TerrainMesh> {
    override val asset: TerrainMesh get() = terrain
}

/** The terrain entities of the scene, loaded like [SceneModels]. GL thread only. */
class SceneTerrains(executor: Executor, loader: AssetLoader<PreparedTerrain, TerrainMesh>) : Disposable {
    private val assets = SceneAssets(executor, loader)
    private val entities = PlacedEntities<TerrainMesh, TerrainEntity> { p, terrain, _ -> TerrainEntity(p, terrain, p.transform.toMatrix()) }

    val drawn: Collection<TerrainEntity> get() = entities.drawn

    val isLoading: Boolean get() = assets.isLoading

    fun update(placements: List<AssetPlacement>, projectDir: File?) {
        assets.update(projectDir, placements.mapTo(HashSet()) { it.assetName })
        entities.update(placements, assets::get)
    }

    /** Forgets everything without GL calls; see [AssetCache.abandon]. */
    fun abandon() {
        entities.clear()
        assets.abandon()
    }

    override fun dispose() {
        entities.clear()
        assets.dispose()
    }
}
