/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.assets.AssetLoading
import java.io.File
import net.nevinsky.abyssus.assets.ShaderSource
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.intellij.openapi.Disposable
import net.nevinsky.abyssus.core.model.Model as ContentModel
import net.nevinsky.abyssus.core.shader.DefaultShaderProvider
import net.nevinsky.abyssus.core.shader.EnvironmentLightAttribute
import net.nevinsky.abyssus.core.shader.ShaderProvider
import net.nevinsky.abyssus.sceneview.fog.FogShaderProvider
import net.nevinsky.abyssus.sceneview.gizmo.DragResult
import net.nevinsky.abyssus.sceneview.gizmo.GizmoAxis
import net.nevinsky.abyssus.sceneview.gizmo.GizmoDrag
import net.nevinsky.abyssus.sceneview.gizmo.GizmoDraw
import net.nevinsky.abyssus.sceneview.gizmo.GizmoHandles
import net.nevinsky.abyssus.sceneview.gizmo.GizmoHit
import net.nevinsky.abyssus.sceneview.gizmo.GizmoMode
import net.nevinsky.abyssus.sceneview.gizmo.canRotate
import net.nevinsky.abyssus.sceneview.skybox.SunDirection
import net.nevinsky.abyssus.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.sceneview.terrain.TerrainShader
import net.nevinsky.abyssus.core.ModelBatch as ContentBatch

/**
 * Draws a scene's environment, a ground grid and the content the scene places (skybox, terrains, models), and picks
 * entities under the cursor. Assets come from [assetLoading]; the grid, overlay and terrain programs from [shaders]. Call only inside [GdxRuntime.withContext]
 * with the GL context current; picking, ground queries and interaction geometry use CPU data only.
 */
