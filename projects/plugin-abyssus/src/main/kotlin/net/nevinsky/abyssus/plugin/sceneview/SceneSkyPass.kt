/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview


import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RaySkySnapshot
import net.nevinsky.abyssus.lib.core.assets.sky.SkyRenderer
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.SunOcclusion
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSky
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSkyMeta
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.SKY_CAMERA_HEIGHT
import net.nevinsky.abyssus.lib.core.editor.content.*
import net.nevinsky.abyssus.lib.core.editor.pick.*
import net.nevinsky.abyssus.lib.raytracing.RaySunVisibility
import net.nevinsky.abyssus.lib.core.editor.ray.RaySkyBaker
import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.core.editor.scene.NO_LIGHTS
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.scene.lightSetOf
import net.nevinsky.abyssus.lib.gdx.shader.DirectionalVisibilityAttribute
import net.nevinsky.abyssus.plugin.sceneview.shadows.CloudShadowMap
import net.nevinsky.abyssus.plugin.sceneview.skybox.SkyClock
import com.badlogic.gdx.graphics.Camera
import net.nevinsky.abyssus.plugin.sceneview.skybox.SunDirection
import net.nevinsky.abyssus.plugin.sceneview.skybox.RaySkyBakeKey
import net.nevinsky.abyssus.plugin.sceneview.skybox.RaySunVisibilityKey
import net.nevinsky.abyssus.plugin.sceneview.skybox.raySunVisibilityKey
import net.nevinsky.abyssus.plugin.sceneview.skybox.planSunShading
import net.nevinsky.abyssus.plugin.sceneview.skybox.raySkyBakeKey
import org.slf4j.Logger

