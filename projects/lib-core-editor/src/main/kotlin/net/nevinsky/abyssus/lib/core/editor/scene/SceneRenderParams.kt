/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.ecs.component.CameraDto
import net.nevinsky.abyssus.lib.core.editor.content.unclampedColor

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.graphics.Color

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.util.float
import java.io.File
import net.nevinsky.abyssus.lib.core.util.quadraticFogCoefficient
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** [direction] is the unit vector the camera looks along. */
data class CameraParams(
    val position: Vector3,
    val direction: Vector3,
    val near: Float,
    val far: Float,
    val fieldOfView: Float,
) {
    companion object {
        val DEFAULT = CameraParams(Vector3(8f, 6f, 8f), normalized(Vector3(-8f, -6f, -8f))!!, 0.1f, 1000f, 67f)
    }
}

/**
 * Native fog: the fog share at distance `d` is `1 - exp(-(d * density)^gradient)`.
 * g3d's default shader fogs with a fixed quadratic ramp, so only [density] can be matched, at its characteristic
 * distance `1 / density`; [gradient] shapes [amount] but not the on-screen curve.
 */
data class FogParams(val color: Color, val density: Float, val gradient: Float) {
    fun amount(distance: Float): Float =
        (1.0 - exp(-(distance.coerceAtLeast(0f) * density).toDouble().pow(gradient.toDouble()))).toFloat()
            .coerceIn(0f, 1f)

    /** Multiplier for g3d's `dot(d, d) * k` fog term that reaches [amount] at `1 / density` for any gradient. */
    val shaderCoefficient: Float get() = quadraticFogCoefficient(density)
}

/** [content] is what the scene places; [projectDir] is the folder of its `.abss`, whose `assets` folder holds the files. */
data class SceneRenderParams(
    val clear: Color,
    val ambient: Color?,
    val fog: FogParams?,
    val camera: CameraParams,
    val content: SceneContent = SceneContent(),
    val projectDir: File? = null,
    /** The scene's typed entities, for scene overlays that draw components the view does not model. */
    val ecs: Map<Long, EntityDto> = emptyMap(),
    val rayTracing: SceneRaySettings = SceneRaySettings(),
) {
    companion object {
        val DEFAULT_CLEAR = Color(0.1f, 0.1f, 0.15f, 1f)
        val DEFAULT = SceneRenderParams(DEFAULT_CLEAR, null, null, CameraParams.DEFAULT)
    }
}

/**
 * The view parameters of [scene], seen through [camera]; [projectDir] holds its assets. The ray settings are the defaults:
 * a scene does not save them.
 */
fun renderParamsOf(scene: SceneDto, camera: CameraParams, projectDir: File? = null): SceneRenderParams {
    val fog = fogOf(scene)
    return SceneRenderParams(
        fog?.color ?: SceneRenderParams.DEFAULT_CLEAR,
        ambientOf(scene),
        fog,
        camera,
        sceneContentOf(scene),
        projectDir,
        scene.ecs,
    )
}

private fun ambientOf(scene: SceneDto): Color? {
    if (scene.ambientLightEnabled != true) return null
    val light = scene.ambientLight ?: return null
    val c = light.color ?: return null
    val k = (light.intensity ?: 1f).coerceAtLeast(0f)
    return unclampedColor(c.r * k, c.g * k, c.b * k, 1f)
}

private fun fogOf(scene: SceneDto): FogParams? {
    if (scene.fogEnabled != true) return null
    val fog = scene.fog ?: return null
    val c = fog.color ?: return null
    val density = fog.density?.takeIf { it > 0f && it.isFinite() } ?: return null
    val gradient = fog.gradient?.takeIf { it > 0f && it.isFinite() } ?: 1f
    return FogParams(Color(c.r, c.g, c.b, 1f), density, gradient)
}

internal fun normalized(v: Vector3): Vector3? {
    val len = v.len()
    return if (len > 1e-6f && len.isFinite()) Vector3(v).scl(1f / len) else null
}

/** The part of an `.abss` project file [MainCamera] reads. */
private data class MainCameraDocument(val mainCamera: CameraDto? = null)

/** The `mainCamera` of the `.abss` project a scene belongs to. */
class MainCamera(
    private val json: JsonProcessor,
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
) {
    fun parse(abssText: String): CameraParams? = runCatchingKeepingCancellation {
        val root = json.readObject(abssText)
        if (format.validate(root, DocumentKind.PROJECT) != null) return null
        val cam = json.bind(root, MainCameraDocument::class.java).mainCamera ?: return null
        val position = cam.position ?: return null
        val direction = normalized(cam.viewPointPosition ?: return null) ?: return null
        val defaults = CameraParams.DEFAULT
        val near = cam.near?.takeIf { it > 0f && it.isFinite() }
        val far = cam.far?.takeIf { it > 0f && it.isFinite() }
        val invertedClip = near != null && far != null && near >= far
        CameraParams(
            position,
            direction,
            if (invertedClip) defaults.near else near ?: defaults.near,
            if (invertedClip) defaults.far else far ?: defaults.far,
            cam.fieldOfView?.takeIf { it > 0f && it < 180f } ?: defaults.fieldOfView,
        )
    }.getOrNull()
}
