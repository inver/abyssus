/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.loading.SceneAssets
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.assets.terrain.PreparedTerrain
import net.nevinsky.abyssus.assets.terrain.TerrainMesh
import java.io.File

class TerrainEntity(override val placement: AssetPlacement, val terrain: TerrainMesh, val world: Matrix4) : PlacedEntity<TerrainMesh> {
    override val asset: TerrainMesh get() = terrain
}

/** The terrain entities of the scene, loaded like [SceneModels]. GL thread only. */
class SceneTerrains(private val assets: SceneAssets<PreparedTerrain, TerrainMesh>) : Disposable {
    private val entities = PlacedEntities<TerrainMesh, TerrainEntity> { p, terrain, _ -> TerrainEntity(p, terrain, p.transform.toMatrix()) }

    val drawn: Collection<TerrainEntity> get() = entities.drawn

    val isLoading: Boolean get() = assets.isLoading

    fun update(placements: List<AssetPlacement>, projectDir: File?) {
        assets.update(projectDir, placements.mapTo(HashSet()) { it.assetName })
        entities.update(placements, assets::get)
    }

    /** Loads [names] again from [files], the project's refreshed snapshot; the old assets stay drawn until each replacement is built. */
    fun revise(files: AssetFiles, names: Set<String>) {
        assets.replaceFiles(files)
        assets.invalidate(names)
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
