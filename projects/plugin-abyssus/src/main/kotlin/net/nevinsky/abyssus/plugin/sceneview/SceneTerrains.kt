/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.scene.PlacedEntity
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh

class TerrainEntity(override val placement: AssetPlacement, val terrain: TerrainMesh, val world: Matrix4) : PlacedEntity<TerrainMesh> {
    override val asset: TerrainMesh get() = terrain

    private var bounds: BoundingBox? = null

    /** The terrain's bounds in its own space, computed on first use (the heights are scanned once, not per click). */
    val localBounds: BoundingBox
        get() = bounds ?: terrain.data.let { data ->
            BoundingBox(Vector3(0f, data.heights.min(), 0f), Vector3(data.size.toFloat(), data.heights.max(), data.size.toFloat()))
        }.also { bounds = it }

    /** The heights changed (a sculpting stroke): the bounds are scanned again on next use. */
    fun heightsChanged() {
        bounds = null
    }

    /** This entity as the splat sync sees it: the view's own splat texture, updated in place for a changed rectangle. */
    internal fun splatTarget(updater: net.nevinsky.abyssus.lib.core.assets.terrain.SplatTexture): SplatTarget = object : SplatTarget {
        override val key: Any get() = terrain
        override val splatAsset: String? get() = terrain.splatAsset()
        override val textureKey: Any? get() = terrain.splatTexture()
        override fun upload(draft: net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatDraft, rect: net.nevinsky.abyssus.lib.core.editor.brush.GridRect): Boolean {
            val texture = terrain.splatTexture() ?: return false
            if (texture.width != draft.resolution || texture.height != draft.resolution) return false
            updater.updateRegion(texture, draft.pixels, rect.minX, rect.minZ, rect.width, rect.height)
            return true
        }
    }

    /** This entity as the sculpt sync sees it: its own mesh and data, sent again for a changed rectangle. */
    internal fun sculptTarget(): SculptTarget = object : SculptTarget {
        override val key: Any get() = terrain
        override val assetName: String get() = placement.assetName
        override val data get() = terrain.data
        override fun refresh(rect: net.nevinsky.abyssus.lib.core.editor.brush.GridRect) {
            terrain.updateRegion(rect.minX, rect.minZ, rect.maxX, rect.maxZ)
            heightsChanged()
        }
    }
}

/** The terrain entities of the scene, loaded like [SceneModels]. GL thread only. */
class SceneTerrains(assets: AssetView<TerrainMesh>) : PlacedAssets<TerrainMesh, TerrainEntity>(
    assets,
    { p, terrain, _ -> TerrainEntity(p, terrain, p.transform.toMatrix()) },
)