class SceneRenderer(
    private val assetLoading: AssetLoading,
    private val shaders: ShaderSource,
) : Disposable {
    @Volatile
    var params: SceneRenderParams = SceneRenderParams.DEFAULT

    /** The entity id the view highlights and shows a gizmo on, or null. */
    @Volatile
    var selectedId: String? = null

    @Volatile
    var gizmoMode: GizmoMode = GizmoMode.MOVE

    /** The gizmo handle under the cursor, drawn brighter. */
    @Volatile
    var hoveredAxis: GizmoAxis? = null

    /** The camera entity the viewport renders from instead of the orbit view, or null. */
    @Volatile
    var viewCamera: String? = null

    /** Transforms shown over the scene's own while a gizmo drag or completed drop is previewed (entity id to where it is now). */
    @Volatile
    var preview: Map<String, DragResult> = emptyMap()

    /** The scene's content with [preview] applied. */
    internal val content: SceneContent
        get() {
            val c = params.content
            val p = preview
            return if (p.isEmpty()) c else ScenePreview.apply(c, p)
        }

    private var batch: ModelBatch? = null
    private var contentBatch: ContentBatch? = null
    private var contentShaders: DefaultShaderProvider? = null
    private val models = SceneModels(assetLoading.assets(assetLoading.models))
    private val terrains = SceneTerrains(assetLoading.assets(assetLoading.terrains))
    private var terrainShader: TerrainShader? = null
    private var skybox: SceneSkybox? = null
    private var overlay: LoadingOverlay? = null
    private var lightsKey: Pair<List<LightPlacement>, Vec3>? = null
    private var lights = LightSet.NONE
    private var lineBatch: LineBatch? = null
    private var gridModel: Model? = null
    private var grid: ModelInstance? = null
    private val camera = PerspectiveCamera()
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

    /**
     * The entity under the pixel ([screenX], [screenY]) of a [width] x [height] view, as of the last rendered frame;
     * null when there is none. Uses CPU-side data only, so it needs no GL context.
     */
    fun pick(screenX: Int, screenY: Int, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val ray = ScenePicker.pickRay(camera, screenX, screenY, width, height)
        val targets = targets()
        return ScenePicker.pick(ray, targets.boxes.map { BoxTarget(it.id, BoundingBox(it.local).mul(it.world)) }, targets.terrains, camera.far)
    }

    private class DrawnBox(val id: String, val local: BoundingBox, val world: Matrix4) {
        fun oriented() = OrientedBox(local, world)
    }
    private class Targets(val boxes: List<DrawnBox>, val terrains: List<TerrainTarget>)

    /** Shared input list: picking derives axis-aligned boxes, Drop retains the actual rotated corners. */
    private fun targets(): Targets {
        val boxes = models.drawn.map { e ->
            val world = preview[e.placement.entityId]?.transform?.toMatrix() ?: e.instance.transform!!
            DrawnBox(e.placement.entityId, e.localBounds, world)
        } + SceneMarkers.targets(content, viewCamera).map { DrawnBox(it.entityId, it.bounds, Matrix4()) }
        return Targets(boxes, terrains.drawn.map { TerrainTarget(it.placement.entityId, it.terrain.data, it.world) })
    }

    /** CPU-only query over the last frame's loaded geometry; terrains and the looked-through camera cannot drop. */
    fun groundBelow(entityId: String): Float? {
        if (entityId == viewCamera || content.terrains.any { it.entityId == entityId }) return null
        val targets = targets()
        val footprint = targets.boxes.firstOrNull { it.id == entityId }?.oriented() ?: return null
        return ScenePicker.restHeight(footprint, targets.boxes.filter { it.id != entityId }.map { it.oriented() }, targets.terrains)
    }

    internal fun lowestPoint(entityId: String): Float? = targets().boxes.firstOrNull { it.id == entityId }?.oriented()?.bottom

    /** Changes only when a model or terrain enters or leaves the drawn lists, including context replacement. */
    var drawnVersion: Long = 0
        private set
    private var drawnIds: Pair<Set<String>, Set<String>> = emptySet<String>() to emptySet()

    private fun updateDrawnVersion() {
        val fresh = models.drawn.mapTo(HashSet()) { it.placement.entityId } to terrains.drawn.mapTo(HashSet()) { it.placement.entityId }
        if (fresh != drawnIds) { drawnIds = fresh; drawnVersion++ }
    }

    /** The ray through the pixel ([screenX], [screenY]) of a [width] x [height] view, as of the last rendered frame. */
    fun rayAt(screenX: Int, screenY: Int, width: Int, height: Int): Ray? =
        if (width <= 0 || height <= 0) null else ScenePicker.pickRay(camera, screenX, screenY, width, height)

    /** The gizmo of the selected entity for a view [height] pixels tall, or null when nothing is selected or it has no handles. */
    internal fun gizmoHandles(height: Int): GizmoHandles? {
        val id = selectedId ?: return null
        val c = content
        if (gizmoMode == GizmoMode.ROTATE && !canRotate(c, id)) return null
        val selected = ScenePreview.selected(c, id) ?: return null
        val eye = Vec3(camera.position.x, camera.position.y, camera.position.z)
        return GizmoHandles.of(selected.transform.position, gizmoMode, eye, camera.fieldOfView, height)
    }

    /** The handle of the selected entity's gizmo under the pixel, or null. Uses CPU-side data only. */
    fun gizmoHit(screenX: Int, screenY: Int, width: Int, height: Int): GizmoAxis? {
        val handles = gizmoHandles(height) ?: return null
        val ray = rayAt(screenX, screenY, width, height) ?: return null
        return GizmoHit.find(ray, handles)
    }

    /** A drag of the [axis] handle of the selected entity's gizmo, started at the pixel; null when it cannot start. */
    fun beginDrag(axis: GizmoAxis, screenX: Int, screenY: Int, width: Int, height: Int): GizmoDrag? {
        val id = selectedId ?: return null
        val selected = ScenePreview.selected(content, id) ?: return null
        val ray = rayAt(screenX, screenY, width, height) ?: return null
        return GizmoDrag(gizmoMode, axis, selected.transform, ray, selected.direction).takeIf { it.isUsable }
    }

    /** The camera as of the last frame (for tests). */
    internal val frameCamera: PerspectiveCamera get() = camera

    /**
     * Starts with a clean slate. When the previous GL context was abandoned instead of released (the view was hidden),
     * the models and terrains cached from it are invalid in the new context and must be loaded again.
     */
    fun create() {
        models.abandon()
        terrains.abandon()
        updateDrawnVersion()
        batch = ModelBatch(FogShaderProvider { fogCoefficient })
        contentShaders = DefaultShaderProvider().also { contentBatch = ContentBatch(it) }
        terrainShader = TerrainShader(shaders)
        skybox = SceneSkybox(assetLoading.assets(assetLoading.skies))
        overlay = LoadingOverlay(shaders)
        lineBatch = LineBatch(shaders)
        gridModel = buildGrid().also { grid = ModelInstance(it) }
    }

    fun render(width: Int, height: Int, orbit: OrbitCamera, deltaSeconds: Float = 0f) {
        val batch = batch ?: return
        val grid = grid ?: return
        aspectOf(width, height) ?: return
        val p = params
        lastWidth = width
        lastHeight = height

        applyEnvironment(p)
        Gdx.gl.glViewport(0, 0, width, height)
        Gdx.gl.glClearColor(p.clear.r, p.clear.g, p.clear.b, p.clear.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        updateCamera(width, height, orbit)

        skybox?.draw(camera, p.content.skybox, p.projectDir, SunDirection.of(p.content.lights))

        batch.begin(camera)
        batch.render(grid, environment)
        batch.end()

        renderContent(p, orbit, deltaSeconds)
        drawOverlays(width, height)

        // grayed out with a spinner for as long as the scene's assets are on their way
        loading = (skybox?.isLoading ?: false) || models.isLoading || terrains.isLoading
        if (loading) overlay?.draw(width, height, deltaSeconds)
    }

    /**
     * Sets the frame's camera: from the camera entity [viewCamera] when the scene has it, else from [orbit]. Needs no GL,
     * so tests can call it directly.
     */
    internal fun updateCamera(width: Int, height: Int, orbit: OrbitCamera) {
        val p = params
        val c = content
        val through = viewCamera?.let { id -> c.cameras.firstOrNull { it.entityId == id } }
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        if (through != null) {
            val direction = CameraFrustum.directionOf(through, c.entityPositions)
            camera.fieldOfView = through.fieldOfView.takeIf { it > 0f && it < 180f } ?: DEFAULT_CAMERA_FOV
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

    /** Camera markers and light markers in the scene, then the selection's highlight and gizmo on top of everything. */
    private fun drawOverlays(width: Int, height: Int) {
        val lines = lineBatch ?: return
        val c = content
        drewGizmo = false
        drawnCameraMarkers = c.cameras.count { it.entityId != viewCamera }
        lines.begin(camera, depthTest = true)
        SceneMarkers.draw(lines, c, aspectOf(width, height) ?: return, viewCamera)
        lines.end()
        val id = selectedId ?: return
        lines.begin(camera, depthTest = false)
        boundsOf(c, id)?.let { drawBox(lines, it) }
        gizmoHandles(height)?.let {
            GizmoDraw.draw(lines, it, hoveredAxis)
            drewGizmo = true
        }
        lines.end()
    }

    /** The world bounds of the entity [id] as the last frame drew it. */
    internal fun boundsOf(c: SceneContent, id: String): BoundingBox? {
        models.drawn.firstOrNull { it.placement.entityId == id }?.let { return BoundingBox(it.localBounds).mul(it.instance.transform) }
        terrains.drawn.firstOrNull { it.placement.entityId == id }?.let {
            val data = it.terrain.data
            val local = BoundingBox(
                Vector3(0f, data.heights.min(), 0f),
                Vector3(data.size.toFloat(), data.heights.max(), data.size.toFloat()),
            )
            return local.mul(it.world)
        }
        return SceneMarkers.boundsOf(c, id)
    }

    private fun drawBox(out: LineSink, box: BoundingBox) {
        val a = box.min
        val b = box.max
        val p = { x: Float, y: Float, z: Float -> Vec3(x, y, z) }
        val corners = listOf(
            p(a.x, a.y, a.z), p(b.x, a.y, a.z), p(b.x, a.y, b.z), p(a.x, a.y, b.z),
            p(a.x, b.y, a.z), p(b.x, b.y, a.z), p(b.x, b.y, b.z), p(a.x, b.y, b.z),
        )
        for (ring in 0..1) for (i in 0 until 4) out.line(corners[ring * 4 + i], corners[ring * 4 + (i + 1) % 4], HIGHLIGHT)
        for (i in 0 until 4) out.line(corners[i], corners[4 + i], HIGHLIGHT)
    }

    private fun renderContent(p: SceneRenderParams, orbit: OrbitCamera, deltaSeconds: Float) {
        val contentBatch = contentBatch ?: return
        val c = content
        applyLights(c, orbit)
        // after the grid: a built HDR sky replaces the ambient color for the content only (applyEnvironment undoes it)
        val ambient = SceneAmbient.of(c.skybox, p.ambient) { skybox?.environment(it) }
        val sky = (ambient as? SceneAmbient.Sky)?.environment
        if (sky != null) {
            environment.remove(ColorAttribute.AmbientLight)
            environment.set(EnvironmentLightAttribute(sky.specular, sky.irradiance, sky.levels, sky.ambient))
        }
        terrains.update(c.terrains, p.projectDir)
        terrainShader?.draw(camera, terrains.drawn, p.ambient, p.fog, lights, sky?.irradiance)
        models.update(c.models, p.projectDir, deltaSeconds)
        updateDrawnVersion()
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
        lights = LightSet.of(c.lights, target)
        lights.applyTo(environment)
    }

    /** Fog color comes from the environment; density through [net.nevinsky.abyssus.sceneview.fog.FogShader] (see [FogParams] for what cannot be matched). */
    private fun applyEnvironment(p: SceneRenderParams) {
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

    /** Lines need normals for the default shader to apply ambient light; emissive keeps the grid visible without it. */
    private fun buildGrid(): Model {
        val builder = ModelBuilder()
        builder.begin()
        val material = Material(ColorAttribute.createDiffuse(Color.WHITE), ColorAttribute.createEmissive(0.25f, 0.25f, 0.25f, 1f))
        val part = builder.part("grid", GL20.GL_LINES, (Usage.Position or Usage.Normal).toLong(), material)
        val info = VertexInfo()
        val n = GRID_HALF_EXTENT.toFloat()
        for (i in -GRID_HALF_EXTENT..GRID_HALF_EXTENT) {
            val f = i.toFloat()
            val a = part.vertex(info.set(Vector3(f, 0f, -n), Vector3.Y, null, null))
            val b = part.vertex(info.set(Vector3(f, 0f, n), Vector3.Y, null, null))
            part.line(a, b)
            val c = part.vertex(info.set(Vector3(-n, 0f, f), Vector3.Y, null, null))
            val d = part.vertex(info.set(Vector3(n, 0f, f), Vector3.Y, null, null))
            part.line(c, d)
        }
        return builder.end()
    }

    override fun dispose() {
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
        const val GRID_HALF_EXTENT = 50
        const val MIN_NEAR = 0.01f
        val HIGHLIGHT = Rgba(1f, 0.72f, 0.15f, 1f)
    }
}
