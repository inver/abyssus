/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.water

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Pool
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.content.Rgba
import net.nevinsky.abyssus.lib.core.editor.scene.FogParams
import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.core.editor.scene.ModelEntity
import net.nevinsky.abyssus.lib.core.water.*
import net.nevinsky.abyssus.lib.gdx.g3d.ModelDepthShaderProvider
import net.nevinsky.abyssus.lib.gdx.shader.ShadowDepthPass
import net.nevinsky.abyssus.lib.render.shadow.DepthSubmission
import net.nevinsky.abyssus.plugin.sceneview.TerrainEntity
import net.nevinsky.abyssus.plugin.sceneview.shadows.FoliageCastSource
import net.nevinsky.abyssus.plugin.sceneview.shadows.ShadowCaster
import net.nevinsky.abyssus.plugin.sceneview.shadows.ShadowCasterBounds
import net.nevinsky.abyssus.plugin.sceneview.shadows.shadowCasters
import org.slf4j.Logger

/** What a water frame reads of the scene: the camera and viewport, the surfaces, and the colours and geometry it draws with. */
internal class WaterFrameInput(
    val camera: PerspectiveCamera,
    val width: Int,
    val height: Int,
    val deltaSeconds: Float,
    val surfaces: List<WaterSettings>,
    val clear: Rgba,
    val ambient: Rgba?,
    val fog: FogParams?,
    val lights: LightSet,
    val models: Collection<ModelEntity>,
    val terrains: Collection<TerrainEntity>,
    val foliage: FoliageCastSource?,
)

/**
 * The water passes of one Scene view, in the order a frame runs them: [begin] plans the frame (and says whether there
 * is any water to draw), [renderReflections] draws the scene mirrored through each reflected level, and after the
 * opaque scene is on the canvas [drawSurfaces] copies it, draws the depth targets and the surfaces. A scene without
 * visible water costs [begin] and nothing else.
 *
 * Failures are isolated: a shader or mesh that cannot be made skips the water (logged once, retried after the context
 * is replaced); targets that cannot be allocated leave plain tinted water; a reflection that fails shows the sky colour
 * for its level; a surface that throws is skipped while the others draw. Render thread only, with the context current.
 */
internal class SceneWater(private val shaders: ShaderStorage, private val log: Logger) : Disposable {
    private val owner = Gdx.app
    private val resources = WaterResources(log)
    private var drawable: WaterDrawable? = null
    private var drawableFailed = false
    private val planner = WaterPassPlanner()
    private val area = WaterScreenArea()
    private val reflection = WaterReflectionCamera()
    private var previousLevels: List<Long> = emptyList()
    private var seconds = 0.0
    private var idleFrames = 0
    private val reflectionOk = BooleanArray(MAX_REFLECTION_TARGETS)
    private val mirrors = arrayOfNulls<MirroredCamera>(MAX_REFLECTION_TARGETS)
    private val depthPass = ShadowDepthPass()
    private var depthShaders = ModelDepthShaderProvider(depthPass)
    private val depthSubmission = DepthSubmission { depthShaders.get(it) }
    private val pool = object : Pool<Renderable>() {
        override fun newObject() = Renderable()
    }
    private val renderables = Array<Renderable>()
    private val casterBounds = ShadowCasterBounds()
    private val surfaceFailures = HashSet<String>()

    /** When set, the water animation stands at this time instead of advancing with real time; for tests that compare frames. */
    var frozenSeconds: Double? = null

    /** The plan of the last frame, or null when it had no water; for tests. */
    var lastPlan: WaterPassPlan? = null
        private set

    /** How many reflection images, depth targets and surfaces the last frame drew; for tests. */
    var reflectionPasses = 0
        private set
    var depthPasses = 0
        private set
    var drawnSurfaces = 0
        private set

    /** True when the last frame drew its water without the scene copy and depth targets (allocation failed). */
    var degraded = false
        private set

    /** True when the water shader or mesh could not be made, so no water is drawn until the context is replaced. */
    val unavailable: Boolean get() = drawableFailed

    internal val resourceTargets: Int get() = resources.targetCount

    /** The targets of this view's water passes, for tests that read them back. */
    internal val targets: WaterResources get() = resources

    /** Whether the reflection image in [slot] was drawn this frame. */
    internal fun reflectionReady(slot: Int): Boolean = reflectionOk.getOrElse(slot) { false }

