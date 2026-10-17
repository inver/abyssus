/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ray

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.app.game.controlline.render.FailableFieldView
import net.nevinsky.abyssus.app.game.controlline.render.FieldScene
import net.nevinsky.abyssus.app.game.controlline.render.LineSegment
import net.nevinsky.abyssus.lib.core.editor.ray.RayBackendService
import net.nevinsky.abyssus.lib.raytracing.RayColor
import net.nevinsky.abyssus.lib.raytracing.RayFog
import net.nevinsky.abyssus.lib.raytracing.RayLight
import net.nevinsky.abyssus.lib.raytracing.RayLightKind
import net.nevinsky.abyssus.lib.raytracing.RayVec3
import java.util.concurrent.ExecutorService
import org.slf4j.LoggerFactory

/**
 * The game's ray-traced field (design decision 10): the field image comes from the ray tracer, drawn as a fullscreen
 * quad with its depth, and the control lines are drawn over it with the raster pipeline. Foliage is not drawn and
 * animated models stay in bind pose. While the first ray frame is not ready the view shows the fog colour. When ray
 * rendering stops (quality limit, stall, lost device) [failure] says why and the game continues with the raster field.
 * GL thread only; the service owns the native worker.
 */
class RayFieldPresenter(
    private val assets: RayFieldAssets,
    private val runtime: RayFieldRuntime,
    private val service: RayBackendService,
    private val executor: ExecutorService,
) : FailableFieldView {
    private val log = LoggerFactory.getLogger("control-line.ray")
    private val builder = RayFieldSceneBuilder()
    private val quad = RayFrameQuad()
    private var shapes: ShapeRenderer? = null

    override val failure: String? get() = runtime.failure ?: assetFailure

    private var assetFailure: String? = null

    override fun draw(
        field: FieldScene, camera: Camera, lines: List<LineSegment>, hidden: Entity?,
        clips: Map<Entity, String>, seconds: Float,
    ) {
        val fog = field.fogColor ?: Color(0.6f, 0.7f, 0.85f, 1f)
        Gdx.gl.glClearColor(fog.r, fog.g, fog.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        if (failure != null) return

        val drawnModels = field.models.filter { it.first !== hidden }
        assets.update(
            drawnModels.map { it.second }.toSet(), field.terrains.map { it.second }.toSet(), field.skyName,
        )
        when (val state = assets.poll()) {
            is RayFieldAssetState.Preparing -> return
            is RayFieldAssetState.Failed -> {
                assetFailure = "ray assets failed: " + state.failures.entries.joinToString { "${it.key}: ${it.value.message}" }
                return
            }
            is RayFieldAssetState.Ready -> {
                val perspective = camera as? PerspectiveCamera
                if (perspective == null) {
                    assetFailure = "ray mode needs a perspective camera"
                    return
                }
                val frame = try {
                    builder.build(inputsOf(field, drawnModels, state, perspective))
                } catch (e: RuntimeException) {
                    assetFailure = "ray scene could not be built: ${e.message ?: e}"
                    return
                }
                runtime.offer(frame, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
            }
        }
        runtime.latest()?.let(quad::draw)
        drawLines(camera, lines)
    }

    private fun inputsOf(
        field: FieldScene, models: List<Pair<Entity, String>>, state: RayFieldAssetState.Ready, camera: PerspectiveCamera,
    ): RayFieldInputs {
        fun ray(c: Color) = RayColor(c.r, c.g, c.b)
        val placedModels = models.mapIndexedNotNull { index, (entity, name) ->
            state.models[name]?.let { RayPlacedModel("m$index", it, Matrix4(field.position(entity).getTransform())) }
        }
        val placedTerrains = field.terrains.mapIndexedNotNull { index, (entity, name) ->
            state.terrains[name]?.let { RayPlacedTerrain("t$index", it, Matrix4(field.position(entity).getTransform())) }
        }
        // the light travels against the direction of the sun
        val sunTravel = RayVec3(-field.sunDirection.x, -field.sunDirection.y, -field.sunDirection.z)
        val sun = RayLight("sun", RayLightKind.DIRECTIONAL, ray(field.sunColor), sunTravel)
        val background = field.fogColor ?: Color(0.6f, 0.7f, 0.85f, 1f)
        return RayFieldInputs(
            placedModels, placedTerrains, listOf(sun), ray(field.ambient), ray(background),
            field.fogColor?.let { RayFog(ray(it), field.fogDensity, field.fogGradient) }, state.sky,
            RayFieldCamera(
                listOf(camera.position.x, camera.position.y, camera.position.z),
                listOf(camera.direction.x, camera.direction.y, camera.direction.z),
                listOf(camera.up.x, camera.up.y, camera.up.z),
                camera.fieldOfView, camera.near, camera.far,
            ),
        )
    }

    private fun drawLines(camera: Camera, lines: List<LineSegment>) {
        if (lines.isEmpty()) return
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        val shapes = shapes ?: ShapeRenderer().also { shapes = it }
        shapes.projectionMatrix = camera.combined
        for ((width, group) in lines.groupBy { it.width }) {
            Gdx.gl.glLineWidth(width)
            shapes.begin(ShapeRenderer.ShapeType.Line)
            for (line in group) {
                shapes.color = line.color
                shapes.line(line.from, line.to)
            }
            shapes.end()
        }
        Gdx.gl.glLineWidth(1f)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    override fun resize(width: Float, height: Float) {
        // the ray frame is requested at the window's size on every draw: no stored viewport state
    }

    override fun dispose() {
        try {
            runtime.close()
            service.close()
        } catch (e: RuntimeException) {
            log.warn("Closing the ray view failed", e)
        }
        assets.close()
        executor.shutdownNow()
        quad.dispose()
        shapes?.dispose()
    }
}
