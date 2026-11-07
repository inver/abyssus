/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute
import com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.environment.SpotLight
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.AnimationController
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import java.io.File
import java.util.WeakHashMap
import java.util.concurrent.Executor
import kotlin.math.cos
import net.nevinsky.abyssus.lib.core.BaseCtx
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDrawable
import net.nevinsky.abyssus.lib.core.assets.loading.AssetStorage
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.SkyFrame
import net.nevinsky.abyssus.lib.core.assets.sky.SkyRenderer
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CLOUD_FRAME_BUDGET
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudFrameBudget
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrEnvironment
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSky
import net.nevinsky.abyssus.lib.core.assets.standard.StandardAssetLoaders
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.light.LightKind
import net.nevinsky.abyssus.lib.core.light.LightPlacement
import net.nevinsky.abyssus.lib.core.light.pointLightOf
import net.nevinsky.abyssus.lib.core.light.scaledColor
import net.nevinsky.abyssus.lib.core.light.spotLightOf
import net.nevinsky.abyssus.lib.core.util.GAME_SCENE_MAX_POINT
import net.nevinsky.abyssus.lib.core.util.GAME_SCENE_MAX_SPOT
import net.nevinsky.abyssus.lib.gdx.g3d.ModelShaderProvider
import net.nevinsky.abyssus.lib.gdx.shader.EnvironmentLightAttribute
import net.nevinsky.abyssus.lib.gdx.shader.LinearTargetAttribute
import net.nevinsky.abyssus.lib.gdx.shader.OutputTransform
import net.nevinsky.abyssus.lib.gdx.shader.OutputTransformAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ProbeBoxAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.lib.render.frame.FrameInputs
import net.nevinsky.abyssus.lib.render.frame.RenderFog
import net.nevinsky.abyssus.lib.render.light.DirectionalInput
import net.nevinsky.abyssus.lib.render.light.PointInput
import net.nevinsky.abyssus.lib.render.light.RenderLights
import net.nevinsky.abyssus.lib.render.light.SpotInput
import net.nevinsky.abyssus.lib.render.light.selectLocalLights
import net.nevinsky.abyssus.lib.render.light.setSkyLight
import net.nevinsky.abyssus.lib.render.post.BloomPass
import net.nevinsky.abyssus.lib.render.post.CompositeInputs
import net.nevinsky.abyssus.lib.render.post.CompositePass
import net.nevinsky.abyssus.lib.render.post.HdrFrameTarget
import net.nevinsky.abyssus.lib.render.post.ReflectionProbe
import net.nevinsky.abyssus.lib.render.post.SsaoPass
import net.nevinsky.abyssus.lib.render.terrain.*
import org.slf4j.Logger

/**
 * The one asset storage of the project in [projectDir], wired by constructors like the plugin does (both take the
 * [StandardAssetLoaders] table): a composite over every kind of asset, prepared on [executor] and built (and owned) by
 * the storage on the GL thread.
 */
fun fieldAssets(
    projectDir: File,
    json: JsonProcessor,
    log: Logger,
    executor: Executor,
): AssetStorage {
    val files = FileLoader(projectDir)
    val metas = AssetMetaLoader(json, files, log)
    val loaders = StandardAssetLoaders(files, metas, ShaderStorage().withAssets(files), log = log)
    return AssetStorage(log, executor, metas::loadBaseMeta).also { it.registerAll(loaders.byType) }
}

/**
 * A segment to draw: a control line or a debrief track from [from] to [to], in [color], [width] pixels wide (drivers
 * may cap it at 1). With a [thickness] above 0 it is a wire of that many metres, drawn to scale as a ribbon facing the
 * camera ([wireLook]) and [width] is ignored.
 */
class LineSegment(
    val from: Vector3,
    val to: Vector3,
    val color: Color,
    val width: Float = 1f,
    val thickness: Float = 0f,
)

/**
 * The game's field view: draw a frame, resize with the window, and release GL resources. Raster and ray modes both
 * present through this interface; `Main` picks the implementation at launch (design decision 7).
 */
interface FieldView {
    /** What draws the field, as the start log and a crash record name it: `raster` or `ray`. */
    val kind: String

    /**
     * Whether this view draws the control lines as lit [Cable] geometry given in [draw]; one that does not (the ray
     * view) is given them as [LineSegment]s in `lines` like the other overlays.
     */
    val drawsCables: Boolean
        get() = false

