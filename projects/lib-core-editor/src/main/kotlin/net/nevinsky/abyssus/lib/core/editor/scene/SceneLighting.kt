/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.Rgba
import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.content.LightPlacement

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.environment.SpotLight
import com.badlogic.gdx.math.Vector3
import kotlin.math.sqrt
import net.nevinsky.abyssus.lib.runtime.ecs.EcsUtils.Companion.LIGHT_RANGE

/** A directional light: [color] already multiplied by the light's intensity. */
data class DirectionalSource(val direction: Vec3, val color: Rgba, val entityId: String = "", val position: Vec3 = Vec3(0f, 0f, 0f))

/** A point light: [color] already multiplied by the light's intensity. */
data class PointSource(val position: Vec3, val color: Rgba, val range: Float = LIGHT_RANGE, val entityId: String = "")

data class SpotSource(val entityId: String, val position: Vec3, val direction: Vec3, val color: Rgba, val range: Float, val cone: SpotCone)

/**
 * The light entities of a scene as the renderers use them, limited to what the shaders support
 * ([MAX_DIRECTIONAL] / [MAX_POINT], configured shader limits).
 *
 * Point and spot lights share the local-light ceiling.
 */
class LightSet(val directional: List<DirectionalSource>, val point: List<PointSource>, val spot: List<SpotSource> = emptyList()) {
    /** Replaces the directional and point lights of [environment]; the ambient light is left alone. */
    fun applyTo(environment: Environment) {
        environment.remove(DirectionalLightsAttribute.Type)
        environment.remove(PointLightsAttribute.Type)
        environment.remove(SpotLightsAttribute.Type)
        for (d in directional) {
            environment.add(DirectionalLight().set(Color(d.color.r, d.color.g, d.color.b, 1f), d.direction.x, d.direction.y, d.direction.z))
        }
        for (p in point) {
            environment.add(PointLight().set(Color(p.color.r, p.color.g, p.color.b, 1f), p.position.x, p.position.y, p.position.z, p.range))
        }
        for (s in spot) {
            environment.add(SpotLight().set(
                Color(s.color.r, s.color.g, s.color.b, 1f),
                Vector3(s.position.x, s.position.y, s.position.z), Vector3(s.direction.x, s.direction.y, s.direction.z),
                s.range, s.cone.angle / 2f, s.cone.softness))
        }
    }
}

const val MAX_DIRECTIONAL = 2

const val MAX_POINT = 5

val NO_LIGHTS = LightSet(emptyList(), emptyList())

/** Nearest lights with stable identity ties; unusable lights are skipped. */
fun lightSetOf(lights: List<LightPlacement>, target: Vec3): LightSet {
    val directional = lights.filter { it.kind == LightKind.DIRECTIONAL && usable(it) }
        .sortedWith(compareBy<LightPlacement> { distance(it.position, target) }.thenBy { it.entityId })
        .take(MAX_DIRECTIONAL)
        .map { DirectionalSource(it.direction, scaled(it), it.entityId, it.position) }
    val local = lights.filter { it.kind != LightKind.DIRECTIONAL && usable(it) }
        .sortedWith(compareBy<LightPlacement> { distance(it.position, target) }.thenBy { it.entityId })
        .take(MAX_POINT)
    val point = local.filter { it.kind == LightKind.POINT }.map { PointSource(it.position, scaled(it), it.range, it.entityId) }
    val spot = local.filter { it.kind == LightKind.SPOT }.map {
        SpotSource(it.entityId, it.position, it.direction, scaled(it), it.range, SpotCone(it.coneAngle, it.edgeSoftness))
    }
    return LightSet(directional, point, spot)
}

private fun usable(l: LightPlacement) = l.intensity > 0f && l.intensity.isFinite() && l.range > 0f && l.range.isFinite() &&
    (l.kind != LightKind.SPOT || (l.coneAngle.isFinite() && l.coneAngle > 0f && l.coneAngle < 180f &&
        l.edgeSoftness.isFinite() && l.edgeSoftness in 0f..1f &&
        (l.direction.x != 0f || l.direction.y != 0f || l.direction.z != 0f))) &&
    listOf(l.color.r, l.color.g, l.color.b, l.position.x, l.position.y, l.position.z, l.direction.x, l.direction.y, l.direction.z)
        .all { it.isFinite() }

private fun scaled(l: LightPlacement) = Rgba(l.color.r * l.intensity, l.color.g * l.intensity, l.color.b * l.intensity, 1f)

private fun distance(a: Vec3, b: Vec3): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    val dz = a.z - b.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

