/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.gdx.AnimationController
import net.nevinsky.abyssus.lib.core.BaseCtx
import net.nevinsky.abyssus.lib.gdx.ModelBatch
import net.nevinsky.abyssus.lib.gdx.ModelInstance
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.loading.AssetStorage
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.model.ModelLoader
import net.nevinsky.abyssus.lib.core.assets.sky.SkyFrame
import net.nevinsky.abyssus.lib.core.assets.sky.SkyRenderer
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudsLoader
import net.nevinsky.abyssus.lib.core.assets.sky.cube.SkyboxLoader
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.ExrLoader
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrSkyLoader
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.ToneCurve
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSkyLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDrawable
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageRuntimeLoad
import net.nevinsky.abyssus.lib.core.assets.foliage.RuntimeFoliageLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.lib.core.assets.texture.TextureLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.Model
import net.nevinsky.abyssus.lib.core.scene.SceneEnvironment
import net.nevinsky.abyssus.lib.gdx.shader.DefaultShaderProvider
import net.nevinsky.abyssus.lib.gdx.shader.ShaderProvider
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.Executor

/**
 * The one asset storage of the project in [projectDir], wired by constructors like the plugin does: a composite over
 * every kind of asset, prepared on [executor] and built (and owned) by the storage on the GL thread.
 */
fun fieldAssets(
    projectDir: File,
    json: JsonProcessor,
    log: Logger,
    executor: Executor
): AssetStorage {
    val files = FileLoader(projectDir)
    val metas = AssetMetaLoader(json, files, log)
    val skyShaders = ShaderStorage().withAssets(files)
    val terrains = TerrainLoader(files, metas)
    return AssetStorage(log, executor, metas::loadBaseMeta).also {
        it.registerAll(
            mapOf(
                MetaType.MODEL to ModelLoader(metas, AssimpModelLoader(), files),
                MetaType.TERRAIN to terrains,
                MetaType.FOLIAGE to RuntimeFoliageLoader(metas, FoliageRuntimeLoad(files, metas, terrains)) { log.warn(it) },
                MetaType.TEXTURE to TextureLoader(files, metas),
                MetaType.PIXMAP_TEXTURE to TextureLoader(files, metas),
                MetaType.SKYBOX to SkyboxLoader(files, metas, skyShaders),
                MetaType.SKYBOX_PROCEDURAL to ProceduralSkyLoader(files, metas),
            MetaType.CLOUDS to CloudsLoader(metas),
                MetaType.SKYBOX_HDR to HdrSkyLoader(metas, ExrLoader(files), skyShaders, ToneCurve()),
            ),
        )
    }
}

/** A segment to draw: a control line from [from] to [to], in [color]. */
class LineSegment(val from: Vector3, val to: Vector3, val color: Color)

/**
 * The cloud technique the game draws its sky with. The asset's own (shells: eight noise layers per band at the full
 * window resolution, every frame) keeps a 3.2 core context under 40 frames a second; layered clouds cost almost nothing.
 */
private val GAME_CLOUDS = CloudTechnique.LAYERED

/** The texture unit of the terrain's shadow map: the splat layers and the splat map take the units before it. */
private const val SHADOW_UNIT = 6

/**
 * Draws a [FieldScene] (design decision 8): the sky, the terrain with the game's own shader, the models with the
 * default shader lit by the scene's sun and ambient light, and the control lines. The sun casts shadows from models and
 * terrain onto both ([FieldShadows]), aimed at the pilot. Assets load through `core`'s [AssetStorage] ([fieldAssets]):
 * prepared off the GL thread, built here. GL thread only.
 */
class FieldRenderer(private val ctx: BaseCtx) : Disposable {
    private val modelShaders = DefaultShaderProvider()
    private val batch = ModelBatch(modelShaders)
    private val terrainShader = ctx.shaderStorage.program("terrain")
    private val blank = Texture(Pixmap(1, 1, Pixmap.Format.RGBA8888).apply { setColor(Color.WHITE); fill() }, false)
    private val shapes = ShapeRenderer()
    private val instances = HashMap<Entity, Pair<Model, ModelInstance>>()
    private val playing = HashMap<Entity, Pair<String, AnimationController>>()
    private var scene: FieldScene? = null

    /** One drawable per terrain placement, made from the shared asset it was copied from and released before it. */
    private val placements = HashMap<Entity, Placement>()
    private val reported = HashSet<String>()

