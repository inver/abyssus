/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.ray.RayFrameContext
import net.nevinsky.abyssus.lib.core.editor.ray.RaySceneDisplay
import net.nevinsky.abyssus.lib.core.editor.ray.RaySkyBaker
import net.nevinsky.abyssus.lib.core.editor.scene.AssetRevisionBatch
import net.nevinsky.abyssus.lib.core.editor.scene.MAX_POINT
import net.nevinsky.abyssus.lib.core.editor.scene.ModelEntity
import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.core.editor.scene.NO_LIGHTS
import net.nevinsky.abyssus.lib.core.editor.scene.PendingAssetRevision
import net.nevinsky.abyssus.lib.core.editor.scene.lightSetOf
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.scene.cameraDirectionOf
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import net.nevinsky.abyssus.lib.core.editor.pick.FrameSnapshot
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.pick.SceneMarkers
import net.nevinsky.abyssus.lib.core.editor.pick.ScenePreview
import net.nevinsky.abyssus.lib.core.editor.pick.SceneQueries
import net.nevinsky.abyssus.lib.core.editor.pick.SceneViewState
import net.nevinsky.abyssus.lib.core.editor.pick.SnapshotSceneQueries
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import net.nevinsky.abyssus.lib.core.editor.pick.aspectOf
import net.nevinsky.abyssus.lib.core.editor.pick.copyOfCamera
import net.nevinsky.abyssus.lib.core.editor.pick.gizmoHandlesFor
import net.nevinsky.abyssus.lib.core.editor.pick.snapshotBoxOf
import net.nevinsky.abyssus.lib.core.editor.pick.snapshotTerrainOf
import net.nevinsky.abyssus.lib.core.editor.content.Pose
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.LightPlacement

import java.io.File
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.intellij.openapi.Disposable
import net.nevinsky.abyssus.lib.core.shader.DefaultShaderProvider
import net.nevinsky.abyssus.lib.core.shader.EnvironmentLightAttribute
import net.nevinsky.abyssus.lib.core.shader.ShaderProvider
import net.nevinsky.abyssus.plugin.sceneview.fog.FogShaderProvider
import net.nevinsky.abyssus.plugin.sceneview.gizmo.GizmoDraw
import net.nevinsky.abyssus.plugin.sceneview.skybox.SkyClock
import net.nevinsky.abyssus.plugin.sceneview.skybox.SunDirection
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.SunOcclusion
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSky
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSkyMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.SKY_CAMERA_HEIGHT
import net.nevinsky.abyssus.plugin.sceneview.terrain.TerrainShader
import net.nevinsky.abyssus.plugin.sceneview.shadows.SceneShadows
import net.nevinsky.abyssus.lib.core.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.lib.core.ModelBatch as ContentBatch
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.CAMERA_FOV

/**
 * Draws a scene's environment, a ground grid and the content the scene places (skybox, terrains, models), and picks
 * entities under the cursor. Assets come from [assets]; the grid, overlay and terrain programs from [shaders]. Call only inside [GdxRuntime.withContext]
 * with the GL context current; picking, ground queries and interaction geometry use CPU data only.
 */