    /**
     * Plans this frame's water. Null when there is nothing to draw (no valid, enabled surface in view above the camera,
     * or the water shader is unavailable); the frame then runs the ordinary path.
     */
    fun begin(input: WaterFrameInput): WaterPassPlan? {
        reflectionPasses = 0; depthPasses = 0; drawnSurfaces = 0; degraded = false
        seconds = frozenSeconds ?: ((seconds + input.deltaSeconds.coerceIn(0f, 1f)) % TIME_WRAP)
        val eye = input.camera.position
        val plan = planner.plan(
            input.surfaces, eye.y.toDouble(), input.width, input.height,
            { area.pixels(input.camera.combined, it, input.width, input.height) },
            previousLevels, eye.x.toDouble(), eye.z.toDouble(),
        )
        lastPlan = plan.takeUnless { it.isEmpty }
        if (plan.isEmpty || drawableFailed) {
            previousLevels = emptyList()
            // keep the targets for a while so a surface that flickers in and out of view does not reallocate them
            if (++idleFrames > IDLE_FRAMES_BEFORE_RELEASE) resources.dispose()
            return null
        }
        idleFrames = 0
        previousLevels = plan.reflected.map { it.key }
        if (drawable == null) {
            drawable = runCatchingKeepingCancellation { WaterDrawable(shaders) }.getOrElse { e ->
                drawableFailed = true
                log.warn(
                    "The water shader could not be made (${e.message ?: e.javaClass.simpleName}); no water is drawn in this view",
                    e
                )
                null
            }
            if (drawable == null) return null
        }
        degraded = !resources.ensure(plan, input.width, input.height)
        return plan
    }

    /**
     * Draws the scene mirrored through each reflected level of [plan] into its target: [draw] is given the mirrored
     * camera and the world clip plane (keep `dot(n, p) + d >= 0`) and draws the sky and the opaque scene through them.
     * The winding is flipped for the duration. A level whose pass fails shows the sky colour instead.
     */
    fun renderReflections(plan: WaterPassPlan, input: WaterFrameInput, draw: (PerspectiveCamera, FloatArray) -> Unit) {
        reflectionOk.fill(false)
        if (degraded) return
        for ((i, group) in plan.reflected.withIndex()) {
            val target = resources.reflection(i) ?: continue
            val level = group.level.toFloat()
            val mirrored = mirrors[i]?.also { it.level = level } ?: MirroredCamera(
                input.camera,
                level,
                reflection
            ).also { mirrors[i] = it }
            mirrored.update()
            val amplitude = group.surfaces.maxOf { it.waveAmplitude }
            val distance = kotlin.math.abs(input.camera.position.y - level)
            val clip = reflection.clipPlane(level, reflection.bias(amplitude, distance))
            reflectionOk[i] = resources.render(target, input.clear) {
                Gdx.gl.glFrontFace(GL20.GL_CW)
                draw(mirrored, clip)
            }
            if (reflectionOk[i]) reflectionPasses++
        }
    }

    /**
     * Whether some visible level shows the sky instead of a planar reflection (beyond the reflection budget, or its
     * pass failed or was not possible): the caller then provides the sky's lighting cube to [drawSurfaces].
     */
    fun usesSkyFallback(plan: WaterPassPlan): Boolean =
        plan.fallback.isNotEmpty() || plan.reflected.indices.any { !reflectionOk[it] }

    /** The reflection image of level group [slot] if its pass succeeded, for tests and drawing. */
    private fun reflectionTexture(slot: Int): FrameBuffer? =
        if (slot >= 0 && reflectionOk.getOrElse(slot) { false }) resources.reflection(slot) else null

