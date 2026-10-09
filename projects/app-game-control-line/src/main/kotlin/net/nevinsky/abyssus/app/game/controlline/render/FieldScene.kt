/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.PilotComponent
import net.nevinsky.abyssus.app.game.controlline.flow.PlaneChoice
import net.nevinsky.abyssus.app.game.controlline.flow.planeChoices
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.TypeComponent
import net.nevinsky.abyssus.lib.core.util.EcsUtils
import net.nevinsky.abyssus.lib.core.scene.SceneContext

/** The field scene's file in the project. */
const val FIELD_SCENE = "scenes/Field.scene"

/**
 * One load of the field scene, as the game draws it (no GL): its entities, the sky, the sun, ambient light and fog, and
 * the planes for plane select. A flight gets its own load, so the parked planes are parked again afterward.
 */
class FieldScene(val loaded: SceneContext) {
    val engine = loaded.engine
    private val entities: Map<Int, Entity> = engine.ids.ids.sorted().associateWith { engine.ids[it]!! }

    val planes: List<PlaneChoice> = planeChoices(entities)
    val pilot: Entity? = entities.values.firstOrNull { it.getComponent(PilotComponent::class.java) != null }

    fun entity(id: Int): Entity? = entities[id]

    /** Entities drawn with a model: (entity, model asset folder). */
    val models: List<Pair<Entity, String>> =
        entities.values.mapNotNull { e -> EcsUtils.assetName(e, MetaType.MODEL)?.let { e to it } }

    /** Entities drawn as terrain: (entity, terrain asset folder). */
    val terrains: List<Pair<Entity, String>> =
        entities.values.mapNotNull { e -> EcsUtils.assetName(e, MetaType.TERRAIN)?.let { e to it } }

    val skyName: String? = loaded.scene.skyboxName.takeIf { loaded.scene.skyboxEnabled }

    /** The unit vector toward the sun: against the first directional light's direction, 45° up without one. */
    val sunDirection: Vector3 =
        entities.values.firstOrNull { it.getComponent(TypeComponent::class.java)?.type == TypeComponent.Type.LIGHT_DIRECTIONAL }
            ?.let(::lightDirection)?.scl(-1f) ?: Vector3(0f, 1f, 1f).nor()

    val sunColor: Color =
        entities.values.firstOrNull { it.getComponent(TypeComponent::class.java)?.type == TypeComponent.Type.LIGHT_DIRECTIONAL }
            ?.getComponent(LightComponent::class.java)?.light?.let { color(it.color, it.intensity) } ?: Color(
            1f,
            1f,
            1f,
            1f
        )

    val ambient: Color = loaded.scene.ambientLight?.takeIf { loaded.scene.ambientLightEnabled }
        ?.let { color(it.color ?: Color(1f, 1f, 1f, 1f), it.intensity ?: 0.3f) }
        ?: Color(0.3f, 0.3f, 0.3f, 1f)

    val fogColor: Color? = loaded.scene.fog?.takeIf { loaded.scene.fogEnabled }
        ?.color?.let { Color(it.r, it.g, it.b, 1f) }
    val fogDensity: Float = loaded.scene.fog?.density ?: 0f
    val fogGradient: Float = loaded.scene.fog?.gradient ?: 1.5f

    fun position(entity: Entity): PositionComponent =
        entity.getComponent(PositionComponent::class.java) ?: PositionComponent()

    /** Where a light shines: toward its look-at entity, else along its rotated -Z. */
    private fun lightDirection(light: Entity): Vector3? {
        val p = light.getComponent(PositionComponent::class.java) ?: return null
        val target = entities[p.lookAtId]?.getComponent(PositionComponent::class.java)
        val toward = target?.let { Vector3(it.localPosition).sub(p.localPosition) }?.takeIf { !it.isZero }
        return (toward ?: p.localRotation.transform(Vector3(0f, 0f, -1f))).nor()
    }

    private fun color(c: Color, intensity: Float) = Color(c.r * intensity, c.g * intensity, c.b * intensity, 1f)
}
