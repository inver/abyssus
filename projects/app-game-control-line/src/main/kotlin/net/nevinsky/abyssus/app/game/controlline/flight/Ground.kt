/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.physics.ColliderComponent
import net.nevinsky.abyssus.lib.physics.ColliderShape
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.runtime.ecs.scene.SceneEngine

/**
 * The field's ground: the height of the height-field terrain under a point, as the physics sees it ([field]'s terrain
 * at its position and scale, rotation ignored). Without a terrain, or outside it, the ground is at 0.
 */
class Ground(val field: Entity?, private val terrain: TerrainData?) {
    private val origin = field?.getComponent(PositionComponent::class.java)?.localPosition?.cpy() ?: Vector3()
    private val scale = field?.getComponent(PositionComponent::class.java)?.localScale?.cpy() ?: Vector3(1f, 1f, 1f)

    fun heightAt(x: Float, z: Float): Float {
        val data = terrain ?: return 0f
        val h = data.heightAt((x - origin.x) / scale.x, (z - origin.z) / scale.z) ?: return 0f
        return origin.y + h * scale.y
    }
}

/** The ground of [engine]'s scene: its first entity with a height-field collider and its terrain, read through [assets]. */
fun groundOf(engine: SceneEngine, assets: PhysicsAssets): Ground {
    val field = engine.ids.ids.sorted().mapNotNull { engine.ids[it] }.firstOrNull {
        it.getComponent(ColliderComponent::class.java)?.shape == ColliderShape.HEIGHT_FIELD
    }
    val terrain = field?.let { assets.assetName(it, MetaType.TERRAIN) }?.let(assets::terrain)
    return Ground(field, terrain)
}
