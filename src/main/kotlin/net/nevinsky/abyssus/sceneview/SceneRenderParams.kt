/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.ray.SceneRaySettingsState
import net.nevinsky.abyssus.editor.ray.SceneRaySettingsCodec

import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.content.Rgba

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.editor.document.SceneJson
import net.nevinsky.abyssus.core.scene.Scene
import net.nevinsky.abyssus.runtime.float
import net.nevinsky.abyssus.runtime.obj
import java.io.File
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** [direction] is the unit vector the camera looks along. */
data class CameraParams(
    val position: Vec3,
    val direction: Vec3,
    val near: Float,
    val far: Float,
    val fieldOfView: Float,
) {
    companion object {
        val DEFAULT = CameraParams(Vec3(8f, 6f, 8f), normalized(Vec3(-8f, -6f, -8f))!!, 0.1f, 1000f, 67f)
    }
}

/**
 * Native fog: the fog share at distance `d` is `1 - exp(-(d * density)^gradient)`.
 * g3d's default shader fogs with a fixed quadratic ramp, so only [density] can be matched, at its characteristic
 * distance `1 / density`; [gradient] shapes [amount] but not the on-screen curve.
 */
data class FogParams(val color: Rgba, val density: Float, val gradient: Float) {
    fun amount(distance: Float): Float =
        (1.0 - exp(-(distance.coerceAtLeast(0f) * density).toDouble().pow(gradient.toDouble()))).toFloat()
            .coerceIn(0f, 1f)

    /** Multiplier for g3d's `dot(d, d) * k` fog term that reaches [amount] at `1 / density` for any gradient. */
    val shaderCoefficient: Float get() = ((1.0 - exp(-1.0)) * density.toDouble() * density).toFloat()
}

/** [content] is what the scene places; [projectDir] is the folder of its `.abss`, whose `assets` folder holds the files. */
data class SceneRenderParams(
    val clear: Rgba,
    val ambient: Rgba?,
    val fog: FogParams?,
    val camera: CameraParams,
    val content: SceneContent = SceneContent.EMPTY,
    val projectDir: File? = null,
    /** The scene's `ecs` block as read, for scene overlays that draw components the view does not model. */
    val ecs: JsonNode? = null,
    val rayTracing: SceneRaySettingsState = SceneRaySettingsCodec().read(SceneJson.mapper.createObjectNode()),
) {
    companion object {
        val DEFAULT_CLEAR = Rgba(0.1f, 0.1f, 0.15f, 1f)
        val DEFAULT = SceneRenderParams(DEFAULT_CLEAR, null, null, CameraParams.DEFAULT)

        fun from(scene: Scene, camera: CameraParams, projectDir: File? = null): SceneRenderParams {
            val fog = fogOf(scene)
            return SceneRenderParams(
                fog?.color ?: DEFAULT_CLEAR,
                ambientOf(scene),
                fog,
                camera,
                SceneContent.of(scene),
                projectDir,
                scene.ecs,
                SceneRaySettingsCodec().read(SceneJson.mapper.createObjectNode().also { root ->
                    scene.rayTracing?.let { root.set<JsonNode>("rayTracing", SceneJson.mapper.valueToTree(it)) }
                }),
            )
        }

        private fun ambientOf(scene: Scene): Rgba? {
            if (scene.ambientLightEnabled != true) return null
            val light = scene.ambientLight ?: return null
            val c = light.color ?: return null
            val k = (light.intensity ?: 1f).coerceAtLeast(0f)
            return Rgba(c.r * k, c.g * k, c.b * k, 1f)
        }

        private fun fogOf(scene: Scene): FogParams? {
            if (scene.fogEnabled != true) return null
            val fog = scene.fog ?: return null
            val c = fog.color ?: return null
            val density = fog.density?.takeIf { it > 0f && it.isFinite() } ?: return null
            val gradient = fog.gradient?.takeIf { it > 0f && it.isFinite() } ?: 1f
            return FogParams(Rgba(c.r, c.g, c.b, 1f), density, gradient)
        }
    }
}

internal fun normalized(v: Vec3): Vec3? {
    val len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
    return if (len > 1e-6f && len.isFinite()) Vec3(v.x / len, v.y / len, v.z / len) else null
}

/** The `mainCamera` of the `.abss` project a scene belongs to. */
object MainCamera {
    fun parse(abssText: String): CameraParams? = runCatchingKeepingCancellation {
        val root = SceneJson.parse(abssText).takeIf { it.isObject } ?: return null
        if (net.nevinsky.abyssus.format.AbyssusDocumentFormat()
                .validate(root, net.nevinsky.abyssus.format.DocumentKind.PROJECT) != null
        ) return null
        val cam = root.obj("mainCamera") ?: return null
        val position = cam.vec("position") ?: return null
        val direction = normalized(cam.vec("viewPointPosition") ?: return null) ?: return null
        val defaults = CameraParams.DEFAULT
        val near = cam.float("near")?.takeIf { it > 0f && it.isFinite() }
        val far = cam.float("far")?.takeIf { it > 0f && it.isFinite() }
        val invertedClip = near != null && far != null && near >= far
        CameraParams(
            position,
            direction,
            if (invertedClip) defaults.near else near ?: defaults.near,
            if (invertedClip) defaults.far else far ?: defaults.far,
            cam.float("fieldOfView")?.takeIf { it > 0f && it < 180f } ?: defaults.fieldOfView,
        )
    }.getOrNull()

    private fun JsonNode.vec(name: String): Vec3? {
        val o = obj(name) ?: return null
        return Vec3(o.float("x") ?: 0f, o.float("y") ?: 0f, o.float("z") ?: 0f)
    }
}