    /**
     * Draws [field]. An entity in [poses] is drawn at that transform instead of its `PositionComponent`'s: the flying
     * plane's drawn pose, between its last two simulated steps (design decision D3), which the simulation never reads.
     */
    fun draw(
        field: FieldScene,
        camera: Camera,
        lines: List<LineSegment> = emptyList(),
        hidden: Entity? = null,
        clips: Map<Entity, String> = emptyMap(),
        seconds: Float = 0f,
        cables: List<Cable> = emptyList(),
        poses: Map<Entity, Matrix4> = emptyMap(),
    )

    fun resize(width: Float, height: Float)

    fun dispose()
}

/**
 * A field view that can give up while the game runs (the ray-traced view: a quality limit, a stalled frame, a lost
 * device). Once [failure] is set, the game disposes the view and continues with the raster field, stating the reason.
 */
interface FailableFieldView : FieldView {
    val failure: String?
}

/**
 * Draws a [FieldScene] (design decision 8): the sky, the terrain with the game's own shader, the models with the
 * default shader lit by the scene's sun and ambient light, and the control lines. The sun casts shadows from models and
 * terrain onto both ([FieldShadows]), aimed at the pilot. Assets load through `core`'s [AssetStorage] ([fieldAssets]):
 * prepared off the GL thread, built here. GL thread only.
 */
