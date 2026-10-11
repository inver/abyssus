/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray

import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.core.editor.scene.ModelEntity
import net.nevinsky.abyssus.lib.core.editor.scene.FogParams
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.Rgba

import com.badlogic.gdx.graphics.PerspectiveCamera
import net.nevinsky.abyssus.lib.core.assets.sky.RaySkySnapshot
import net.nevinsky.abyssus.lib.raytracing.RayFrame
import java.io.File
import java.util.Collections

/**
 * The cloud shadow map of the sun, captured on the render thread for a ray frame: [size]² visibility bytes (0 dark to 255
 * lit, one per texel, row 0 first as the map is drawn), the transform from world positions to map coordinates ([worldToMap]:
 * two rows of four numbers), the [edgeFade] share of the map's width and the [outside] visibility beyond it, for the light of
 * entity [sunEntityId]. [revision] grows with every new capture. Immutable once published.
 */
class RayCloudShadowInput(
    val sunEntityId: String, val size: Int, pixels: ByteArray, val worldToMap: List<Float>, val edgeFade: Float, val outside: Float,
    val revision: Long,
) {
    private val data = pixels.copyOf()

    init {
        require(size > 0 && data.size == size * size && worldToMap.size == 8)
    }

    fun pixels(): ByteArray = data.copyOf()
}

/** Borrowed frame data; consumed on the render thread before publishing immutable worker inputs. */
class RayFrameContext(
    val params: SceneRenderParams, val content: SceneContent, val camera: PerspectiveCamera,
    val lights: LightSet, val models: Collection<ModelEntity>, val width: Int, val height: Int,
    val viewCamera: String?,
    /** The built HDR sky's six axis irradiance colours (+X, -X, +Y, -Y, +Z, -Z), the same ones raster models use as ambient. */
    val hdrAmbient: FloatArray? = null,
    /** Captures the sun's cloud shadow map when the raster view shades the sun with one (null: no cloud shadows). Only asked in ray mode. */
    val cloudShadow: (() -> RayCloudShadowInput?)? = null,
    /** Bakes the scene's procedural sky (arbitrary asset GLSL) on the render thread; null for any other sky. Only asked in ray mode. Last, so callers can pass it as a trailing lambda. */
    val bakedSky: (() -> RaySkySnapshot?)? = null,
)

data class RayDisplayCamera(
    val position: Vec3, val direction: Vec3, val up: Vec3,
    val fieldOfView: Float, val near: Float, val far: Float,
) {
    fun applyTo(camera: PerspectiveCamera, width: Int, height: Int) {
        camera.position.set(position.x, position.y, position.z)
        camera.direction.set(direction.x, direction.y, direction.z)
        camera.up.set(up.x, up.y, up.z)
        camera.fieldOfView = fieldOfView
        camera.near = near
        camera.far = far
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
    }
}

/** Matched camera/content for overlays; mutable libGDX objects never enter a worker mailbox. */
data class RayDisplayMetadata(
    val camera: RayDisplayCamera, val content: SceneContent,
    val width: Int, val height: Int, val viewCamera: String?,
    val projectDir: File?, val ambient: Rgba?, val fog: FogParams?,
)

fun captureRayDisplay(context: RayFrameContext): RayDisplayMetadata {
    val camera = context.camera
    fun vec(value: com.badlogic.gdx.math.Vector3) = Vec3(value.x, value.y, value.z)
    fun <T> frozen(values: List<T>) = Collections.unmodifiableList(values.toList())
    val content = context.content.copy(
        models = frozen(context.content.models), terrains = frozen(context.content.terrains),
        lights = frozen(context.content.lights), cameras = frozen(context.content.cameras),
        entityPositions = Collections.unmodifiableMap(context.content.entityPositions.toMap()),
    )
    return RayDisplayMetadata(RayDisplayCamera(vec(camera.position), vec(camera.direction), vec(camera.up),
        camera.fieldOfView, camera.near, camera.far), content, context.width, context.height,
        context.viewCamera, context.params.projectDir, context.params.ambient, context.params.fog)
}


class RaySceneDisplay(val frame: RayFrame, val metadata: RayDisplayMetadata)