    /**
     * Draws the surfaces of [plan] far to near over the opaque scene already on the canvas: copies the canvas, draws the
     * scene and terrain depth targets, then each surface. The caller restores nothing: the GL state this leaves is the
     * one a content pass starts from.
     */
    fun drawSurfaces(plan: WaterPassPlan, input: WaterFrameInput, skyCube: GLTexture? = null) {
        val drawable = drawable ?: return
        var haveDepth = false
        if (!degraded && resources.captureScene(input.width, input.height)) {
            val sceneDepth = resources.sceneDepth
            val terrainDepth = resources.terrainDepth
            if (sceneDepth != null && terrainDepth != null) {
                pool.freeAll(renderables); renderables.clear()
                val all = shadowCasters(input.models, input.terrains, input.foliage, renderables, pool, casterBounds)
                val terrainOnly = shadowCasters(emptyList(), input.terrains, null, renderables, pool, casterBounds)
                haveDepth =
                    depthTarget(sceneDepth, input.camera, all) && depthTarget(terrainDepth, input.camera, terrainOnly)
            }
        }
        if (!haveDepth && !degraded) degraded = true
        val frame = frameOf(input)
        for (surface in plan.drawOrder) {
            val group = plan.reflectionOf(surface)
            val reflectionImage = reflectionTexture(group?.let { plan.reflected.indexOf(it) } ?: -1)?.colorBufferTexture
            val textures = if (haveDepth) {
                WaterTextures(
                    resources.sceneColor,
                    resources.sceneDepth?.colorBufferTexture,
                    resources.terrainDepth?.colorBufferTexture,
                    reflectionImage,
                    skyCube
                )
            } else WaterTextures(null, null, null, reflectionImage, skyCube)
            val ok = runCatchingKeepingCancellation { drawable.draw(surface, frame, textures) }
            if (ok.isSuccess) drawnSurfaces++
            else if (surfaceFailures.add(surface.id)) {
                log.warn(
                    "Water surface '${surface.name}' could not be drawn (${ok.exceptionOrNull()?.message}); the rest of the scene is unaffected",
                    ok.exceptionOrNull()
                )
            }
        }
        // leave the state a content pass starts from
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
    }

    private fun depthTarget(target: FrameBuffer, camera: PerspectiveCamera, casters: List<ShadowCaster>): Boolean {
        depthPass.radialDepth = false
        depthPass.clipPlane = null
        depthPass.linearFar = camera.far
        val ok = resources.render(target, Rgba(1f, 1f, 1f, 1f)) {
            depthSubmission.submit(
                camera,
                casters.filter { camera.frustum.boundsInFrustum(it.bounds) }.map { it.renderable })
        }
        if (ok) depthPasses++
        return ok
    }

    private fun frameOf(input: WaterFrameInput): WaterFrame {
        val directions = FloatArray(MAX_DIRECTIONAL * 3)
        val colors = FloatArray(MAX_DIRECTIONAL * 3)
        val count = input.lights.directional.size.coerceAtMost(MAX_DIRECTIONAL)
        for (i in 0 until count) {
            val d = input.lights.directional[i]
            directions[i * 3] = d.direction.x; directions[i * 3 + 1] = d.direction.y; directions[i * 3 + 2] =
                d.direction.z
            colors[i * 3] = d.color.r; colors[i * 3 + 1] = d.color.g; colors[i * 3 + 2] = d.color.b
        }
        val ambient = input.ambient
        val fog = input.fog
        return WaterFrame(
            input.camera, input.width, input.height, seconds,
            floatArrayOf(ambient?.r ?: 0.25f, ambient?.g ?: 0.25f, ambient?.b ?: 0.25f),
            floatArrayOf(input.clear.r, input.clear.g, input.clear.b),
            floatArrayOf(fog?.color?.r ?: 0f, fog?.color?.g ?: 0f, fog?.color?.b ?: 0f),
            fog?.shaderCoefficient ?: 0f,
            count, directions, colors,
        )
    }

    /** The context is gone (the view was hidden): forget every GL object without GL calls and allow a retry. */
    fun abandon() {
        resources.abandon()
        drawable?.abandon()
        drawable = null
        drawableFailed = false
        com.badlogic.gdx.graphics.glutils.ShaderProgram.clearAllShaderPrograms(owner)
        depthShaders = ModelDepthShaderProvider(depthPass)
        renderables.clear(); pool.clear(); casterBounds.clear()
        mirrors.fill(null)
        reflectionOk.fill(false)
        surfaceFailures.clear()
        previousLevels = emptyList()
        lastPlan = null
    }

    override fun dispose() {
        resources.dispose()
        drawable?.dispose()
        drawable = null
        depthShaders.dispose()
        renderables.clear(); pool.clear(); casterBounds.clear()
        lastPlan = null
    }

    private companion object {
        const val TIME_WRAP = 86_400.0 * 30
        const val IDLE_FRAMES_BEFORE_RELEASE = 600
    }
}
