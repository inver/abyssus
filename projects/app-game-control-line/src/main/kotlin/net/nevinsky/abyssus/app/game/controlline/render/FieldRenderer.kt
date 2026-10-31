/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import net.nevinsky.abyssus.lib.render.light.setSkyLight
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.AnimationController
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.BaseCtx
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDrawable
import net.nevinsky.abyssus.lib.core.assets.loading.AssetStorage
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.SkyFrame
import net.nevinsky.abyssus.lib.core.assets.sky.SkyRenderer
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSky
import net.nevinsky.abyssus.lib.core.assets.standard.StandardAssetLoaders
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import com.badlogic.gdx.graphics.g3d.Environment
import net.nevinsky.abyssus.lib.gdx.g3d.ModelShaderProvider
import net.nevinsky.abyssus.lib.gdx.shader.EnvironmentLightAttribute
import net.nevinsky.abyssus.lib.gdx.shader.OutputTransformAttribute
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrEnvironment
import net.nevinsky.abyssus.lib.render.frame.FrameInputs
import net.nevinsky.abyssus.lib.render.frame.RenderFog
import net.nevinsky.abyssus.lib.render.light.DirectionalInput
import net.nevinsky.abyssus.lib.render.light.RenderLights
import net.nevinsky.abyssus.lib.render.terrain.*
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.Executor
import kotlin.math.min

/**
 * The one asset storage of the project in [projectDir], wired by constructors like the plugin does (both take the
 * [StandardAssetLoaders] table): a composite over every kind of asset, prepared on [executor] and built (and owned) by
 * the storage on the GL thread.
 */
fun fieldAssets(
    projectDir: File,
    json: JsonProcessor,
    log: Logger,
    executor: Executor
): AssetStorage {
    val files = FileLoader(projectDir)
    val metas = AssetMetaLoader(json, files, log)
    val loaders = StandardAssetLoaders(files, metas, ShaderStorage().withAssets(files), log = log)
    return AssetStorage(log, executor, metas::loadBaseMeta).also { it.registerAll(loaders.byType) }
}

/**
 *  A segment to draw: a control line or a debrief track from [from] to [to], in [color], [width] pixels wide
 * (drivers may cap it at 1). With a [thickness] above 0 it is a wire of that many metres, drawn to scale as a ribbon
 * facing the camera ([wireLook]) and [width] is ignored.
 */
class LineSegment(
    val from: Vector3, val to: Vector3, val color: Color, val width: Float = 1f, val thickness: Float = 0f,
)


/** The game's field view: draw a frame, resize with the window, and release GL resources. Raster and ray modes both
 * present through this interface; `Main` picks the implementation at launch (design decision 7). */
interface FieldView {
    /**
     * Whether this view draws the control lines as lit [Cable] geometry given in [draw]; one that does not (the ray view)
     * is given them as [LineSegment]s in `lines` like the other overlays.
     */
    val drawsCables: Boolean get() = false

