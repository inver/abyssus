/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview.shadows

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute
import com.badlogic.gdx.graphics.g3d.environment.BaseLight
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Vector4
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool
import com.badlogic.gdx.utils.Disposable
import com.intellij.openapi.diagnostic.Logger
import net.nevinsky.abyssus.core.Renderable
import net.nevinsky.abyssus.core.shader.ModelDepthShaderProvider
import net.nevinsky.abyssus.core.shader.ShadowDepthPass
import net.nevinsky.abyssus.core.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.core.shader.ShadowAtlasView
import net.nevinsky.abyssus.core.shader.ShadowLightRecord
import net.nevinsky.abyssus.core.shader.ShadowLightKind as AtlasLightKind
import net.nevinsky.abyssus.sceneview.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/** One view's atlas and depth shaders. Geometry is captured once after animation/preview updates. */
class SceneShadows : Disposable {
    private val owner = Gdx.app
    private val resources = ShadowResources()
    private val layout = ShadowLayout()
    private val pass = ShadowDepthPass()
    private val shaders = ModelDepthShaderProvider(pass)
    private val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 1))
    private val pool = object : Pool<Renderable>() { override fun newObject() = Renderable() }
    private val renderables = Array<Renderable>()
    private val casterBounds = ShadowCasterBounds()
    private val log = Logger.getInstance(SceneShadows::class.java)
    private var failed = false
    private data class Caster(val renderable: Renderable, val bounds: BoundingBox)

    init { if (!resources.available) log.warn("Shadow atlas unavailable; scene lights will render without shadows") }

    fun render(camera: PerspectiveCamera, lights: LightSet, environment: Environment,
               models: Collection<ModelEntity>, terrains: Collection<TerrainEntity>): ShadowAtlasAttribute? {
        if (failed || !resources.available) return null
        pool.freeAll(renderables); renderables.clear()
        val casters = mutableListOf<Caster>()
        models.forEach { entity ->
            val first = renderables.size
            entity.instance.getRenderables(renderables, pool)
            for (i in first until renderables.size) casters += Caster(renderables[i], casterBounds.world(renderables[i]))
        }
        casterBounds.retain(renderables.mapNotNull { it.meshPart.mesh }.toSet())
        terrains.forEach { entity ->
            val out = pool.obtain().also { it.cleanup() }
            entity.terrain.depthRenderable(entity.world, out); renderables.add(out)
            val data = entity.terrain.data
            casters += Caster(out, BoundingBox(Vector3(0f,data.heights.min(),0f),Vector3(data.size.toFloat(),data.heights.max(),data.size.toFloat())).mul(entity.world))
        }
        val receiver = receiverBounds(camera, casters.map { it.bounds })
        val dirs = (environment.get(DirectionalLightsAttribute.Type) as? DirectionalLightsAttribute)?.lights
        val points = (environment.get(PointLightsAttribute.Type) as? PointLightsAttribute)?.lights
        val spots = (environment.get(SpotLightsAttribute.Type) as? SpotLightsAttribute)?.lights
        val records = mutableListOf<ShadowLightRecord>()
        for (allocation in layout.allocate(lights)) {
            val resolution = allocation.tiles.first().size
            val projection = ShadowProjection(resolution)
            val sourcePosition: Vector3
            val range: Float
            val light: BaseLight<*>
            val views: List<ShadowView>
            when (allocation.kind) {
                ShadowLightKind.DIRECTIONAL -> {
                    val index = lights.directional.indexOfFirst { it.entityId == allocation.entityId }
                    val source = lights.directional[index]
                    light = dirs?.get(index) ?: continue
                    sourcePosition = source.position.toVector3(); range = 1f
                    views = receiver?.let { projection.directional(source.direction.toVector3(), it, casters.map(Caster::bounds)) }?.let(::listOf) ?: continue
                }
                ShadowLightKind.POINT -> {
                    val index = lights.point.indexOfFirst { it.entityId == allocation.entityId }
                    val source = lights.point[index]
                    light = points?.get(index) ?: continue
                    sourcePosition = source.position.toVector3(); range = source.range
                    views = projection.point(sourcePosition, range)
                }
                ShadowLightKind.SPOT -> {
                    val index = lights.spot.indexOfFirst { it.entityId == allocation.entityId }
                    val source = lights.spot[index]
                    light = spots?.get(index) ?: continue
                    sourcePosition = source.position.toVector3(); range = source.range
                    views = projection.spot(sourcePosition, source.direction.toVector3(), source.cone.angle, range)?.let(::listOf) ?: continue
                }
            }
            if (views.size != allocation.tiles.size) continue
            pass.radialDepth = allocation.kind == ShadowLightKind.POINT
            pass.lightPosition.set(sourcePosition); pass.far = range
            for ((view, tile) in views.zip(allocation.tiles)) {
                val success = resources.render(tile) {
                    context.begin()
                    try {
                        for (caster in casters) {
                            if (!view.camera.frustum.boundsInFrustum(caster.bounds)) continue
                            val shader = shaders.get(caster.renderable) ?: continue
                            shader.begin(view.camera, context)
                            try { shader.render(caster.renderable) } finally { shader.end() }
                        }
                    } finally { context.end() }
                }
                if (!success) {
                    failed = true
                    log.warn("Shadow depth rendering failed; using unshadowed lighting until the view context is rebuilt")
                    return null
                }
            }
            val first = views.first()
            val depthBias = when (val viewCamera = first.camera) {
                is OrthographicCamera -> 0.25f * viewCamera.viewportWidth / max(first.far - first.near, 0.01f) / resolution
                else -> if (pass.radialDepth) 0.5f / resolution else 0.00005f * tan(Math.toRadians((viewCamera as PerspectiveCamera).fieldOfView.toDouble() / 2)).toFloat()
            }
            records += ShadowLightRecord(allocation.entityId, AtlasLightKind.valueOf(allocation.kind.name), light,
                views.zip(allocation.tiles).map { (view, tile) -> ShadowAtlasView(view.combined,
                    Vector4(tile.x.toFloat()/tile.atlasSize,tile.y.toFloat()/tile.atlasSize,tile.size.toFloat()/tile.atlasSize,tile.size.toFloat()/tile.atlasSize)) },
                sourcePosition, range, depthBias)
        }
        return resources.attribute(records).takeIf { records.isNotEmpty() }
    }

    /** Fit a finite camera region to visible geometry; large terrain bounds are clipped to the receiver region. */
    private fun receiverBounds(camera: PerspectiveCamera, bounds: List<BoundingBox>): BoundingBox? {
        val direction = Vector3(camera.direction).nor()
        val right = Vector3(direction).crs(camera.up).nor()
        val up = Vector3(right).crs(direction).nor()
        val frustum = BoundingBox().inf()
        val tangent = tan(Math.toRadians(camera.fieldOfView.toDouble()/2)).toFloat()
        for (distance in listOf(camera.near, min(camera.far,80f))) {
            val center = Vector3(camera.position).mulAdd(direction,distance)
            val halfH = tangent * distance
            val halfW = halfH * camera.viewportWidth / camera.viewportHeight
            for (x in listOf(-1f,1f)) for (y in listOf(-1f,1f)) frustum.ext(Vector3(center).mulAdd(right,x*halfW).mulAdd(up,y*halfH))
        }
        val receivers = BoundingBox().inf()
        for (box in bounds) {
            if (!camera.frustum.boundsInFrustum(box) || !frustum.intersects(box)) continue
            val lo = Vector3(max(box.min.x,frustum.min.x),max(box.min.y,frustum.min.y),max(box.min.z,frustum.min.z))
            val hi = Vector3(min(box.max.x,frustum.max.x),min(box.max.y,frustum.max.y),min(box.max.z,frustum.max.z))
            receivers.ext(lo); receivers.ext(hi)
        }
        return receivers.takeIf { it.isValid }
    }

    fun abandon() {
        resources.abandon()
        ShaderProgram.clearAllShaderPrograms(owner)
        renderables.clear(); pool.clear(); casterBounds.clear()
    }
    override fun dispose() { shaders.dispose(); resources.dispose(); renderables.clear(); pool.clear(); casterBounds.clear() }
}
