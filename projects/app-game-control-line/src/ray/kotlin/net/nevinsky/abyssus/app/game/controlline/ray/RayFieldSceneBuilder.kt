/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ray

import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.core.editor.ray.RaySceneConverter
import net.nevinsky.abyssus.lib.core.editor.ray.RaySnapshotLimits
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelSnapshot
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RaySkySnapshot
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayTerrainSnapshot
import net.nevinsky.abyssus.lib.raytracing.*

/** One placed model in bind pose: a stable [id] (so its geometry keeps its identity), its asset and world transform. */
class RayPlacedModel(val id: String, val asset: RayModelSnapshot, val world: Matrix4)

/** One placed terrain. */
class RayPlacedTerrain(val id: String, val asset: RayTerrainSnapshot, val world: Matrix4)

/** The field camera as plain numbers: nothing mutable crosses to the ray worker. */
class RayFieldCamera(
    val position: List<Float>, val direction: List<Float>, val up: List<Float>,
    val fieldOfView: Float, val near: Float, val far: Float,
)

/** What the game knows about one frame of the field, with no GL and no Ashley type. */
class RayFieldInputs(
    val models: List<RayPlacedModel>,
    val terrains: List<RayPlacedTerrain>,
    val lights: List<RayLight>,
    val ambient: RayColor,
    val background: RayColor,
    val fog: RayFog?,
    val sky: RaySkySnapshot?,
    val camera: RayFieldCamera,
)

/** A built frame: the scene, the camera, and the revisions the scheduler keys its history on. */
class RayFieldFrame(
    val scene: RaySceneSnapshot,
    val camera: RaySliceCamera,
    val key: RayFrameKey,
    val contentRevision: Long,
    val environmentRevision: Long,
)

/**
 * Builds one [RaySceneSnapshot] per frame (terrain, models in bind pose, sun, sky and fog) through the editor's
 * [RaySceneConverter], which keeps converted meshes and textures per asset: a frame that only moves instances or the
 * camera reuses every mesh, so the backend does not rebuild geometry. The structure, content, environment and camera
 * revisions say what changed since the previous frame.
 */
class RayFieldSceneBuilder(limits: RaySnapshotLimits = RaySnapshotLimits()) {
    private val converter = RaySceneConverter(limits)
    private var skyTexture: Pair<RaySkySnapshot, RayTexture>? = null

    private var sceneGeneration = 0L
    private var cameraRevision = 0L
    private var contentRevision = 0L
    private var environmentRevision = 0L

    private var assets: List<Any> = emptyList()
    private var topology: List<String> = emptyList()
    private var transforms: Map<String, List<Float>> = emptyMap()
    private var camera: List<Any>? = null
    private var lights: List<RayLight> = emptyList()
    private var environment: Triple<RayEnvironment, RayFog?, Any?>? = null

    /** Geometry builds so far: the number of times the structure changed. */
    val structureChanges: Long get() = sceneGeneration

    fun build(inputs: RayFieldInputs): RayFieldFrame {
        val texture = inputs.sky?.let(::textureOf)
        converter.reset(listOfNotNull(texture))
        for (placed in inputs.models) converter.model(placed.id, placed.world, placed.asset)
        for (placed in inputs.terrains) converter.terrain(placed.id, placed.world, placed.asset)
        converter.checkBounds()

        val environmentNow = RayEnvironment(inputs.ambient, inputs.background, texture = texture?.let { 0 })
        val scene = RaySceneSnapshot(
            converter.meshes, converter.instances, converter.materials,
            fitTextureTexels(converter.textures, halve = ::halvedTexture)
                ?: throw IllegalArgumentException("The scene textures cannot be reduced to $RAY_MAX_TEXTURE_TEXELS texels"),
            inputs.lights, environmentNow, inputs.fog,
        )
        val c = inputs.camera
        val rayCamera = RaySliceCamera(c.position, c.direction, c.up, c.fieldOfView, c.near, c.far)

        val assetsNow = inputs.models.map<RayPlacedModel, Any> { it.asset } + inputs.terrains.map { it.asset }
        val structure = topology != converter.topology.toList() ||
            assets.size != assetsNow.size || assets.indices.any { assets[it] !== assetsNow[it] }
        val moved = transforms != converter.transforms
        val cameraNow = listOf(c.position, c.direction, c.up, c.fieldOfView, c.near, c.far)
        val lit = lights != inputs.lights
        val skyNow = Triple(environmentNow, inputs.fog, inputs.sky)
        val env = environment?.let { it.first != skyNow.first || it.second != skyNow.second || it.third !== skyNow.third } ?: true

        if (structure) sceneGeneration++
        if (camera != cameraNow) cameraRevision++
        // a sky-only change keeps the older frame shown; any other change restarts sampling
        if (structure || moved || lit) contentRevision++ else if (env) environmentRevision++

        assets = assetsNow
        topology = converter.topology.toList()
        transforms = converter.transforms.toMap()
        camera = cameraNow
        lights = inputs.lights
        environment = skyNow
        return RayFieldFrame(
            scene, rayCamera, RayFrameKey(sceneGeneration, 1, cameraRevision, 0), contentRevision, environmentRevision,
        )
    }

    // halved copies of oversized textures, per source, so a scene over the texel budget reduces them once, not every frame
    private val halved = java.util.WeakHashMap<RayTexture, RayTexture>()
    private fun halvedTexture(texture: RayTexture): RayTexture? =
        halved[texture] ?: texture.halved()?.also { halved[texture] = it }

    private fun textureOf(sky: RaySkySnapshot): RayTexture {
        skyTexture?.takeIf { it.first === sky }?.let { return it.second }
        val bytes = sky.rgba8()
        return (if (bytes != null) RayTexture("sky", sky.width, sky.height, bytes, RayWrap.REPEAT, RayWrap.CLAMP_TO_EDGE, RayFilter.LINEAR)
        else RayTexture("sky", sky.width, sky.height, sky.rgba(), RayWrap.REPEAT, RayWrap.CLAMP_TO_EDGE, RayFilter.LINEAR))
            .also { skyTexture = sky to it }
    }
}