    fun draw(
        field: FieldScene, camera: Camera, lines: List<LineSegment> = emptyList(), hidden: Entity? = null,
        clips: Map<Entity, String> = emptyMap(), seconds: Float = 0f, cables: List<Cable> = emptyList(),
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
) : FieldView, Disposable {
    /** What the field is drawn with: [requested], less the display transform on a context that cannot run it. */
    val settings: FieldRenderSettings = requested.forContext(Gdx.graphics.isGL30Available).also {
        if (it !== requested) ctx.log.warn("The display transform needs OpenGL 3.0; drawing the field without it")
    }

    private val modelShaders = ModelShaderProvider()
    private val batch = ModelBatch(modelShaders)
    private val terrainPass = TerrainPass(ctx.shaderStorage, TerrainVariant.GAME_SINGLE_SUN)
    private val shapes = ShapeRenderer()
    private val instances = HashMap<Entity, Pair<Model, ModelInstance>>()
    private val playing = HashMap<Entity, Pair<String, AnimationController>>()
    private var scene: FieldScene? = null

    /** After a frame: if the clouds held the game too slow, draws the next simpler technique from now on. */
    private fun stepDownClouds(sky: SkyRenderer, seconds: Float) {
        val drawn = (sky as? ProceduralSky)?.drawnTechnique
        val next = cloudStepDown.frame(seconds, drawn) ?: return
        clouds = next
        ctx.log.warn("${drawn?.key} clouds held the game under 30 frames a second for two seconds; drawing ${next.key} clouds instead")
    }

    /** One drawable per terrain placement, made from the shared asset it was copied from and released before it. */
    private val placements = HashMap<Entity, Placement>()
    private val reported = HashSet<String>()

    /**
     * The cloud technique the sky is drawn with: the caller's or the cloud asset's own (null) until volumetric clouds
     * hold the game too slow, then the next simpler one each time (never back up, never written to a file).
     */
    private var clouds: CloudTechnique? = clouds
    private val cloudStepDown = CloudStepDown()

    /** Seconds of play drawn so far: the sky's clouds drift by it. */
    private var skyTime = 0.0
    private val environment = Environment()
    private val sun = DirectionalLight()
    private val shadows = FieldShadows()
    private val cableMesh = CableMesh()

    /** The sky light the models and terrain are lit with this frame, and the one fading in over it. */
    private class SkyLight(val environment: HdrEnvironment, val fadeTo: HdrEnvironment?, val weight: Float) {
        val irradiance get() = environment.irradiance
    }

    /**
     * Moves the field sky's light one frame and sets it on the environment in place of the flat ambient colour, or sets the
     * flat ambient back when there is no light to give (the sky does not opt in with `lightsScene`, is not a procedural
     * sky, its first build is not done, or [FieldRenderSettings.skyLight] is off). Null then.
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
        field.models.all { ctx.assetStorage.getAs<Model>(it.second) != null }
                && field.terrains.all { ctx.assetStorage.getAs<TerrainMesh>(it.second) != null }
                && field.foliages.all { !ctx.assetStorage.isLoading(it.foliage) }

    /**
     * Draws [field] seen from [camera] with [lines]; [hidden] (the pilot whose eyes the camera is) is left out. Each
     * entity in [clips] plays that clip of its model once, from when it first appears there, and then holds its last
     * frame; [seconds] is the frame's time. A model without the clip is drawn as it is.
     */
    override val drawsCables: Boolean get() = true

    override fun draw(
        field: FieldScene, camera: Camera, lines: List<LineSegment>, hidden: Entity?,
        clips: Map<Entity, String>, seconds: Float, cables: List<Cable>,
    ) {
        skyTime += seconds
        if (scene !== field) {
            releasePlacements()
            instances.clear()
            playing.clear()
            scene = field
            environment.clear()
            environment.set(ColorAttribute(ColorAttribute.AmbientLight, field.ambient))
            field.fogColor?.let { environment.set(ColorAttribute(ColorAttribute.Fog, it)) }
            sun.set(field.sunColor, Vector3(field.sunDirection).scl(-1f))
            environment.add(sun)
            settings.outputTransform?.let { environment.set(OutputTransformAttribute(it)) }
        }
        val wanted = buildSet {
            addAll(field.models.map { it.second }.toSet())
            field.terrains.mapTo(this) { it.second }
            field.foliages.mapTo(this) { it.foliage }
            addAll(setOfNotNull(field.skyName))
        }
        wanted.forEach(ctx.assetStorage::request)
        ctx.assetStorage.update()

        val fog = field.fogColor ?: Color(0.6f, 0.7f, 0.85f, 1f)
        Gdx.gl.glClearColor(fog.r, fog.g, fog.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        val sky = field.skyName?.let { ctx.assetStorage.getAs<SkyRenderer>(it) }
        sky?.let {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDepthMask(false)
            Gdx.gl.glDisable(GL20.GL_CULL_FACE)
            sky.draw(camera, SkyFrame(field.sunDirection, skyTime, technique = clouds))
            Gdx.gl.glDepthMask(true)
            stepDownClouds(it, seconds)
        }
        val skyLight = lightWithSky(field, sky, seconds)

        val drawn = drawnModels(field)
        animate(drawn, clips, seconds)
        val terrains = field.terrains.mapNotNull { (entity, name) ->
            ctx.assetStorage.getAs<TerrainMesh>(name)?.let { it to field.position(entity).getTransform() }
        }
        val foliage = foliageOf(field)
        shadows.aim(sun, field.sunDirection, field.pilot?.let { field.position(it).localPosition } ?: Vector3.Zero)
        val light = shadows.casterCamera()
        for ((entity, placement) in foliage) placement.drawable.updateCasters(
            light,
            field.position(entity).getTransform()
        )
        shadows.render(drawn.map { it.second }, terrains) { out, pool ->
            for (placement in foliage.values) placement.drawable.getCasterRenderables(out, pool)
        }
        shadows.attribute()?.let(environment::set)

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)
        val sunInput = DirectionalInput(
            "sun",
            Vector3(field.sunDirection).scl(-1f),
            field.sunColor.r,
            field.sunColor.g,
            field.sunColor.b
        )
        val frame = FrameInputs(
            camera.combined, camera.position, floatArrayOf(field.ambient.r, field.ambient.g, field.ambient.b),
            RenderFog(fog.r, fog.g, fog.b, if (field.fogColor != null) field.fogDensity else 0f, field.fogGradient),
            RenderLights(listOf(sunInput)), TerrainVariant.GAME_SINGLE_SUN.profile, settings.outputTransform,
        )
        val sun = TerrainLighting.Sun(
            field.sunDirection, field.sunColor.r, field.sunColor.g, field.sunColor.b,
            SunShadowInput(shadows.texture, shadows.matrix, 1f / SHADOW_MAP_SIZE, shadows.bias),
            skyLight?.irradiance, skyLight?.fadeTo?.irradiance, skyLight?.weight ?: 0f,
        )
        terrainPass.draw(frame, sun, terrains.map { (mesh, world) -> TerrainDraw(mesh, world) })

        // the viewer's own culling and buffers, after the light pass: casters never touch them
        for ((entity, placement) in foliage) placement.drawable.update(camera, field.position(entity).getTransform())
        batch.begin(camera)
        for ((entity, instance) in drawn) {
            if (entity === hidden) continue
            batch.render(instance, environment)
        }
        for (placement in foliage.values) batch.render(placement.drawable, environment)
        cableMesh.update(cables, camera, Gdx.graphics.backBufferHeight.toFloat().coerceAtLeast(1f))
        batch.render(cableMesh, environment)
        batch.end()

        val (wires, plain) = lines.partition { it.thickness > 0f }
        if (plain.isNotEmpty()) {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            shapes.projectionMatrix = camera.combined
            // one batch per width: the width is a GL state, set outside begin / end
            for ((width, group) in plain.groupBy { it.width }) {
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
        }
        if (wires.isNotEmpty()) wireRenderer.draw(wires, camera, field.sunDirection)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
    }

    private val wireRenderer = WireRenderer()

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
                        "Foliage '${entry.foliage}' is bound to terrain '$bound',"
                                + " but entity ${entry.entityId} draws '${entry.terrain}': nothing drawn for it"
                    )
                }
                placements.remove(entry.entity)?.drawable?.dispose()
                continue
            }
            val current = placements[entry.entity]
            val placement = if (current != null && current.shared === shared) current else {
                current?.drawable?.dispose()
                Placement(shared, FoliageDrawable(shared.foliage, ctx.assetStorage)).also {
                    placements[entry.entity] = it
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
    internal fun placementDrawables(): Map<Entity, FoliageDrawable> = placements.mapValues { it.value.drawable }

    /** The environment the models are drawn with: for tests. */
    internal val modelEnvironment: Environment get() = environment

    /** The sun's shadow map: for tests. */
    internal val sunShadows: FieldShadows get() = shadows

    /** Releases every placement drawable; the shared assets stay with the storage and are released after them. */
    private fun releasePlacements() {
        for (placement in placements.values) placement.drawable.dispose()
        placements.clear()
        reported.clear()
    }

    private class Placement(val shared: FoliageDrawable, val drawable: FoliageDrawable)

    /** The scene's models that are built, each with its instance placed at the entity's transform. */
    private fun drawnModels(field: FieldScene): List<Pair<Entity, ModelInstance>> =
        field.models.mapNotNull { (entity, name) ->
            val model = ctx.assetStorage.getAs<Model>(name) ?: return@mapNotNull null
            val instance = instances[entity]?.takeIf { it.first === model }?.second
                ?: ModelInstance(model).also {
                    instances[entity] = model to it
                    playing.remove(entity)
                }
            instance.transform!!.set(field.position(entity).getTransform())
            entity to instance
        }

    /** Starts the [clips] not yet playing on the [drawn] models, and advances every playing clip by [seconds]. */
    private fun animate(drawn: List<Pair<Entity, ModelInstance>>, clips: Map<Entity, String>, seconds: Float) {
        for ((entity, instance) in drawn) {
            val clip = clips[entity] ?: continue
            if (playing[entity]?.first == clip || instance.getAnimation(clip) == null) continue
            playing[entity] = clip to AnimationController(instance).also { it.setAnimation(clip, 1) }
        }
        for ((_, controller) in playing.values) controller.update(seconds)
    }

    override fun dispose() {
        releasePlacements()
        ctx.assetStorage.dispose()
        shadows.dispose()
        modelShaders.dispose()
        terrainPass.dispose()
        cableMesh.dispose()
        shapes.dispose()
        wireRenderer.dispose()
    }
}