/** Sky drawing, cloud shading and immutable ray payloads for one view. GL work stays in its canvas context. */
internal class SceneSkyPass(
    private val assets: ViewAssets,
    private val shaders: ShaderStorage,
    private val state: SceneViewState,
    private val environment: Environment,
    private val log: Logger,
) {
    var skybox: SceneSkybox? = null
        private set

    fun create(resources: RenderResources) {
        skyLease = null
        skyLeaseOwner = null
        skybox = resources.own(SceneSkybox(AssetView(assets, SkyRenderer::class.java)), SceneSkybox::dispose, SceneSkybox::abandon).value
    }

    fun invalidateLighting() {
        lightsKey = null
        frameLightsKey = null
    }

    fun abandon() {
        bakedSky = null
        cloudShadowMap?.abandon()
        cloudShadowMap = null
        sunVisibility = null
        skybox = null
    }

    fun dispose() {
        bakedSky = null
        releaseSkyLease()
        cloudShadowMap?.dispose()
        cloudShadowMap = null
        sunVisibility = null
        skybox = null
    }

    fun draw(view: Camera, p: SceneRenderParams) {
        skybox?.draw(view, p.content.skybox, SunDirection.of(p.content.lights), skyClock.seconds, state.cloudTechnique)
    }
    private var lightsKey: Pair<List<LightPlacement>, Vector3>? = null
    var lights = NO_LIGHTS

    /** [lights] with the sun dimmed by the clouds over it: what the environment and the terrain shader light with. */
    var frameLights = NO_LIGHTS
    private var frameLightsKey: Triple<LightSet, String?, Pair<Float, Int?>>? = null
    private val sunOcclusion = SunOcclusion()

    /** The cloud shadow map of this view; created with the first clouds that cast shadows, null when it could not be. */
    private var cloudShadowMap: CloudShadowMap? = null

    /** The sun's visibility map for this frame's content, or null when the uniform sun dimming applies. */
    var sunVisibility: DirectionalVisibilityAttribute? = null
    private val sunMapMatrix = Matrix4()

    // what the last applySunScale decided, for the ray tracing copy of the clouds' shade on the sun
    private var frameSky: ProceduralSky? = null
    private var frameSunId: String? = null
    private var frameSunDirection = SunDirection.DEFAULT
    private var frameTargetX = 0f
    private var frameTargetZ = 0f

    /** True when the last frame shaded the sun with the cloud shadow map instead of the uniform dimming. */
    internal val cloudShadowsActive: Boolean get() = sunVisibility != null

    /** The time this view's sky is drawn at; clouds drift by it. */
    internal val skyClock = SkyClock()

    /** The share of the sun's light the clouds let through in the last frame (1 without clouds). */
    internal val sunScale: Float get() = sunOcclusion.transmittance

    /** The lights the last frame lit the scene with (the sun dimmed by clouds), for tests. */
    internal val litLights: LightSet get() = frameLights

    /** The cloud technique the last frame drew; null when it drew no clouds. */
    /**
     * True when the sky of [p] is a procedural sky whose `meta.json` names a cloud asset it can still draw (once built,
     * a sky whose cloud asset is missing or empty, or that every technique failed for, has none). Reads the metadata, no
     * GL: the view's Clouds choice follows it.
     */
    internal fun cloudsEnabled(p: SceneRenderParams): Boolean {
        val name = p.content.skybox ?: return false
        val dir = p.projectDir ?: return false
        val meta = assets.project(dir).metas.loadBaseMeta(name)?.takeIf { it.type == MetaType.SKYBOX_PROCEDURAL }
            ?: return false
        if (meta.typedAdditional<ProceduralSkyMeta>().cloudsReference == null) return false
        return (skybox?.sky(name) as? ProceduralSky)?.hasClouds ?: true
    }

    /** Recomputed only when the scene's lights or the orbit target (which decides the nearest point lights) change. */
    fun applyLights(c: SceneContent, orbit: OrbitCamera) {
        val target = Vector3(orbit.target.x, orbit.target.y, orbit.target.z)
        val key = c.lights to target
        if (key == lightsKey) return
        lightsKey = key
        lights = lightSetOf(c.lights, target)
    }

    /**
     * Dims the sun light by the clouds of the scene's procedural sky between the orbit target and the sun, every frame
     * (the clouds drift), and applies the lights to the environment when they changed. Other lights are untouched.
     */
    fun applySunScale(c: SceneContent, orbit: OrbitCamera) {
        val sky = skybox?.sky(c.skybox) as? ProceduralSky
        val sun = SunDirection.of(c.lights)
        val sunVector = Vector3(sun.x, sun.y, sun.z)
        val scale = sunOcclusion.update(
            sky?.clouds, sunVector, orbit.target.x, orbit.target.z,
            sky?.params?.planetRadius ?: 0f, SKY_CAMERA_HEIGHT, skyClock.seconds, skyClock.lastStep,
        )
        val sunId = SunDirection.sunLight(c.lights)?.entityId
        frameSky = sky
        frameSunId = sunId
        frameSunDirection = sun
        frameTargetX = orbit.target.x
        frameTargetZ = orbit.target.z
        val map = cloudShadowMapFor(sky, sunId)?.takeIf {
            it.update(
                sky!!.clouds!!, sunVector, orbit.target.x, orbit.target.z, sky.params.planetRadius, SKY_CAMERA_HEIGHT,
                skyClock.seconds,
            )
        }
        val shading = planSunShading(lights, sunId, sky?.hasClouds == true, map != null, scale)
        sunVisibility = map?.texture?.let { texture ->
            shading.mapLightIndex?.let { index ->
                DirectionalVisibilityAttribute(
                    texture, index, map.worldToMap(sunVector, sunMapMatrix), CLOUD_SHADOW_EDGE_FADE, shading.outside,
                )
            }
        }
        val key = Triple(lights, sunId, scale to shading.mapLightIndex)
        if (key == frameLightsKey) return
        frameLightsKey = key
        frameLights = shading.lights
        frameLights.applyTo(environment)
    }

    private class PublishedSunVisibility(val key: RaySunVisibilityKey, val value: RaySunVisibility?)

    private var publishedSunVisibility: PublishedSunVisibility? = null
    private var sunVisibilityRevision = 0L

    /**
     * The clouds' shade on the sun as an immutable copy for ray tracing, or null when no clouds shade it: a read-back of
     * the cloud shadow map when it shaded the sun this frame (with the uniform factor beyond it), else the uniform
     * dimming as one factor. Taken again only when [raySunVisibilityKey] changes. Render thread, in ray mode only.
     */
    fun raySunVisibility(): RaySunVisibility? {
        val sky = frameSky ?: return null
        val sunId = frameSunId ?: return null
        if (!sky.hasClouds || lights.directionalIndexOf(sunId) == null) return null
        val attribute = sunVisibility
        val scale = sunOcclusion.transmittance
        val clouds = sky.cloudAsset
        val drifting = clouds?.settings?.bandsFarToNear()?.any { it.windX != 0f || it.windZ != 0f } == true
        val key = raySunVisibilityKey(
            sunId, frameSunDirection, clouds, drifting, attribute != null, scale, frameTargetX, frameTargetZ, skyClock.seconds,
        )
        publishedSunVisibility?.takeIf { it.key == key }?.let { return it.value }
        val revision = ++sunVisibilityRevision
        val mapped = attribute?.let { shade ->
            cloudShadowMap?.readback(RAY_SUN_VISIBILITY_SIZE)?.let { texels ->
                val m = shade.worldToMap.`val`
                RaySunVisibility(
                    sunId, RAY_SUN_VISIBILITY_SIZE, texels,
                    floatArrayOf(m[Matrix4.M00], m[Matrix4.M01], m[Matrix4.M02], m[Matrix4.M03],
                        m[Matrix4.M10], m[Matrix4.M11], m[Matrix4.M12], m[Matrix4.M13]),
                    shade.edgeFade, shade.outside, revision,
                )
            }
        }
        val value = mapped ?: if (scale < 1f) RaySunVisibility.uniform(sunId, scale, revision) else null
        publishedSunVisibility = PublishedSunVisibility(key, value)
        return value
    }

    /**
     * The map to shade the sun with this frame: the view's, made on first need, when the sky draws clouds some technique
     * can still draw and the sun is among the rendered directional lights; null otherwise or when the map failed.
     */
    private fun cloudShadowMapFor(sky: ProceduralSky?, sunId: String?): CloudShadowMap? {
        if (sky == null || !sky.hasClouds || lights.directionalIndexOf(sunId) == null) return null
        val map = cloudShadowMap ?: CloudShadowMap(shaders, log).also { cloudShadowMap = it }
        return map.takeUnless { it.failed }
    }

    private var skyLease: ProceduralSky.EnvironmentLease? = null

    /** Whether this view holds a lease on its sky's cloudy environment for water that shows the sky; for tests. */
    internal val skyLeaseActive: Boolean get() = skyLease != null
    private var skyLeaseOwner: ProceduralSky? = null

    private fun releaseSkyLease() {
        skyLease?.close()
        skyLease = null
        skyLeaseOwner = null
    }

    /**
     * The lighting cube of the scene's sky for water that shows the sky instead of a planar reflection, or null when
     * the sky has none (a plain cube sky) or it is not built yet. A procedural sky with clouds builds its cloudy cube only
     * while this view holds a lease, so the lease is taken while some surface needs the fallback and given back when
     * none does (and with the view).
     */
    fun waterSkyCube(c: SceneContent, needed: Boolean): com.badlogic.gdx.graphics.GLTexture? {
        val sky = skybox?.sky(c.skybox)
        if (!needed || sky !== skyLeaseOwner) releaseSkyLease()
        if (!needed) return null
        return when (sky) {
            is net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrSky -> sky.environment.specular
            is ProceduralSky -> {
                if (skyLease == null && sky.hasClouds) {
                    skyLease = sky.acquireEnvironment()
                    skyLeaseOwner = sky
                }
                (sky.lightingEnvironment ?: skyLease?.environment)?.specular
            }
            else -> null
        }
    }

    private class BakedSky(val key: RaySkyBakeKey, val snapshot: RaySkySnapshot?)

    private var bakedSky: BakedSky? = null
    private val skyBaker = RaySkyBaker()

    /**
     * The procedural sky rendered into a ray texture, with its clouds when it draws any; null while it loads or fails to
     * bake. Baked again when the sky, project, sun or cloud asset change and, for drifting clouds, every
     * [net.nevinsky.abyssus.plugin.sceneview.skybox.RAY_SKY_BAKE_REFRESH_SECONDS] of sky time. It is a display
     * sample; the linear diffuse sky light reaches the rays through the ambient cube.
     */
    fun bakedProceduralSky(c: SceneContent, p: SceneRenderParams): RaySkySnapshot? {
        val name = c.skybox ?: return null
        val sky = skybox?.sky(name) as? ProceduralSky ?: return null
        val sun = SunDirection.of(c.lights)
        val clouds = sky.cloudAsset?.takeIf { sky.hasClouds }
        val drifting = clouds?.settings?.bandsFarToNear()?.any { it.windX != 0f || it.windZ != 0f } == true
        val key = raySkyBakeKey(name, p.projectDir, sun, clouds, drifting, skyClock.seconds)
        bakedSky?.takeIf { it.key == key }?.let { return it.snapshot }
        val snapshot = try {
            skyBaker.bake(sky, sun, skyClock.seconds, clouds != null)
        } catch (failure: Exception) {
            null
        }
        bakedSky = BakedSky(key, snapshot)
        return snapshot
    }

    private val CLOUD_SHADOW_EDGE_FADE = 0.1f
    private val RAY_SUN_VISIBILITY_SIZE = 256
}