class SceneRenderer(
    val assets: ViewAssets,
    private val shaders: ShaderStorage,
    /** What the user selected, previews and looks through; the view panel changes it, this draws from it. */
    val state: SceneViewState = SceneViewState(),
) : Disposable {
    @Volatile
    var params: SceneRenderParams = SceneRenderParams.DEFAULT

    private var posedBase: SceneContent? = null
    private var posedPoses: Map<String, Pose>? = null
    private var posed: SceneContent? = null

    /** The scene's content with the simulated poses applied: the authored content while nothing plays. */
    internal val posedContent: SceneContent
        get() {
            val c = params.content
            val poses = state.poses
            if (poses.isEmpty()) return c
            posed?.takeIf { c === posedBase && poses === posedPoses }?.let { return it }
            return ScenePreview().withPoses(c, poses).also {
                posed = it
                posedBase = c
                posedPoses = poses
            }
        }

    /** The scene's content with simulated poses and then the previewed transforms applied. */
    internal val content: SceneContent
        get() {
            val c = posedContent
            val p = state.preview
            return if (p.isEmpty()) c else ScenePreview().apply(c, p)
        }

    /** Other plugins' overlays for this view; drawn after the markers, then again over everything. */
    internal var overlays: SceneOverlayHost? = null

    private var batch: ModelBatch? = null
    private var contentBatch: ContentBatch? = null
    private var contentShaders: DefaultShaderProvider? = null
    /** Asset changes waiting for a frame that can safely replace GL resources; merged until [render] takes them. */
    private val pendingRevision = PendingAssetRevision()

    private val models = SceneModels(AssetView(assets, net.nevinsky.abyssus.lib.core.model.Model::class.java))
    private val terrains = SceneTerrains(AssetView(assets, net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh::class.java))
    private var terrainShader: TerrainShader? = null
    private var shadows: SceneShadows? = null
    internal var shadowedLightIds: Set<String> = emptySet()
        private set
    private var skybox: SceneSkybox? = null
    private var overlay: LoadingOverlay? = null
    private var lightsKey: Pair<List<LightPlacement>, Vec3>? = null
    private var lights = NO_LIGHTS

    /** [lights] with the sun dimmed by the clouds over it: what the environment and the terrain shader light with. */
    private var frameLights = NO_LIGHTS
    private var frameLightsKey: Triple<LightSet, String?, Float>? = null
    private val sunOcclusion = SunOcclusion()

    /** The time this view's sky is drawn at; clouds drift by it. */
    internal val skyClock = SkyClock()

    /** The share of the sun's light the clouds let through in the last frame (1 without clouds). */
    internal val sunScale: Float get() = sunOcclusion.transmittance

    /** The lights the last frame lit the scene with (the sun dimmed by clouds), for tests. */
    internal val litLights: LightSet get() = frameLights

    /** The cloud technique the last frame drew; null when it drew no clouds. */
    internal var drawnCloudTechnique: CloudTechnique? = null
        private set

    /**
     * True when the sky of [p] is a procedural sky whose `meta.json` names a cloud asset it can still draw (once built,
     * a sky whose cloud asset is missing or empty, or that every technique failed for, has none). Reads the metadata, no
     * GL: the view's Clouds choice follows it.
     */
    internal fun cloudsEnabled(p: SceneRenderParams = params): Boolean {
        val name = p.content.skybox ?: return false
        val dir = p.projectDir ?: return false
        val meta = assets.project(dir).metas.loadBaseMeta(name)?.takeIf { it.type == MetaType.SKYBOX_PROCEDURAL } ?: return false
        if (meta.typedAdditional<ProceduralSkyMeta>().cloudsReference == null) return false
        return (skybox?.sky(name) as? ProceduralSky)?.hasClouds ?: true
    }
    private var lineBatch: LineBatch? = null
    private var gridModel: Model? = null
    private var grid: ModelInstance? = null
    private val camera = PerspectiveCamera()
    private val rayCamera = PerspectiveCamera()
    private var rayPresenter: RayFramePresenter? = null
    /** Invoked after current preview/camera/animation updates, in the safe canvas context. */
    internal var rayFrameProvider: ((RayFrameContext) -> RaySceneDisplay?)? = null
    internal var presentedRayFrame = false
        private set
    private val environment = Environment()

    @Volatile
    private var fogCoefficient: Float? = null

    /** True while assets are loading and the overlay is drawn. */
    @Volatile
    internal var loading = false
        private set

    internal var lastWidth = 0
        private set
    internal var lastHeight = 0
        private set

    /** The model entities drawn in the last frame (for tests). */
    internal val drawnModels: Collection<ModelEntity> get() = models.drawn

    internal val drawnTerrains: Collection<TerrainEntity> get() = terrains.drawn

    /** How many camera markers and whether a gizmo were drawn in the last frame (for tests). */
    internal var drawnCameraMarkers = 0
        private set
    internal var drewGizmo = false
        private set

    /** What the last frame drew, for [queries]; null before the first frame and after the context was replaced. */
    private var snapshot: FrameSnapshot? = null

    /** CPU-only questions about the scene as the last frame drew it: picking, ground below, gizmo handles. */
    val queries: SceneQueries = SnapshotSceneQueries({ snapshot }, state) { posedContent }

    /** Copies what was drawn so the queries need neither this renderer nor GL. */
    internal fun publishSnapshot() {
        snapshot = FrameSnapshot(
            copyOfCamera(camera),
            models.drawn.map { snapshotBoxOf(it.placement.entityId, it.localBounds, it.instance.transform!!) },
            terrains.drawn.map { snapshotTerrainOf(TerrainTarget(it.placement.entityId, it.terrain.data, it.world)) },
            drawnVersion,
        )
    }

    /** Changes only when a model or terrain enters or leaves the drawn lists, including context replacement. */
    var drawnVersion: Long = 0
        private set
    private var drawnIds: Pair<Set<String>, Set<String>> = emptySet<String>() to emptySet()
    private var drawnAssets: List<Pair<String, Int>> = emptyList()

    private fun updateDrawnVersion() {
        val fresh = models.drawn.mapTo(HashSet()) { it.placement.entityId } to terrains.drawn.mapTo(HashSet()) { it.placement.entityId }
        // a replaced asset (new heights under the same entity) changes what the entities are standing on
        val assets = models.drawn.map { it.placement.entityId to System.identityHashCode(it.model) } +
            terrains.drawn.map { it.placement.entityId to System.identityHashCode(it.terrain) }
        if (fresh != drawnIds || assets != drawnAssets) { drawnIds = fresh; drawnAssets = assets; drawnVersion++ }
    }

    /** The camera as of the last frame (for tests). */
    internal val frameCamera: PerspectiveCamera get() = camera

    /**
     * Starts with a clean slate. When the previous GL context was abandoned instead of released (the view was hidden),
     * the models and terrains cached from it are invalid in the new context and must be loaded again.
     */
    fun create() {
        // An abandoned context owns the previous handles; never delete those through a new context.
        rayPresenter = null
        abandonShadows()
        shadows = SceneShadows()
        models.abandon()
        terrains.abandon()
        snapshot = null
        // the previous context's GL objects went with it: forget them without GL calls, as for models and terrains
        skybox?.abandon()
        skybox = null
        overlay = null
        lineBatch = null
        terrainShader = null
        updateDrawnVersion()
        batch = ModelBatch(FogShaderProvider { fogCoefficient })
        contentShaders = DefaultShaderProvider(net.nevinsky.abyssus.lib.core.shader.ShaderConfig().apply {
            numSpotLights = MAX_POINT
        }).also { contentBatch = ContentBatch(it) }
        terrainShader = TerrainShader(shaders)
        skybox = SceneSkybox(AssetView(assets, net.nevinsky.abyssus.lib.core.assets.sky.SkyRenderer::class.java))
        overlay = LoadingOverlay(shaders)
        lineBatch = LineBatch(shaders)
        gridModel = GridModel.build().also { grid = ModelInstance(it) }
    }

    /**
     * Asks for the assets of [revision] to be loaded again. Any thread; nothing is touched until the next [render], which
     * only runs while the canvas can safely draw, so a hidden view applies it when it is shown again. Revisions made
     * meanwhile are merged.
     */
    fun queueAssetRevision(revision: AssetRevisionBatch) {
        pendingRevision.queue(revision)
    }

    /** Applies the queued revision on the GL thread: the unsaved text for later loads, then the changed names reload. */
    private fun applyPendingRevision() {
        val revision = pendingRevision.take() ?: return
        assets.replaceUnsaved(revision.unsaved)
        assets.invalidate(revision.names)
    }

    fun render(width: Int, height: Int, orbit: OrbitCamera, deltaSeconds: Float = 0f) {
        val batch = batch ?: return
        val grid = grid ?: return
        aspectOf(width, height) ?: return
        applyPendingRevision()
        val p = params
        lastWidth = width
        lastHeight = height

        applyEnvironment(p)
        applyCamera(width, height, orbit)
        val c = content
        skyClock.advance(deltaSeconds)
        applyLights(c, orbit)
        applySunScale(c, orbit)
        models.update(c.models, p.projectDir, deltaSeconds)
        terrains.update(c.terrains, p.projectDir)
        updateDrawnVersion()
        skybox?.update(c.skybox, p.projectDir)
        val hdrAmbient = (SceneAmbient.of(c.skybox, p.ambient) { skybox?.environment(it) } as? SceneAmbient.Sky)?.environment?.ambient
        val rayDisplay = rayFrameProvider?.invoke(RayFrameContext(p, c, camera, lights, models.drawn, width, height, state.viewCamera, hdrAmbient) { bakedProceduralSky(c, p) })
            ?.takeIf { compatibleRayDisplay(it, c, width, height) }
        presentedRayFrame = rayDisplay != null
        val atlas = if (rayDisplay == null) shadows?.render(camera, lights, environment, models.drawn, terrains.drawn) else null
        shadowedLightIds = atlas?.records?.mapTo(HashSet()) { it.lightId } ?: emptySet()
        Gdx.gl.glViewport(0, 0, width, height)
        Gdx.gl.glClearColor(p.clear.r, p.clear.g, p.clear.b, p.clear.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        if (rayDisplay == null) {
            skybox?.draw(camera, p.content.skybox, p.projectDir, SunDirection.of(p.content.lights), skyClock.seconds, state.cloudTechnique)
            drawnCloudTechnique = (skybox?.sky(p.content.skybox) as? ProceduralSky)?.drawnTechnique
            batch.begin(camera)
            batch.render(grid, environment)
            batch.end()
            renderContent(p, c, atlas)
            drawOverlays(width, height)
        } else {
            drawnCloudTechnique = null
            val matched = rayDisplay.metadata
            matched.camera.applyTo(rayCamera, width, height)
            val presenter = rayPresenter ?: RayFramePresenter().also { rayPresenter = it }
            presenter.draw(rayDisplay.frame)
            applyEnvironment(p.copy(ambient = matched.ambient, fog = matched.fog))
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
            batch.begin(rayCamera)
            batch.render(grid, environment)
            batch.end()
            drawOverlays(width, height, matched.content, rayCamera)
            applyEnvironment(p)
        }

        // grayed out with a spinner for as long as the scene's assets are on their way
        loading = (skybox?.isLoading ?: false) || models.isLoading || terrains.isLoading
        if (loading) overlay?.draw(width, height, deltaSeconds)
        publishSnapshot()
    }

    /**
     * Sets the frame's camera: from the camera entity [SceneViewState.viewCamera] when the scene has it, else from [orbit]. Needs no GL,
     * so tests can call it directly.
     */
    internal fun updateCamera(width: Int, height: Int, orbit: OrbitCamera) {
        applyCamera(width, height, orbit)
        publishSnapshot()
    }

    private fun applyCamera(width: Int, height: Int, orbit: OrbitCamera) {
        val p = params
        val c = content
        val through = state.viewCamera?.let { id -> c.cameras.firstOrNull { it.entityId == id } }
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        if (through != null) {
            val direction = cameraDirectionOf(through, c.entityPositions)
            camera.fieldOfView = through.fieldOfView.takeIf { it > 0f && it < 180f } ?: CAMERA_FOV
            camera.near = through.near.coerceAtLeast(MIN_NEAR)
            camera.far = through.far.coerceAtLeast(camera.near + MIN_NEAR)
            camera.position.set(through.position.x, through.position.y, through.position.z)
            camera.direction.set(direction.x, direction.y, direction.z)
            camera.up.set(if (kotlin.math.abs(direction.y) > 0.999f) Vector3.Z else Vector3.Y)
            camera.normalizeUp()
        } else {
            val eye = orbit.position()
            camera.fieldOfView = p.camera.fieldOfView
            camera.near = p.camera.near
            camera.far = p.camera.far
            camera.position.set(eye.x, eye.y, eye.z)
            camera.up.set(Vector3.Y)
            camera.lookAt(orbit.target.x, orbit.target.y, orbit.target.z)
        }
        camera.update()
    }

    /**
     * Camera markers, light markers and the overlays' depth-tested pass in the scene, then the selection's highlight,
     * its gizmo and the overlays' second pass on top of everything.
     */
    private fun drawOverlays(width: Int, height: Int, c: SceneContent = content, displayCamera: PerspectiveCamera = camera) {
        val lines = lineBatch ?: return
        drewGizmo = false
        drawnCameraMarkers = c.cameras.count { it.entityId != state.viewCamera }
        val aspect = aspectOf(width, height) ?: return
        lines.begin(displayCamera, depthTest = true)
        SceneMarkers().draw(lines, c, aspect, state.viewCamera)
        overlays?.draw(overlayView(c, displayCamera, height, onTop = false), lines)
        lines.end()
        lines.begin(displayCamera, depthTest = false)
        state.selectedId?.let { id ->
            boundsOf(c, id)?.let { SelectionBox.draw(lines, it) }
            gizmoHandlesFor(c, displayCamera, state, height)?.let {
                GizmoDraw.draw(lines, it, state.hoveredAxis)
                drewGizmo = true
            }
        }
        overlays?.draw(overlayView(c, displayCamera, height, onTop = true), lines)
        lines.end()
    }

    internal fun overlayView(c: SceneContent, displayCamera: PerspectiveCamera, height: Int, onTop: Boolean) = OverlayView(
        c, params.ecs, params.projectDir, state.selectedId, displayCamera, height, state.poses.isNotEmpty() || !state.gizmosEnabled, onTop,
    )

    /** The world bounds of the entity [id] as the last frame drew it. */
    internal fun boundsOf(c: SceneContent, id: String): BoundingBox? {
        models.drawn.firstOrNull { it.placement.entityId == id }?.let {
            val transform = c.models.firstOrNull { model -> model.entityId == id }?.transform?.toMatrix() ?: it.instance.transform
            return BoundingBox(it.localBounds).mul(transform)
        }
        terrains.drawn.firstOrNull { it.placement.entityId == id }?.let {
            return BoundingBox(it.localBounds).mul(c.terrains.firstOrNull { terrain -> terrain.entityId == id }?.transform?.toMatrix() ?: it.world)
        }
        return SceneMarkers().boundsOf(c, id)
    }

    private fun renderContent(p: SceneRenderParams, c: SceneContent, atlas: ShadowAtlasAttribute?) {
        val contentBatch = contentBatch ?: return
        // after the grid: a built HDR sky replaces the ambient color for the content only (applyEnvironment undoes it)
        val ambient = SceneAmbient.of(c.skybox, p.ambient) { skybox?.environment(it) }
        val sky = (ambient as? SceneAmbient.Sky)?.environment
        if (sky != null) {
            environment.remove(ColorAttribute.AmbientLight)
            environment.set(EnvironmentLightAttribute(sky.specular, sky.irradiance, sky.levels, sky.ambient))
        }
        if (atlas != null) environment.set(atlas)
        terrainShader?.draw(camera, terrains.drawn, p.ambient, p.fog, frameLights, sky?.irradiance, atlas)
        contentBatch.begin(camera)
        for (entity in models.drawn) contentBatch.render(entity.instance, environment, ShaderProvider.DEFAULT_SHADER_KEY)
        contentBatch.end()
    }

    /** Recomputed only when the scene's lights or the orbit target (which decides the nearest point lights) change. */
    private fun applyLights(c: SceneContent, orbit: OrbitCamera) {
        val target = Vec3(orbit.target.x, orbit.target.y, orbit.target.z)
        val key = c.lights to target
        if (key == lightsKey) return
        lightsKey = key
        lights = lightSetOf(c.lights, target)
    }

    /**
     * Dims the sun light by the clouds of the scene's procedural sky between the orbit target and the sun, every frame
     * (the clouds drift), and applies the lights to the environment when they changed. Other lights are untouched.
     */
    private fun applySunScale(c: SceneContent, orbit: OrbitCamera) {
        val sky = skybox?.sky(c.skybox) as? ProceduralSky
        val sun = SunDirection.of(c.lights)
        val scale = sunOcclusion.update(
            sky?.clouds, Vector3(sun.x, sun.y, sun.z), orbit.target.x, orbit.target.z,
            sky?.params?.planetRadius ?: 0f, SKY_CAMERA_HEIGHT, skyClock.seconds, skyClock.lastStep,
        )
        val sunId = SunDirection.sunLight(c.lights)?.entityId
        val key = Triple(lights, sunId, scale)
        if (key == frameLightsKey) return
        frameLightsKey = key
        frameLights = lights.withSunScale(sunId, scale)
        frameLights.applyTo(environment)
    }

    /** Fog color comes from the environment; density through [net.nevinsky.abyssus.plugin.sceneview.fog.FogShader] (see [FogParams] for what cannot be matched). */
    private fun applyEnvironment(p: SceneRenderParams) {
        environment.remove(ShadowAtlasAttribute.Type)
        environment.remove(EnvironmentLightAttribute.Type)
        val ambient = p.ambient
        if (ambient != null) {
            environment.set(ColorAttribute(ColorAttribute.AmbientLight, ambient.r, ambient.g, ambient.b, 1f))
        } else {
            environment.remove(ColorAttribute.AmbientLight)
        }
        val fog = p.fog
        if (fog != null) {
            environment.set(ColorAttribute(ColorAttribute.Fog, fog.color.r, fog.color.g, fog.color.b, 1f))
            fogCoefficient = fog.shaderCoefficient
        } else {
            environment.remove(ColorAttribute.Fog)
            fogCoefficient = null
        }
    }

    /** No GL calls: used immediately when a hidden canvas loses its context, including final editor disposal. */
    internal fun abandonShadows() {
        rayPresenter = null
        bakedSky = null
        presentedRayFrame = false
        shadows?.abandon()
        shadows = null
        shadowedLightIds = emptySet()
        environment.remove(ShadowAtlasAttribute.Type)
    }

    override fun dispose() {
        snapshot = null
        posed = null
        rayPresenter?.dispose()
        rayPresenter = null
        bakedSky = null
        presentedRayFrame = false
        shadows?.dispose()
        shadows = null
        shadowedLightIds = emptySet()
        environment.remove(ShadowAtlasAttribute.Type)
        models.dispose()
        terrains.dispose()
        updateDrawnVersion()
        terrainShader?.dispose()
        terrainShader = null
        skybox?.dispose()
        skybox = null
        lineBatch?.dispose()
        lineBatch = null
        overlay?.dispose()
        overlay = null
        contentShaders?.dispose()
        contentShaders = null
        contentBatch = null
        batch?.dispose()
        gridModel?.dispose()
        batch = null
        gridModel = null
        grid = null
    }

    private companion object {
        const val MIN_NEAR = 0.01f
    }

    private class BakedSky(val key: Triple<String, File?, Vec3>, val snapshot: net.nevinsky.abyssus.lib.core.assets.sky.RaySkySnapshot?)
    private var bakedSky: BakedSky? = null
    private val skyBaker = RaySkyBaker()

    /** The procedural sky rendered into a ray texture once per sky and sun direction; null while it loads or fails to bake. */
    private fun bakedProceduralSky(c: SceneContent, p: SceneRenderParams): net.nevinsky.abyssus.lib.core.assets.sky.RaySkySnapshot? {
        val name = c.skybox ?: return null
        val sky = skybox?.sky(name) as? net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSky ?: return null
        val sun = SunDirection.of(c.lights)
        val key = Triple(name, p.projectDir, sun)
        bakedSky?.takeIf { it.key == key }?.let { return it.snapshot }
        val snapshot = try { skyBaker.bake(sky, sun) } catch (failure: Exception) { null }
        bakedSky = BakedSky(key, snapshot)
        return snapshot
    }

    private fun compatibleRayDisplay(display: RaySceneDisplay, current: SceneContent, width: Int, height: Int): Boolean {
        val metadata = display.metadata
        fun keys(placements: List<AssetPlacement>) = placements.map { it.entityId to it.assetName }.toSet()
        return metadata.width == width && metadata.height == height && metadata.viewCamera == state.viewCamera &&
            metadata.projectDir == params.projectDir && keys(metadata.content.models) == keys(current.models) &&
            keys(metadata.content.terrains) == keys(current.terrains)
    }
}
