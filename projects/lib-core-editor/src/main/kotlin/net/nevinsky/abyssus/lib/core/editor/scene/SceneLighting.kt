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
import net.nevinsky.abyssus.lib.core.dto.LIGHT_RANGE
import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.content.LightPlacement
import net.nevinsky.abyssus.lib.core.editor.content.unclampedColor
import net.nevinsky.abyssus.lib.core.util.EDITOR_MAX_DIRECTIONAL
import net.nevinsky.abyssus.lib.core.util.EDITOR_MAX_LOCAL
import net.nevinsky.abyssus.lib.core.util.EDITOR_MAX_POINT
import net.nevinsky.abyssus.lib.core.util.EDITOR_MAX_SPOT
import kotlin.math.sqrt

/** A directional light: [color] already multiplied by the light's intensity. */
data class DirectionalSource(
    val direction: Vector3,
    val color: Color,
    val entityId: String = "",
    val position: Vector3 = Vector3(0f, 0f, 0f)
)

/** A point light: [color] already multiplied by the light's intensity. */
data class PointSource(val position: Vector3, val color: Color, val range: Float = LIGHT_RANGE, val entityId: String = "")

data class SpotSource(
    val entityId: String,
    val position: Vector3,
    val direction: Vector3,
    val color: Color,
    val range: Float,
    val cone: SpotCone
)

/**
 * The light entities of a scene as the renderers use them, limited to what the shaders support
 * ([MAX_DIRECTIONAL] / [MAX_POINT], configured shader limits).
 *
 * Point and spot lights share the local-light ceiling.
 */
class LightSet(
    val directional: List<DirectionalSource>,
    val point: List<PointSource>,
    val spot: List<SpotSource> = emptyList()
) {
    /**
     * The index among [directional] (the order the shaders see them in) of the light of entity [entityId]; null when
     * there is no such entity or it did not make the [MAX_DIRECTIONAL] budget.
     */
    fun directionalIndexOf(entityId: String?): Int? =
        entityId?.let { id -> directional.indexOfFirst { it.entityId == id }.takeIf { it >= 0 } }

    /**
     * This set with the color of the directional light of entity [entityId] (the sun) times [scale], as clouds over
     * the sun dim it; every other light is the same. This set itself when [scale] is 1 or the sun is not in it.
     */
    fun withSunScale(entityId: String?, scale: Float): LightSet {
        if (entityId == null || scale == 1f || directional.none { it.entityId == entityId }) return this
        val scaled = directional.map {
            if (it.entityId != entityId) it else it.copy(
                color = unclampedColor(
                    it.color.r * scale,
                    it.color.g * scale,
                    it.color.b * scale,
                    it.color.a
                )
            )
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
                DirectionalLight().set(
                    Color(d.color.r, d.color.g, d.color.b, 1f),
                    d.direction.x,
                    d.direction.y,
                    d.direction.z
                )
            )
        }
        for (p in point) {
            environment.add(
                PointLight().set(
                    Color(p.color.r, p.color.g, p.color.b, 1f),
                    p.position.x,
                    p.position.y,
                    p.position.z,
                    p.range
                )
            )
        }
        for (s in spot) {
            environment.add(
                SpotLight().set(
                    Color(s.color.r, s.color.g, s.color.b, 1f),
                    Vector3(s.position.x, s.position.y, s.position.z),
                    Vector3(s.direction.x, s.direction.y, s.direction.z),
                    s.range,
                    s.cone.angle / 2f,
                    s.cone.softness
                )
            )
        }
    }
}

const val MAX_DIRECTIONAL = EDITOR_MAX_DIRECTIONAL

const val MAX_POINT = EDITOR_MAX_POINT

const val MAX_SPOT = EDITOR_MAX_SPOT

val NO_LIGHTS = LightSet(emptyList(), emptyList())

/** Nearest lights with stable identity ties; unusable lights are skipped. */
fun lightSetOf(lights: List<LightPlacement>, target: Vector3): LightSet {
    val directional = lights.filter { it.kind == LightKind.DIRECTIONAL && usable(it) }
        .sortedWith(compareBy<LightPlacement> { distance(it.position, target) }.thenBy { it.entityId })
        .take(MAX_DIRECTIONAL)
        .map { DirectionalSource(it.direction, scaled(it), it.entityId, it.position) }
    val local = lights.filter { it.kind != LightKind.DIRECTIONAL && usable(it) }
        .sortedWith(compareBy<LightPlacement> { distance(it.position, target) }.thenBy { it.entityId })
        .take(EDITOR_MAX_LOCAL)
    val point =
        local.filter { it.kind == LightKind.POINT }.map { PointSource(it.position, scaled(it), it.range, it.entityId) }
    val spot = local.filter { it.kind == LightKind.SPOT }.map {
        SpotSource(
            it.entityId,
            it.position,
            it.direction,
            scaled(it),
            it.range,
            SpotCone(it.coneAngle, it.edgeSoftness)
        )
    }
    return LightSet(directional, point, spot)
}

private fun usable(l: LightPlacement) =
    l.intensity > 0f && l.intensity.isFinite() && l.range > 0f && l.range.isFinite() &&
            (l.kind != LightKind.SPOT || (l.coneAngle.isFinite() && l.coneAngle > 0f && l.coneAngle < 180f &&
                    l.edgeSoftness.isFinite() && l.edgeSoftness in 0f..1f &&
                    (l.direction.x != 0f || l.direction.y != 0f || l.direction.z != 0f))) &&
            listOf(
                l.color.r,
                l.color.g,
                l.color.b,
                l.position.x,
                l.position.y,
                l.position.z,
                l.direction.x,
                l.direction.y,
                l.direction.z
            )
                .all { it.isFinite() }

private fun scaled(l: LightPlacement) =
    unclampedColor(l.color.r * l.intensity, l.color.g * l.intensity, l.color.b * l.intensity, 1f)

private fun distance(a: Vector3, b: Vector3): Float = a.dst(b)

