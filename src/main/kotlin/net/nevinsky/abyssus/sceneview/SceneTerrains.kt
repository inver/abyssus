/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.assets.loading.SceneAssets
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.assets.terrain.PreparedTerrain
import net.nevinsky.abyssus.assets.terrain.TerrainMesh

class TerrainEntity(override val placement: AssetPlacement, val terrain: TerrainMesh, val world: Matrix4) : PlacedEntity<TerrainMesh> {
    override val asset: TerrainMesh get() = terrain

    /** The terrain's bounds in its own space, computed on first use (the heights are scanned once, not per click). */
    val localBounds: BoundingBox by lazy {
        val data = terrain.data
        BoundingBox(Vector3(0f, data.heights.min(), 0f), Vector3(data.size.toFloat(), data.heights.max(), data.size.toFloat()))
    }
}

/** The terrain entities of the scene, loaded like [SceneModels]. GL thread only. */
class SceneTerrains(assets: SceneAssets<PreparedTerrain, TerrainMesh>) : PlacedAssets<PreparedTerrain, TerrainMesh, TerrainEntity>(
    assets,
    { p, terrain, _ -> TerrainEntity(p, terrain, p.transform.toMatrix()) },
)
