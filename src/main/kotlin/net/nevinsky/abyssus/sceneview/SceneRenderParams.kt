/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.filetype.SceneJson
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.assets.json.float
import net.nevinsky.abyssus.assets.json.obj
import net.nevinsky.abyssus.runtime.scene.SceneDto
import java.io.File
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation

data class Vec3(val x: Float, val y: Float, val z: Float)

data class Rgba(val r: Float, val g: Float, val b: Float, val a: Float)

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
 * Mundus fog: the fog share at distance `d` is `1 - exp(-(d * density)^gradient)`.
 * g3d's default shader fogs with a fixed quadratic ramp, so only [density] can be matched, at its characteristic
 * distance `1 / density`; [gradient] shapes [amount] but not the on-screen curve.
 */
data class FogParams(val color: Rgba, val density: Float, val gradient: Float) {
    fun amount(distance: Float): Float =
        (1.0 - exp(-(distance.coerceAtLeast(0f) * density).toDouble().pow(gradient.toDouble()))).toFloat().coerceIn(0f, 1f)

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
) {
    companion object {
        val DEFAULT_CLEAR = Rgba(0.1f, 0.1f, 0.15f, 1f)
        val DEFAULT = SceneRenderParams(DEFAULT_CLEAR, null, null, CameraParams.DEFAULT)

        fun from(scene: SceneDto, camera: CameraParams, projectDir: File? = null): SceneRenderParams {
            val fog = fogOf(scene)
            return SceneRenderParams(fog?.color ?: DEFAULT_CLEAR, ambientOf(scene), fog, camera, SceneContent.of(scene), projectDir)
        }

        private fun ambientOf(scene: SceneDto): Rgba? {
            if (scene.ambientLightEnabled != true) return null
            val light = scene.ambientLight ?: return null
            val c = light.color ?: return null
            val k = (light.intensity ?: 1f).coerceAtLeast(0f)
            return Rgba(c.r * k, c.g * k, c.b * k, 1f)
        }

        private fun fogOf(scene: SceneDto): FogParams? {
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
