/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.environment.SpotLight
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.component.LIGHT_RANGE
import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.content.LightPlacement
import net.nevinsky.abyssus.lib.core.editor.content.unclampedColor
import net.nevinsky.abyssus.lib.core.light.scaledColor
import net.nevinsky.abyssus.lib.core.light.selectLights
import net.nevinsky.abyssus.lib.core.util.EDITOR_MAX_DIRECTIONAL
import net.nevinsky.abyssus.lib.core.util.EDITOR_MAX_LOCAL
import net.nevinsky.abyssus.lib.core.util.EDITOR_MAX_POINT
import net.nevinsky.abyssus.lib.core.util.EDITOR_MAX_SPOT

/** A directional light: [color] already multiplied by the light's intensity. */
data class DirectionalSource(
    val direction: Vector3,
    val color: Color,
    val entityId: Long = -1L,
    val position: Vector3 = Vector3(0f, 0f, 0f),
)

/** A point light: [color] already multiplied by the light's intensity. */
data class PointSource(
    val position: Vector3,
    val color: Color,
    val range: Float = LIGHT_RANGE,
    val entityId: Long = -1L,
)

data class SpotSource(
    val entityId: Long,
    val position: Vector3,
    val direction: Vector3,
    val color: Color,
    val range: Float,
    val cone: SpotCone,
)

/**
 * The light entities of a scene as the renderers use them, limited to what the shaders support ([MAX_DIRECTIONAL] /
 * [MAX_POINT], configured shader limits).
 *
 * Point and spot lights share the local-light ceiling.
 */
class LightSet(
    val directional: List<DirectionalSource>,
    val point: List<PointSource>,
    val spot: List<SpotSource> = emptyList(),
) {
    /**
     * The index among [directional] (the order the shaders see them in) of the light of entity [entityId]; null when
     * there is no such entity or it did not make the [MAX_DIRECTIONAL] budget.
     */
    fun directionalIndexOf(entityId: Long?): Int? = entityId?.let { id ->
        directional.indexOfFirst { it.entityId == id }.takeIf { it >= 0 }
    }

    /**
     * This set with the color of the directional light of entity [entityId] (the sun) times [scale], as clouds over the
     * sun dim it; every other light is the same. This set itself when [scale] is 1 or the sun is not in it.
     */
    fun withSunScale(entityId: Long?, scale: Float): LightSet {
        if (entityId == null || scale == 1f || directional.none { it.entityId == entityId }) {
            return this
        }
        val scaled = directional.map {
            if (it.entityId != entityId) {
                it
            } else {
                it.copy(
                    color =
                        unclampedColor(
                            it.color.r * scale,
                            it.color.g * scale,
                            it.color.b * scale,
                            it.color.a,
                        )
                )
            }
        }
        return LightSet(scaled, point, spot)
    }

    /** Replaces the directional and point lights of [environment]; the ambient light is left alone. */
    fun applyTo(environment: Environment) {
        environment.remove(DirectionalLightsAttribute.Type)
        environment.remove(PointLightsAttribute.Type)
        environment.remove(SpotLightsAttribute.Type)
        for (d in directional) {
            environment.add(
                DirectionalLight()
                    .set(
                        Color(d.color.r, d.color.g, d.color.b, 1f),
                        d.direction.x,
                        d.direction.y,
                        d.direction.z,
                    )
            )
        }
        for (p in point) {
            environment.add(
                PointLight()
                    .set(
                        unclampedColor(p.color.r, p.color.g, p.color.b, 1f),
                        p.position.x,
                        p.position.y,
                        p.position.z,
                        p.range,
                    )
            )
        }
        for (s in spot) {
            environment.add(
                SpotLight()
                    .set(
                        unclampedColor(s.color.r, s.color.g, s.color.b, 1f),
                        Vector3(s.position.x, s.position.y, s.position.z),
                        Vector3(s.direction.x, s.direction.y, s.direction.z),
                        s.range,
                        s.cone.angle / 2f,
                        s.cone.softness,
                    )
            )
        }
    }
}

const val MAX_DIRECTIONAL = EDITOR_MAX_DIRECTIONAL

const val MAX_POINT = EDITOR_MAX_POINT

const val MAX_SPOT = EDITOR_MAX_SPOT

val NO_LIGHTS = LightSet(emptyList(), emptyList())

/**
 * Nearest lights with stable identity ties; unusable lights are skipped. The rules are `lib-core`'s ([selectLights]),
 * which games use too.
 */
fun lightSetOf(lights: List<LightPlacement>, target: Vector3): LightSet {
    val selected = selectLights(lights, target, MAX_DIRECTIONAL, EDITOR_MAX_LOCAL)
    val directional =
        selected.directional.map { DirectionalSource(it.direction, scaledColor(it), it.entityId, it.position) }
    val point =
        selected.local
            .filter { it.kind == LightKind.POINT }
            .map { PointSource(it.position, scaledColor(it), it.range, it.entityId) }
    val spot =
        selected.local
            .filter { it.kind == LightKind.SPOT }
            .map {
                SpotSource(
                    it.entityId,
                    it.position,
                    it.direction,
                    scaledColor(it),
                    it.range,
                    SpotCone(it.coneAngle, it.edgeSoftness),
                )
            }
    return LightSet(directional, point, spot)
}
