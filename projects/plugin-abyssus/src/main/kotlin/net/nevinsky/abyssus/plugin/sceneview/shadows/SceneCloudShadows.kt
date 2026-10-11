/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.shadows

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.CloudRevision
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.LightingFrame
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.LightingRefreshPolicy
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.SKY_CAMERA_HEIGHT
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSky
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.ray.RayCloudShadowInput
import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.gdx.shader.DirectionalVisibilityAttribute
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger

/**
 * The index in the shaders' directional light list of the sun, the light of entity [sunId], or -1 when the sun is not in
 * [lights] (it has no usable direction, or lost the directional light budget to brighter ones): then no other light may
 * take its place.
 */
fun sunLightIndex(lights: LightSet, sunId: String?): Int =
    if (sunId == null) -1 else lights.directional.indexOfFirst { it.entityId == sunId }

/**
 * Cloud shadows for one view: owns the [CloudShadowMap] and says, each frame, whether it applies and with what. It applies
 * while the sky is a procedural sky that still has clouds to draw, the sun is in the rendered directional lights, and the
 * map can be drawn; [visibility] then describes it for the shaders (the sun only, with the uniform dimming as the
 * visibility beyond the map) and null otherwise, in which case the caller dims the sun uniformly as before. GL thread only.
 */
class SceneCloudShadows(
    private val shaders: ShaderStorage,
    private val log: Logger = NOPLogger.NOP_LOGGER,
) : Disposable {
    private var map: CloudShadowMap? = null

    /** What the shaders multiply the sun with this frame; null when the cloud shadows do not apply. */
    var visibility: DirectionalVisibilityAttribute? = null
        private set

    /**
     * Updates the map for [sky] and the sun ([sunId], toward [sun]) around ([targetX], [targetZ]) at [timeSeconds] and
     * returns whether it applies. [uniformScale] (the smoothed uniform sun dimming) is what the shaders use beyond the map.
     */
    fun update(
        sky: ProceduralSky?, lights: LightSet, sunId: String?, sun: Vec3, targetX: Float, targetZ: Float,
        timeSeconds: Double, uniformScale: Float,
    ): Boolean {
        visibility = null
        val weather = sky?.takeIf { it.hasClouds }?.clouds ?: return false
        val index = sunLightIndex(lights, sunId)
        if (index < 0) return false
        val target = map ?: CloudShadowMap(shaders, log).also { map = it }
        val toSun = Vector3(sun.x, sun.y, sun.z)
        if (!target.update(weather, toSun, targetX, targetZ, sky.params.planetRadius, SKY_CAMERA_HEIGHT, timeSeconds)) return false
        val texture = target.texture ?: return false
        visibility = DirectionalVisibilityAttribute(texture, index, target.worldToMap, CLOUD_SHADOW_EDGE_FADE, uniformScale)
        return true
    }

    private val captureRevision = CloudRevision()
    private val capturePolicy = LightingRefreshPolicy(fadeSeconds = 0.0)
    private var captured: RayCloudShadowInput? = null
    private var captures = 0L

    /**
     * The map as a ray tracing input: a read-back of the map and its transform, taken only when stale (the sun moved over a
     * degree, the cloud asset changed, or two seconds of drifting clouds) so the stall and the accumulation resets stay
     * bounded, and numbered by [RayCloudShadowInput.revision]. Null when the cloud shadows do not apply to the last frame
     * (the ray view then dims the sun as it did before) or the read-back failed.
     */
    fun capture(sky: ProceduralSky?, sunId: String?, sun: Vec3, timeSeconds: Double): RayCloudShadowInput? {
        val shadow = visibility
        val target = map
        if (shadow == null || target == null || sunId == null || sky == null) {
            captured = null
            capturePolicy.reset()
            return null
        }
        val step = capturePolicy.update(
            LightingFrame(Vector3(sun.x, sun.y, sun.z), timeSeconds, captureRevision.of(sky.cloudAsset), true, sky.clouds?.drifts == true)
        )
        val ticket = step.build ?: return captured
        val pixels = target.readVisibility()
        if (pixels == null) {
            capturePolicy.failed(ticket)
            captured = null
            return null
        }
        capturePolicy.completed(ticket)
        val m = target.worldToMap.`val`
        val rows = listOf(
            m[Matrix4.M00], m[Matrix4.M01], m[Matrix4.M02], m[Matrix4.M03], m[Matrix4.M10], m[Matrix4.M11], m[Matrix4.M12], m[Matrix4.M13],
        )
        return RayCloudShadowInput(sunId, CLOUD_SHADOW_SIZE, pixels, rows, shadow.edgeFade, shadow.outside, ++captures).also { captured = it }
    }

    /** Forgets the GL objects without GL calls (the context is gone). */
    fun abandon() {
        map?.abandon()
        map = null
        visibility = null
        captured = null
        capturePolicy.reset()
    }

    override fun dispose() {
        map?.dispose()
        map = null
        visibility = null
        captured = null
        capturePolicy.reset()
    }
}
