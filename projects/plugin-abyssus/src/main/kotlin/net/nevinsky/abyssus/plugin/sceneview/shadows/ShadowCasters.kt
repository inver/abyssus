/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.shadows

import net.nevinsky.abyssus.lib.core.editor.scene.ModelEntity
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool
import net.nevinsky.abyssus.lib.gdx.Renderable
import net.nevinsky.abyssus.plugin.sceneview.TerrainEntity

/**
 * What a view's foliage gives the shadow depth pass: the copies of its OBJECT layers, which cast a shadow, and never
 * those of its DETAIL layers, which only receive one (design decision 5).
 */
interface FoliageCastSource {
    /** Adds one renderable per instanced part of the drawn OBJECT layers; a DETAIL layer's parts stay out. */
    fun getCastRenderables(out: Array<Renderable>, pool: Pool<Renderable>)

    /** The world box those copies stand in, or null when the view draws no OBJECT-layer copies. Read-only. */
    fun castBounds(): BoundingBox?
}

/** One caster of the depth pass: a renderable, and the box its frustum test and the light's fit use for it. */
internal data class ShadowCaster(val renderable: Renderable, val bounds: BoundingBox)

/**
 * What reaches the shadow depth pass: every part of every drawn model, the depth renderable of every terrain, and the
 * instanced parts of the foliage's OBJECT layers. What is collected also lands in [renderables], so the caller's pool
 * frees it again after the frame; [casterBounds] caches the world boxes of the model parts, which it computes once
 * per mesh. Foliage renderables are skipped there on purpose: their copies carry their transforms in the instance
 * attributes, so their box comes from [FoliageCastSource] instead.
 */
internal fun shadowCasters(
    models: Collection<ModelEntity>,
    terrains: Collection<TerrainEntity>,
    foliage: FoliageCastSource?,
    renderables: Array<Renderable>,
    pool: Pool<Renderable>,
    casterBounds: ShadowCasterBounds,
): List<ShadowCaster> {
    val casters = mutableListOf<ShadowCaster>()
    models.forEach { entity ->
        val first = renderables.size
        entity.instance.getRenderables(renderables, pool)
        for (i in first until renderables.size) casters += ShadowCaster(renderables[i], casterBounds.world(renderables[i]))
    }
    casterBounds.retain(renderables.mapNotNull { it.meshPart.mesh }.toSet())
    terrains.forEach { entity ->
        val out = pool.obtain().also { it.cleanup() }
        entity.terrain.depthRenderable(entity.world, out); renderables.add(out)
        val data = entity.terrain.data
        casters += ShadowCaster(out, BoundingBox(
            Vector3(0f, data.heights.min(), 0f),
            Vector3(data.size.toFloat(), data.heights.max(), data.size.toFloat()),
        ).mul(entity.world))
    }
    if (foliage != null) {
        val bounds = foliage.castBounds()
        val first = renderables.size
        foliage.getCastRenderables(renderables, pool)
        if (bounds != null) {
            // every part draws the same copies, so they all share the box those copies stand in
            for (i in first until renderables.size) casters += ShadowCaster(renderables[i], bounds)
        }
    }
    return casters
}