    /** Seconds of play drawn so far: the sky's clouds drift by it. */
    private var skyTime = 0.0
    private val environment = SceneEnvironment()
    private val sun = DirectionalLight()
    private val shadows = FieldShadows()

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
    fun draw(
        field: FieldScene, camera: Camera, lines: List<LineSegment> = emptyList(), hidden: Entity? = null,
        clips: Map<Entity, String> = emptyMap(), seconds: Float = 0f,
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

        field.skyName?.let { ctx.assetStorage.getAs<SkyRenderer>(it) }?.let { sky ->
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDepthMask(false)
            Gdx.gl.glDisable(GL20.GL_CULL_FACE)
            sky.draw(camera, SkyFrame(field.sunDirection, skyTime, technique = GAME_CLOUDS))
            Gdx.gl.glDepthMask(true)
        }

        val drawn = drawnModels(field)
        animate(drawn, clips, seconds)
        val terrains = field.terrains.mapNotNull { (entity, name) ->
            ctx.assetStorage.getAs<TerrainMesh>(name)?.let { it to field.position(entity).getTransform() }
        }
        val foliage = foliageOf(field)
        shadows.aim(sun, field.sunDirection, field.pilot?.let { field.position(it).localPosition } ?: Vector3.Zero)
        val light = shadows.casterCamera()
        for ((entity, placement) in foliage) placement.drawable.updateCasters(light, field.position(entity).getTransform())
        shadows.render(drawn.map { it.second }, terrains) { out, pool ->
            for (placement in foliage.values) placement.drawable.getCasterRenderables(out, pool)
        }
        shadows.attribute()?.let(environment::set)

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)
        terrainShader.bind()
        terrainShader.setUniformMatrix("u_projViewTrans", camera.combined)
        terrainShader.setUniformf("u_cameraPosition", camera.position)
        terrainShader.setUniformf("u_fogDensity", if (field.fogColor != null) field.fogDensity else 0f)
        terrainShader.setUniformf("u_fogGradient", field.fogGradient)
        terrainShader.setUniformf("u_fogColor", fog.r, fog.g, fog.b)
        terrainShader.setUniformf("u_lightDirection", field.sunDirection)
        terrainShader.setUniformf("u_lightColor", field.sunColor.r, field.sunColor.g, field.sunColor.b)
        terrainShader.setUniformf("u_ambient", field.ambient.r, field.ambient.g, field.ambient.b)
        terrainShader.setUniformMatrix("u_shadowMatrix", shadows.matrix)
        terrainShader.setUniformf("u_shadowTexel", 1f / SHADOW_MAP_SIZE)
        terrainShader.setUniformf("u_shadowBias", shadows.bias)
        terrainShader.setUniformi("u_shadowTexture", SHADOW_UNIT)
        for ((mesh, world) in terrains) {
            shadows.texture.bind(SHADOW_UNIT)
            terrainShader.setUniformMatrix("u_worldTrans", world)
            mesh.draw(terrainShader, blank)
        }

        // the viewer's own culling and buffers, after the light pass: casters never touch them
        for ((entity, placement) in foliage) placement.drawable.update(camera, field.position(entity).getTransform())
        batch.begin(camera)
        for ((entity, instance) in drawn) {
            if (entity === hidden) continue
            batch.render(instance, environment, ShaderProvider.DEFAULT_SHADER_KEY)
        }
        for (placement in foliage.values) batch.render(placement.drawable, environment, ShaderProvider.DEFAULT_SHADER_KEY)
        batch.end()

        if (lines.isNotEmpty()) {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
            shapes.projectionMatrix = camera.combined
            shapes.begin(ShapeRenderer.ShapeType.Line)
            for (line in lines) {
                shapes.color = line.color
                shapes.line(line.from, line.to)
            }
            shapes.end()
        }
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
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
                    ctx.log.warn("Foliage '${entry.foliage}' is bound to terrain '$bound', but entity ${entry.entityId} draws '${entry.terrain}': nothing drawn for it")
                }
                placements.remove(entry.entity)?.drawable?.dispose()
                continue
            }
            val current = placements[entry.entity]
            val placement = if (current != null && current.shared === shared) current else {
                current?.drawable?.dispose()
                Placement(shared, FoliageDrawable(shared.foliage, ctx.assetStorage)).also { placements[entry.entity] = it }
            }
            shown[entry.entity] = placement
        }
        return shown
    }

    /** The placement drawables of the last [draw], by entity: for tests. */
    internal fun placementDrawables(): Map<Entity, FoliageDrawable> = placements.mapValues { it.value.drawable }

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
        terrainShader.dispose()
        blank.dispose()
        shapes.dispose()
    }
}