class FieldRenderer(
    private val ctx: BaseCtx,
    /** The cloud technique to draw the sky with; null: the sky's cloud asset's own. */
    clouds: CloudTechnique? = null,
    requested: FieldRenderSettings = FieldRenderSettings(),
    /** Marks the start and end of each pass for the performance workload; the game's does nothing. */
    private val probe: FrameProbe = FrameProbe.NONE,
    /** The frame interval (s) the cloud step-down holds the game to; the game passes [CLOUD_STEP_DOWN_INTERVAL]. */
    cloudFrameBudget: Float = CLOUD_FRAME_BUDGET,
) : FieldView, Disposable {
    override val kind: String
        get() = "raster"

    /**
     * What the field is drawn with: [requested], less the display transform and the final pass on a context that cannot
     * run them.
     */
    val settings: FieldRenderSettings =
        requested.forContext(Gdx.graphics.isGL30Available).also {
            if (it !== requested) {
                ctx.log.warn("The display transform and the final pass need OpenGL 3.0; drawing the field without them")
            }
        }

    // the linear HDR frame the surfaces are drawn into and the final pass that shows it (settings.post)
    private val hdr = HdrFrameTarget()
    private val composite = CompositePass()
    private val bloom = BloomPass()
    private val ssao = SsaoPass()

    /**
     * Whether the final pass draws bloom and ambient occlusion (the display settings' "High quality effects"); off, it
     * keeps only the transform, vignette and grade.
     */
    var highQuality = true

    // a scene without a sky lights its models with a probe of itself (OpenSpec control-line-pbr-rendering,
    // "Interiors reflect themselves"): taken once per scene, after its assets are built
    private val reflectionProbe = ReflectionProbe(ctx.shaderStorage)
    private var probeLight: HdrEnvironment? = null
    private var probeBox: ProbeBoxAttribute? = null
    private var probeTaken = false

    /**
     * Why the final pass cannot run on this context, once it failed; the field then ends each shader in the transform.
     */
    private var postUnavailable: String? = null
    private val viewport = BufferUtils.newIntBuffer(16)
    private val binding = BufferUtils.newIntBuffer(16)

    /**
     * Whether this frame goes through the final pass: settings ask for it and the HDR frame could be made at the size of
     * the current viewport. The first failure is logged and ends it for good.
     */
    private fun postThisFrame(): Boolean {
        if (settings.post == null || settings.outputTransform == null || postUnavailable != null) {
            return false
        }
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport)
        val width = viewport.get(2)
        val height = viewport.get(3)
        if (width <= 0 || height <= 0) {
            return false
        }
        if (!hdr.ensure(width, height)) {
            postUnavailable = "the context cannot make a multisampled RGBA16F framebuffer"
            ctx.log.warn("The field's final pass cannot run ({}); ending each shader in the transform", postUnavailable)
            return false
        }
        return true
    }

    // room for the point and spot lights one model is lit with (LightProfile.GAME_SCENE_LIGHTS)
    private val modelShaders =
        ModelShaderProvider(
            DefaultShader.Config().apply {
                numPointLights = GAME_SCENE_MAX_POINT
                numSpotLights = GAME_SCENE_MAX_SPOT
            }
        )
    private val batch = ModelBatch(modelShaders)
    private val terrainPass = TerrainPass(ctx.shaderStorage, TerrainVariant.GAME_SINGLE_SUN)
    private val shapes = ShapeRenderer()

    /**
     * What a model instance or foliage placement is kept for: the scene file's entity id and the storage key it draws.
     * A flight or the debrief loads the field again with new [Entity] objects; keyed so, the reload reuses what the
     * last scene built instead of rebuilding it (design decision D8, option 1).
     */
    private data class Slot(val entityId: Long, val key: String)

    private fun idOf(entity: Entity): Long = entity.getComponent(HeaderComponent::class.java)?.id ?: -1L

    private val instances = HashMap<Slot, Pair<Model, ModelInstance>>()
    private val playing = HashMap<Entity, Pair<String, AnimationController>>()
    private var scene: FieldScene? = null

    /** After a frame: if the clouds held the game too slow, draws the next simpler technique from now on. */
    private fun stepDownClouds(sky: SkyRenderer, seconds: Float) {
        val drawn = (sky as? ProceduralSky)?.drawnTechnique
        val next = cloudStepDown.frame(seconds, drawn) ?: return
        clouds = next
        ctx.log.warn(
            "{} clouds held the game's frames over {} ms for two seconds; drawing {} clouds instead",
            drawn?.key,
            "%.1f".format(java.util.Locale.ROOT, cloudStepDown.budgetSeconds * 1000f),
            next.key,
        )
    }

    /** One drawable per terrain placement, made from the shared asset it was copied from and released before it. */
    private val placements = HashMap<Slot, Placement>()
    private val reported = HashSet<String>()

    /**
     * The cloud technique the sky is drawn with: the caller's or the cloud asset's own (null) until volumetric clouds
     * hold the game too slow, then the next simpler one each time (never back up, never written to a file).
     */
    private var clouds: CloudTechnique? = clouds
    private val cloudStepDown = CloudStepDown(CloudFrameBudget(cloudFrameBudget))

    /** Seconds of play drawn so far: the sky's clouds drift by it. */
    private var skyTime = 0.0
    private val environment = Environment()
    private val sun = DirectionalLight()
    private var localLights = SceneLocalLights.NONE
    // by instance: an instance made again (a new build, a stopped clip) starts its own, and a dropped one goes with it
    private val instanceLights = WeakHashMap<ModelInstance, InstanceLights>()
    private val modelBox = BoundingBox()
    private val shadows = FieldShadows()
    private val cableMesh = CableMesh()
    private val culling = ModelCulling()

    /** The sky light the models and terrain are lit with this frame, and the one fading in over it. */
    private class SkyLight(val environment: HdrEnvironment, val fadeTo: HdrEnvironment?, val weight: Float) {
        val irradiance
            get() = environment.irradiance
    }

    /**
     * Moves the field sky's light one frame and sets it on the environment in place of the flat ambient colour, or sets
     * the flat ambient back when there is no light to give (the sky does not opt in with `lightsScene`, is not a
     * procedural sky, its first build is not done, or [FieldRenderSettings.skyLight] is off). Null then.
     */
    private fun lightWithSky(field: FieldScene, sky: SkyRenderer?, seconds: Float): SkyLight? {
        val procedural = sky as? ProceduralSky
        if (settings.skyLight && procedural != null) {
            procedural.updateLighting(field.sunDirection, skyTime, true, seconds.toDouble())
            val current = procedural.lightingEnvironment
            if (current != null) {
                val lighting = procedural.lighting
                val next = lighting?.next
                val light = SkyLight(current, next, lighting?.fade ?: 0f)
                environment.setSkyLight(current, next, light.weight)
                return light
            }
        }
        environment.remove(EnvironmentLightAttribute.Type)
        if (!environment.has(ColorAttribute.AmbientLight)) {
            environment.set(ColorAttribute(ColorAttribute.AmbientLight, field.ambient))
        }
        return null
    }

    /**
     * Whether the scene's assets are settled: its models and terrains are built, and its foliage is either built or
     * failed. Foliage is optional, so a missing or unsupported one must not keep the game on its loading screen.
     */
    fun loaded(field: FieldScene): Boolean =
        field.models.all { (entity, name) ->
            ctx.assetStorage.getAs<Model>(
                field.modelStorageName(
                    entity,
                    name,
                )
            ) != null
        } &&
            field.terrains.all { ctx.assetStorage.getAs<TerrainMesh>(it.second) != null } &&
            field.foliages.all { !ctx.assetStorage.isLoading(it.foliage) }

    /**
     * Draws [field] seen from [camera] with [lines]; [hidden] (the pilot whose eyes the camera is) is left out. Each
     * entity in [clips] plays that clip of its model once, from when it first appears there, and then holds its last
     * frame; [seconds] is the frame's time. A model without the clip is drawn as it is.
     */
    override val drawsCables: Boolean
        get() = true

    override fun draw(
        field: FieldScene,
        camera: Camera,
        lines: List<LineSegment>,
        hidden: Entity?,
        clips: Map<Entity, String>,
        seconds: Float,
        cables: List<Cable>,
        poses: Map<Entity, Matrix4>,
    ) {
        skyTime += seconds
        if (scene !== field) {
            keepOnly(field)
            culling.clear()
            scene = field
            environment.clear()
            environment.set(ColorAttribute(ColorAttribute.AmbientLight, field.ambient))
            field.fogColor?.let { environment.set(ColorAttribute(ColorAttribute.Fog, it)) }
            sun.set(field.sunColor, Vector3(field.sunDirection).scl(-1f))
            // a scene without a directional light (a closed hangar) has no sun at all, not a default one
            if (field.hasSun) {
                environment.add(sun)
            }
            localLights = SceneLocalLights.of(field)
            instanceLights.clear()
            shadows.forgetPoints()
            dropProbe()
            settings.outputTransform?.let { environment.set(OutputTransformAttribute(it)) }
        }
        val wanted = buildSet {
            field.models.mapTo(this) { (entity, name) -> field.modelStorageName(entity, name) }
            field.terrains.mapTo(this) { it.second }
            field.foliages.mapTo(this) { it.foliage }
            addAll(setOfNotNull(field.skyName))
        }
        wanted.forEach(ctx.assetStorage::request)
        ctx.assetStorage.update()

        val linear = postThisFrame()
        // the host's framebuffer and viewport, which the final pass draws into
        val host =
            if (linear) {
                Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, binding)
                binding.get(0)
            } else {
                0
            }
        if (linear) {
            environment.set(LinearTargetAttribute())
        } else {
            environment.remove(LinearTargetAttribute.Type)
        }

        val fog = field.fogColor ?: Color(0.6f, 0.7f, 0.85f, 1f)
        Gdx.gl.glClearColor(fog.r, fog.g, fog.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        val sky = field.skyName?.let { ctx.assetStorage.getAs<SkyRenderer>(it) }
        probe.begin(FrameSpan.SKY)
        sky?.let {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDepthMask(false)
            Gdx.gl.glDisable(GL20.GL_CULL_FACE)
            sky.draw(camera, SkyFrame(field.sunDirection, skyTime, technique = clouds))
            Gdx.gl.glDepthMask(true)
            stepDownClouds(it, seconds)
        }
        probe.end(FrameSpan.SKY)
        val skyLight = lightWithSky(field, sky, seconds)

        stopUnaskedClips(clips)
        val drawn = drawnModels(field, poses)
        animate(drawn, clips, seconds)
        val terrains =
            field.terrains.mapNotNull { (entity, name) ->
                ctx.assetStorage.getAs<TerrainMesh>(name)?.let { it to field.position(entity).getTransform() }
            }
        val foliage = foliageOf(field)
        probe.begin(FrameSpan.SHADOW)
        if (field.hasSun) {
            shadows.aim(sun, field.sunDirection, field.pilot?.let { field.position(it).localPosition } ?: Vector3.Zero)
            val light = shadows.casterCamera()
            for ((entity, placement) in foliage) placement.drawable.updateCasters(
                light,
                field.position(entity).getTransform(),
            )
            // only what the sun camera's volume meets is drawn into the map; animated models keep their rest-pose box
            // out of it
            val lightFrustum = light.frustum
            val casters =
                drawn.filter { (entity, instance) -> entity in clips || culling.visible(instance, lightFrustum) }
            shadows.render(casters.map { it.second }, terrains) { out, pool ->
                for (placement in foliage.values) placement.drawable.getCasterRenderables(out, pool)
            }
            shadows.attribute()?.let(environment::set)
        } else if (settings.localShadows && !localLights.isEmpty) {
            // no sun (a closed hangar): the scene's spot lights, and its point lights nearest the scene camera, cast
            // the
            // shadows instead; the point lights' cubes see only the static models, drawn once they are all built
            shadows.renderInterior(
                localLights.spotCasters,
                drawn.filter { it.first !== hidden }.map { it.second },
                localLights.pointCasters,
            ) {
                if (loaded(field)) {
                    drawn.filter { it.first in field.staticModels }.map { it.second }
                } else {
                    null
                }
            }
            shadows.attribute()?.let(environment::set) ?: environment.remove(ShadowAtlasAttribute.Type)
        }
        probe.end(FrameSpan.SHADOW)
        if (skyLight == null) {
            lightWithProbe(field, camera, drawn)
        } else {
            environment.remove(ProbeBoxAttribute.Type)
        }

        if (linear) {
            // the surfaces go into the linear frame; the sky stays on the host's framebuffer, as display colour
            hdr.bind()
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        }
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)
        // no sun: the terrain is lit by a black one, the models by none
        val sunColor = if (field.hasSun) field.sunColor else Color.BLACK
        val sunInput =
            DirectionalInput(
                "sun",
                Vector3(field.sunDirection).scl(-1f),
                sunColor.r,
                sunColor.g,
                sunColor.b,
            )
        val frame =
            FrameInputs(
                camera.combined,
                camera.position,
                floatArrayOf(field.ambient.r, field.ambient.g, field.ambient.b),
                RenderFog(
                    fog.r,
                    fog.g,
                    fog.b,
                    if (field.fogColor != null) {
                        field.fogDensity
                    } else {
                        0f
                    },
                    field.fogGradient,
                ),
                RenderLights(listOf(sunInput)),
                TerrainVariant.GAME_SINGLE_SUN.profile,
                settings.outputTransform,
                linearTarget = linear,
            )
        val sun =
            TerrainLighting.Sun(
                field.sunDirection,
                sunColor.r,
                sunColor.g,
                sunColor.b,
                SunShadowInput(shadows.texture, shadows.matrix, 1f / SHADOW_MAP_SIZE, shadows.bias),
                skyLight?.irradiance,
                skyLight?.fadeTo?.irradiance,
                skyLight?.weight ?: 0f,
            )
        probe.begin(FrameSpan.TERRAIN)
        terrainPass.draw(frame, sun, terrains.map { (mesh, world) -> TerrainDraw(mesh, world) })
        probe.end(FrameSpan.TERRAIN)

        // the viewer's own culling and buffers, after the light pass: casters never touch them
        probe.begin(FrameSpan.MODELS)
        for ((entity, placement) in foliage) placement.drawable.update(camera, field.position(entity).getTransform())
        batch.begin(camera)
        lastDrawnModels = 0
        for ((entity, instance) in drawn) {
            if (entity === hidden) {
                continue
            }
            if (entity !in clips && !culling.visible(instance, camera.frustum)) {
                continue
            }
            batch.render(instance, environmentOf(instance))
            lastDrawnModels++
        }
        for (placement in foliage.values) {
            batch.render(placement.drawable, environment)
        }
        cableMesh.update(cables, camera, Gdx.graphics.backBufferHeight.toFloat().coerceAtLeast(1f))
        batch.render(cableMesh, environment)
        batch.end()
        probe.end(FrameSpan.MODELS)

        probe.begin(FrameSpan.LINES)
        val (wires, plain) = lines.partition { it.thickness > 0f }
        if (plain.isNotEmpty()) {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            blendForCoverage(linear)
            shapes.projectionMatrix = camera.combined
            // one batch per width: the width is a GL state, set outside begin / end
            for ((width, group) in plain.groupBy { it.width }) {
                Gdx.gl.glLineWidth(width)
                shapes.begin(ShapeRenderer.ShapeType.Line)
                for (line in group) {
                    shapes.color =
                        if (linear) {
                            decode(lineColor.set(line.color))
                        } else {
                            line.color
                        }
                    shapes.line(line.from, line.to)
                }
                shapes.end()
            }
            Gdx.gl.glLineWidth(1f)
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }
        if (wires.isNotEmpty()) {
            wireRenderer.draw(wires, camera, field.sunDirection, linear)
        }
        probe.end(FrameSpan.LINES)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        if (linear) {
            probe.begin(FrameSpan.POST)
            showFrame(host, checkNotNull(settings.post), checkNotNull(settings.outputTransform), camera)
            probe.end(FrameSpan.POST)
        }
    }

    private val lineColor = Color()

    /** Releases the probe of the last scene and lets the next scene take its own. */
    private fun dropProbe() {
        reflectionProbe.dispose()
        probeLight?.dispose()
        probeLight = null
        probeBox = null
        probeTaken = false
    }

    /**
     * For a scene without a sky (none to light it): takes the probe of its static models, a few faces a frame, once
     * they are built, from the scene camera's position (else [camera]'s), and then lights the models with it, its
     * reflection corrected to the box around them. Until it is done, or if it fails (logged once), the flat ambient
     * stays.
     */
    private fun lightWithProbe(field: FieldScene, camera: Camera, drawn: List<Pair<Entity, ModelInstance>>) {
        if (field.skyName != null || settings.outputTransform == null || !settings.reflectionProbe) {
            return
        }
        if (!probeTaken && !reflectionProbe.busy && loaded(field)) {
            probeTaken = true
            reflectionProbe.start(field.camera?.first?.localPosition ?: camera.position)
        }
        if (reflectionProbe.busy) {
            val statics = drawn.filter { it.first in field.staticModels }
            val done = reflectionProbe.step { face -> drawProbeFace(face, statics) }
            if (done != null) {
                probeLight = done
                probeBox = boxOf(statics.map { it.second }, reflectionProbe.camera.position)
            } else if (!reflectionProbe.busy) {
                ctx.log.warn("The scene's reflection probe failed ({}); keeping the flat ambient", reflectionProbe.failure)
            }
        }
        val light = probeLight ?: return
        environment.setSkyLight(light, null, 0f)
        probeBox?.let(environment::set)
    }

    /**
     * Draws [statics] seen from [face] into the probe's bound face, in linear light (the probe's cube holds light, not
     * display colour), with the scene's ambient and lights.
     */
    private fun drawProbeFace(face: Camera, statics: List<Pair<Entity, ModelInstance>>) {
        val wasLinear = environment.has(LinearTargetAttribute.Type)
        environment.set(LinearTargetAttribute())
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)
        try {
            batch.begin(face)
            for ((_, instance) in statics) {
                if (culling.visible(instance, face.frustum)) {
                    batch.render(instance, environmentOf(instance))
                }
            }
            batch.end()
        } finally {
            if (!wasLinear) {
                environment.remove(LinearTargetAttribute.Type)
            }
        }
    }

    /** The world box around [instances] (their models' own bounds, transformed), with the probe at [probe]. */
    private fun boxOf(instances: List<ModelInstance>, probe: Vector3): ProbeBoxAttribute? {
        val all = BoundingBox().inf()
        val one = BoundingBox()
        for (instance in instances) {
            instance.calculateBoundingBox(one.inf())
            if (one.isValid) {
                all.ext(one.mul(instance.transform))
            }
        }
        if (!all.isValid || !all.contains(probe)) {
            return null
        }
        return ProbeBoxAttribute(Vector3(all.min), Vector3(all.max), Vector3(probe))
    }

    /** Resolves the linear frame and shows it over the sky on [framebuffer] through the final pass with [post]. */
    private fun showFrame(framebuffer: Int, post: PostSettings, output: OutputTransform, camera: Camera) {
        hdr.resolve(framebuffer)
        val glow =
            if (highQuality && post.bloom && post.bloomStrength > 0f) {
                bloom.render(hdr.colorTexture, hdr.width, hdr.height)
            } else {
                0
            }
        val openness =
            if (highQuality && post.ambientOcclusion && post.aoStrength > 0f) {
                ssao.render(hdr.depthTexture, camera.projection, hdr.width, hdr.height)
            } else {
                0
            }
        Gdx.gl.glViewport(0, 0, hdr.width, hdr.height)
        composite.draw(
            hdr.colorTexture,
            hdr.width,
            hdr.height,
            CompositeInputs(
                output,
                bloom = glow,
                bloomStrength = post.bloomStrength,
                ambientOcclusion = openness,
                aoStrength = post.aoStrength,
                vignette = post.vignette,
                grade = post.grade,
            ),
        )
    }

    private val wireRenderer = WireRenderer()

    /**
     * The environment [instance] is drawn with: the scene's, plus the point and spot lights that reach it
     * ([selectLocalLights]). The scene's own when the scene has no such light. The choice is kept per instance and made
     * again only when the instance moved; the scene's attributes are copied in every frame (its shadow and sky light
     * change).
     */
    private fun environmentOf(instance: ModelInstance): Environment {
        if (localLights.isEmpty) {
            return environment
        }
        val lights = instanceLights.getOrPut(instance) { InstanceLights() }
        if (!lights.placedAt(instance.transform)) {
            val lit = culling.bounds(instance, modelBox)
            val chosen =
                if (lit) {
                    selectLocalLights(localLights.points, localLights.spots, modelBox)
                } else {
                    null
                }
            lights.choose(chosen, localLights)
        }
        return lights.environmentOver(environment)
    }

    /**
     * The drawables of the scene's foliage that can be shown, one per terrain entity: made from the shared asset the
     * storage built (its prepared copies and the shared models), so two terrains using one asset never share instance
     * buffers or culling. A placement is rebuilt when its asset is, and left out, with one log line, when its asset is
     * bound to another terrain than the entity draws.
     */
    private fun foliageOf(field: FieldScene): Map<Entity, Placement> {
        val shown = LinkedHashMap<Entity, Placement>()
        for (entry in field.foliages) {
            val shared = ctx.assetStorage.getAs<FoliageDrawable>(entry.foliage) ?: continue
            val bound = shared.foliage.meta.terrain
            if (bound != entry.terrain) {
                if (reported.add("${entry.entityId}:${entry.foliage}")) {
                    ctx.log.warn(
                        "Foliage '${entry.foliage}' is bound to terrain '$bound'," +
                            " but entity ${entry.entityId} draws '${entry.terrain}': nothing drawn for it"
                    )
                }
                placements.remove(Slot(entry.entityId, entry.foliage))?.drawable?.dispose()
                continue
            }
            val slot = Slot(entry.entityId, entry.foliage)
            val current = placements[slot]
            val placement =
                if (current != null && current.shared === shared) {
                    current
                } else {
                    current?.drawable?.dispose()
                    Placement(shared, FoliageDrawable(shared.foliage, ctx.assetStorage)).also {
                        placements[slot] = it
                    }
                }
            shown[entry.entity] = placement
        }
        return shown
    }

    override fun resize(width: Float, height: Float) {
        // The renderer draws from the camera passed to [draw], so a window resize needs no stored viewport state.
    }

    /** The placement drawables of the last [draw], by entity: for tests. */
    /** How many model instances the last [draw] submitted to the view (after culling). */
    internal var lastDrawnModels = 0
        private set

    /** The foliage drawables of the current scene, by its terrain entities. */
    internal fun placementDrawables(): Map<Entity, FoliageDrawable> =
        scene
            ?.foliages
            .orEmpty()
            .mapNotNull { entry ->
                placements[Slot(entry.entityId, entry.foliage)]?.let { entry.entity to it.drawable }
            }
            .toMap()

    /** Whether the models are lit by the scene's own reflection probe now: for tests. */
    internal val litByProbe: Boolean
        get() = probeLight != null && environment.has(EnvironmentLightAttribute.Type)

    /** The box the probe's reflection is corrected to, if any: for tests. */
    internal val probeBounds: ProbeBoxAttribute?
        get() = probeBox

    /** The environment the models are drawn with: for tests. */
    internal val modelEnvironment: Environment
        get() = environment

    /** The sun's shadow map: for tests. */
    internal val sunShadows: FieldShadows
        get() = shadows

    /** Releases every placement drawable; the shared assets stay with the storage and are released after them. */
    private fun releasePlacements() {
        for (placement in placements.values) placement.drawable.dispose()
        placements.clear()
        reported.clear()
    }

    /**
     * The scene changed to [field]: keeps the instances and placements it draws again, releases the others. An instance
     * that played a clip is not kept, as its nodes hold the clip's last pose.
     */
    private fun keepOnly(field: FieldScene) {
        for (entity in playing.keys) instances.keys.removeIf { it.entityId == idOf(entity) }
        playing.clear()
        val models =
            field.models.mapTo(HashSet()) { (entity, name) -> Slot(idOf(entity), field.modelStorageName(entity, name)) }
        instances.keys.retainAll(models)
        val foliage = field.foliages.mapTo(HashSet()) { Slot(it.entityId, it.foliage) }
        val gone = placements.keys.filter { it !in foliage }
        for (slot in gone) placements.remove(slot)?.drawable?.dispose()
        reported.clear()
    }

    private class Placement(val shared: FoliageDrawable, val drawable: FoliageDrawable)

    /**
     * A scene's point and spot lights, as the selection reads them and as the model shaders take them, by entity id.
     */
    private class SceneLocalLights(
        val points: List<PointInput>,
        val spots: List<SpotInput>,
        val pointLights: Map<String, PointLight>,
        val spotLights: Map<String, SpotLight>,
        /** The spot lights that cast shadows in a scene without a sun, the strongest first. */
        val spotCasters: List<SpotCaster> = emptyList(),
        /** The point lights that cast shadows in a scene without a sun, the nearest the scene camera first. */
        val pointCasters: List<PointCaster> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = points.isEmpty() && spots.isEmpty()

        companion object {
            val NONE = SceneLocalLights(emptyList(), emptyList(), emptyMap(), emptyMap())

            fun of(field: FieldScene): SceneLocalLights {
                val points = field.localLights.filter { it.kind == LightKind.POINT }
                val spots = field.localLights.filter { it.kind == LightKind.SPOT }
                val spotInputs =
                    spots.map {
                        val c = scaledColor(it)
                        val outer = cos(Math.toRadians(it.coneAngle / 2.0)).toFloat()
                        val inner = cos(Math.toRadians(it.coneAngle / 2.0 * (1 - it.edgeSoftness))).toFloat()
                        SpotInput(
                            it.entityId.toString(),
                            it.position,
                            it.direction,
                            c.r,
                            c.g,
                            c.b,
                            it.range,
                            outer,
                            inner,
                        )
                    }
                val spotLights = spots.associate { it.entityId.toString() to spotLightOf(it) }
                val pointLights = points.associate { it.entityId.toString() to pointLightOf(it) }
                val eye = field.camera?.first?.localPosition
                val pointCasters =
                    if (eye == null) {
                        emptyList()
                    } else {
                        points
                            .sortedWith(compareBy<LightPlacement> { it.position.dst2(eye) }.thenBy { it.entityId })
                            .map {
                                PointCaster(
                                    it.entityId.toString(),
                                    pointLights.getValue(it.entityId.toString()),
                                    it.position,
                                    it.range,
                                )
                            }
                    }
                return SceneLocalLights(
                    points.map {
                        val c = scaledColor(it)
                        PointInput(it.entityId.toString(), it.position, c.r, c.g, c.b, it.range)
                    },
                    spotInputs,
                    pointLights,
                    spotLights,
                    spots
                        .sortedWith(compareByDescending<LightPlacement> { it.intensity }.thenBy { it.entityId })
                        .map {
                            SpotCaster(
                                it.entityId.toString(),
                                spotLights.getValue(it.entityId.toString()),
                                it.position,
                                it.direction,
                                it.coneAngle,
                                it.range,
                            )
                        },
                    pointCasters,
                )
            }
        }
    }

    /** One model instance's own environment and the local lights chosen for it at its last transform. */
    private class InstanceLights {
        private val environment = Environment()
        private val points = PointLightsAttribute()
        private val spots = SpotLightsAttribute()
        private val at = Matrix4()
        private var placed = false

        /** Whether the lights were chosen with the instance at [transform]; remembers [transform] if not. */
        fun placedAt(transform: Matrix4): Boolean {
            if (placed && at.`val`.contentEquals(transform.`val`)) {
                return true
            }
            at.set(transform)
            placed = true
            return false
        }

        /** Keeps the [chosen] lights (none when null), as [scene] holds them for the shaders. */
        fun choose(chosen: RenderLights?, scene: SceneLocalLights) {
            points.lights.clear()
            spots.lights.clear()
            chosen?.point?.forEach { p -> scene.pointLights[p.entityId]?.let(points.lights::add) }
            chosen?.spot?.forEach { s -> scene.spotLights[s.entityId]?.let(spots.lights::add) }
        }

        /** The [scene] environment's attributes with this instance's lights over them. */
        fun environmentOver(scene: Environment): Environment {
            environment.clear()
            environment.set(scene)
            if (points.lights.size > 0) {
                environment.set(points)
            }
            if (spots.lights.size > 0) {
                environment.set(spots)
            }
            return environment
        }
    }

    /**
     * The scene's models that are built, each with its instance placed at the entity's transform (or its pose in
     * [poses]).
     */
    private fun drawnModels(field: FieldScene, poses: Map<Entity, Matrix4>): List<Pair<Entity, ModelInstance>> =
        field.models.mapNotNull { (entity, name) ->
            val key = field.modelStorageName(entity, name)
            val model = ctx.assetStorage.getAs<Model>(key) ?: return@mapNotNull null
            culling.track(key, ctx.assetStorage.version(key), model)
            val slot = Slot(idOf(entity), key)
            val instance =
                instances[slot]?.takeIf { it.first === model }?.second
                    ?: ModelInstance(model).also {
                        instances[slot] = model to it
                        playing.remove(entity)
                    }
            instance.transform!!.set(poses[entity] ?: field.position(entity).getTransform())
            entity to instance
        }

    /**
     * Stops a clip no longer asked for in [clips] (the debrief replays the flight's own scene): its instance is made
     * again in its rest pose before this frame's models are placed.
     */
    private fun stopUnaskedClips(clips: Map<Entity, String>) {
        if (playing.isEmpty() || playing.keys.all { it in clips }) {
            return
        }
        for (entity in playing.keys.filter { it !in clips }) {
            playing.remove(entity)
            instances.keys.removeIf { it.entityId == idOf(entity) }
        }
    }

    /** Starts the [clips] not yet playing on the [drawn] models, and advances every playing clip by [seconds]. */
    private fun animate(drawn: List<Pair<Entity, ModelInstance>>, clips: Map<Entity, String>, seconds: Float) {
        for ((entity, instance) in drawn) {
            val clip = clips[entity] ?: continue
            if (playing[entity]?.first == clip || instance.getAnimation(clip) == null) {
                continue
            }
            playing[entity] = clip to AnimationController(instance).also { it.setAnimation(clip, 1) }
        }
        for ((_, controller) in playing.values) controller.update(seconds)
    }

    /** Releases what this renderer made; the asset storage is the context's ([BaseCtx.close]). */
    override fun dispose() {
        releasePlacements()
        shadows.dispose()
        composite.dispose()
        bloom.dispose()
        ssao.dispose()
        dropProbe()
        hdr.dispose()
        modelShaders.dispose()
        terrainPass.dispose()
        cableMesh.dispose()
        shapes.dispose()
        wireRenderer.dispose()
    }
}
