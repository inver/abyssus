/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.intellij.openapi.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSky
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_FOV
import net.nevinsky.abyssus.lib.core.editor.arrange.ArrangementPreview
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight
import net.nevinsky.abyssus.lib.core.editor.arrange.LineHeights
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementAlong
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.Pose
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import net.nevinsky.abyssus.lib.core.editor.pick.*
import net.nevinsky.abyssus.lib.core.editor.ray.RayFrameContext
import net.nevinsky.abyssus.lib.core.editor.ray.RaySceneDisplay
import net.nevinsky.abyssus.lib.core.editor.scene.*
import net.nevinsky.abyssus.lib.core.editor.water.InvalidWater
import net.nevinsky.abyssus.lib.core.editor.water.ValidWater
import net.nevinsky.abyssus.lib.core.editor.water.WaterPreview
import net.nevinsky.abyssus.lib.core.water.WaterPassPlan
import net.nevinsky.abyssus.lib.gdx.shader.DirectionalVisibilityAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.lib.render.shadow.cameraUp
import net.nevinsky.abyssus.plugin.foliage.FoliageDrafts
import net.nevinsky.abyssus.plugin.sceneview.fog.FogShaderProvider
import net.nevinsky.abyssus.plugin.sceneview.gizmo.GizmoDraw
import net.nevinsky.abyssus.plugin.sceneview.shadows.SceneShadows
import net.nevinsky.abyssus.plugin.sceneview.skybox.SunDirection
import net.nevinsky.abyssus.plugin.sceneview.skybox.blendAxisColors
import net.nevinsky.abyssus.plugin.sceneview.water.SceneWater
import net.nevinsky.abyssus.plugin.sceneview.water.WaterFrameInput
import org.lwjgl.opengl.GL11
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger

/**
 * Draws a scene's environment, a ground grid and the content the scene places (skybox, terrains, models), and picks
 * entities under the cursor. Assets come from [assets]; the grid, overlay and terrain programs from [shaders]. Call only inside [GdxRuntime.withContext]
 * with the GL context current; picking, ground queries and interaction geometry use CPU data only.
 */
class SceneRenderer(
    val assets: ViewAssets,
    private val shaders: ShaderStorage,
    /** The project's uncommitted foliage drafts, shared with the properties panel and the brush (decision 7). */
    private val foliageDrafts: FoliageDrafts,
    /** What the user selected, previews and looks through; the view panel changes it, this draws from it. */
    val state: SceneViewState = SceneViewState(),
    /** Where problems that leave the view working (for example, no cloud shadow map) are logged, once each. */
    private val log: Logger = NOPLogger.NOP_LOGGER,
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
            val moved = if (p.isEmpty()) c else ScenePreview().apply(c, p)
            val water = state.waterPreview
            val withWater = if (water.isEmpty()) moved else moved.copy(water = WaterPreview().apply(moved.water, water))
            return withDraftCopies(withWater)
        }

    private var draftOf: PathEdit? = null
    private var draftBase: SceneContent? = null
    private var draftContent: SceneContent? = null

    /** [c] with the copies of the arrangement being edited laid out along its unapplied points. */
    private fun withDraftCopies(c: SceneContent): SceneContent {
        val draft = state.pathDraft ?: return c
        if (draft === draftOf && c === draftBase) draftContent?.let { return it }
        return ArrangementPreview().apply(c, draft, LineHeights()).also {
            draftOf = draft
            draftBase = c
            draftContent = it
        }
    }

    /** Other plugins' overlays for this view; drawn after the markers, then again over everything. */
    internal var overlays: SceneOverlayHost? = null

    private val contentPass = SceneContentPass(assets, shaders, foliageDrafts)
    private val models get() = contentPass.models
    private val terrains get() = contentPass.terrains
    private val foliage get() = contentPass.foliage
    private val environment get() = contentPass.environment
    private var batch: ModelBatch? = null

    /** Asset changes waiting for a frame that can safely replace GL resources; merged until [render] takes them. */
    private val pendingRevision = PendingAssetRevision()

    /** The GL objects of the current context that only this renderer owns; abandoned or released as a whole (see [RenderResources]). */
    private val resources = RenderResources()
    private var shadows: SceneShadows? = null
    private var water: SceneWater? = null
    private val loggedInvalidWater = HashSet<String>()
    internal var shadowedLightIds: Set<String> = emptySet()
        private set
    private val skyPass = SceneSkyPass(assets, shaders, state, environment, log)
    private val skybox get() = skyPass.skybox
    private val lights get() = skyPass.lights
    private val frameLights get() = skyPass.frameLights
    private val sunVisibility get() = skyPass.sunVisibility
    internal val skyClock get() = skyPass.skyClock
    internal val cloudShadowsActive get() = skyPass.cloudShadowsActive
    internal val sunScale get() = skyPass.sunScale
    internal val litLights get() = skyPass.litLights
    internal val skyLeaseActive get() = skyPass.skyLeaseActive
    internal var drawnCloudTechnique: CloudTechnique? = null
        private set

    internal fun cloudsEnabled(p: SceneRenderParams = params) = skyPass.cloudsEnabled(p)
    private var overlay: LoadingOverlay? = null
    private var corners: CornerOverlay? = null

    /** The Swing-painted images drawn in the corners of the view after everything else; set on the AWT thread before a frame. */
    @Volatile
    internal var cornerSprites: List<CornerSprite> = emptyList()

    /**
     * What this view draws: the Shadows, Fog and Grid toggles and the shading. Per view and never saved; set on the AWT thread
     * and read on the next frame. Leaving Unlit makes the lights be applied to the environment again.
     */
    var display: ViewDisplay = ViewDisplay()
        set(value) {
            if (field.shading != value.shading) {
                skyPass.invalidateLighting()
            }
            field = value
        }

    private val unlit: Boolean get() = display.shading == Shading.UNLIT

    private var lineBatch: LineBatch? = null
    internal var brushCursor: FoliageBrushCursor? = null
    private var gridModel: Model? = null
    private var grid: ModelInstance? = null
    private val camera = PerspectiveCamera()
    private val rayCamera = PerspectiveCamera()
    private var rayPresenter: RayFramePresenter? = null

    /** Invoked after current preview/camera/animation updates, in the safe canvas context. */
    internal var rayFrameProvider: ((RayFrameContext) -> RaySceneDisplay?)? = null
    internal var presentedRayFrame = false
        private set


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
        snapshot = contentPass.snapshot(camera, drawnVersion)
    }

    /** Changes only when a model or terrain enters or leaves the drawn lists, including context replacement. */
    var drawnVersion: Long = 0
        private set
    private var drawnIds: Pair<Set<String>, Set<String>> = emptySet<String>() to emptySet()
    private var drawnAssets: List<Pair<String, Int>> = emptyList()

    private fun updateDrawnVersion() {
        val fresh =
            models.drawn.mapTo(HashSet()) { it.placement.entityId } to terrains.drawn.mapTo(HashSet()) { it.placement.entityId }
        // a replaced asset (new heights under the same entity) changes what the entities are standing on
        val assets = models.drawn.map { it.placement.entityId to System.identityHashCode(it.model) } +
                terrains.drawn.map { it.placement.entityId to System.identityHashCode(it.terrain) }
        if (fresh != drawnIds || assets != drawnAssets) {
            drawnIds = fresh; drawnAssets = assets; drawnVersion++
        }
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
        water?.abandon()
        water = null
        contentPass.abandon()
        snapshot = null
        // the previous context's GL objects went with it: forget them without GL calls, as for models and terrains
        resources.abandonAll()
        overlay = null
        corners = null
        lineBatch = null
        batch = null
        gridModel = null
        grid = null
        updateDrawnVersion()
        batch = resources.own(ModelBatch(FogShaderProvider { contentPass.fogCoefficient }), ModelBatch::dispose).value
        contentPass.create(resources)
        skyPass.create(resources)
        overlay = resources.own(LoadingOverlay(shaders), LoadingOverlay::dispose).value
        corners = resources.own(CornerOverlay(shaders), CornerOverlay::dispose, CornerOverlay::abandon).value
        lineBatch = resources.own(LineBatch(shaders), LineBatch::dispose).value
        gridModel = resources.own(GridModel.build(), Model::dispose).value.also { grid = ModelInstance(it) }
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
        skyPass.applyLights(c, orbit)
        skyPass.applySunScale(c, orbit)
        // Unlit: no lights reach the materials, and applyEnvironment already made the ambient white
        if (unlit) NO_LIGHTS.applyTo(environment)
        models.update(c.models, p.projectDir, deltaSeconds)
        terrains.update(c.terrains, p.projectDir)
        foliage.update(c.foliages, p.projectDir, camera)
        updateDrawnVersion()
        skybox?.update(c.skybox, p.projectDir)
        skybox?.updateLighting(
            c.skybox,
            SunDirection.of(c.lights),
            skyClock.seconds,
            true,
            skyClock.lastStep.toDouble()
        )
        val skyLight =
            (SceneAmbient.of(c.skybox, p.ambient) { skybox?.environment(it) } as? SceneAmbient.Sky)?.environment
        val hdrAmbient = skyLight?.let { current ->
            val fading = skybox?.fadingTo(c.skybox)
            blendAxisColors(current.ambient, fading?.environment?.ambient, fading?.weight ?: 0f)
        }
        val rayDisplay = rayFrameProvider?.invoke(
            RayFrameContext(
                p,
                c,
                camera,
                lights,
                models.drawn,
                width,
                height,
                state.viewCamera,
                hdrAmbient,
                bakedSky = { skyPass.bakedProceduralSky(c, p) },
                sunVisibility = { skyPass.raySunVisibility() },
            )
        )
            ?.takeIf { compatibleRayDisplay(it, c, width, height) }
        presentedRayFrame = rayDisplay != null
        val atlas =
            if (rayDisplay == null && display.drawsShadows) shadows?.render(
                camera,
                lights,
                environment,
                models.drawn,
                terrains.drawn,
                foliage
            ) else null
        shadowedLightIds = atlas?.records?.mapTo(HashSet()) { it.lightId } ?: emptySet()
        // water: planned once per frame; null (the ordinary path) when no valid surface above the camera is in view
        val waterInput = if (rayDisplay == null) waterInputOf(p, c, width, height, deltaSeconds) else null
        val waterPlan = waterInput?.let { water().begin(it) }
        if (waterPlan != null) {
            water().renderReflections(waterPlan, waterInput) { mirrored, clip ->
                drawReflected(
                    p,
                    c,
                    atlas,
                    mirrored,
                    clip
                )
            }
        } else {
            lastWaterPlan = null
        }
        Gdx.gl.glViewport(0, 0, width, height)
        Gdx.gl.glClearColor(p.clear.r, p.clear.g, p.clear.b, p.clear.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        if (rayDisplay == null) {
            wireframePass { skyPass.draw(camera, p) }
            drawnCloudTechnique = (skybox?.sky(p.content.skybox) as? ProceduralSky)?.drawnTechnique
            if (display.grid) {
                batch.begin(camera)
                batch.render(grid, environment)
                batch.end()
            }
            wireframePass {
                renderContent(p, c, atlas, parts = if (waterPlan != null) ContentParts.OPAQUE else ContentParts.ALL)
            }
            if (waterPlan != null) {
                wireframePass {
                    water().drawSurfaces(
                        waterPlan,
                        waterInput!!,
                        skyPass.waterSkyCube(c, water().usesSkyFallback(waterPlan))
                    )
                    renderContent(p, c, atlas, parts = ContentParts.BLENDED)
                }
            }
            lastWaterPlan = waterPlan
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
            if (display.grid) {
                batch.begin(rayCamera)
                batch.render(grid, environment)
                batch.end()
            }
            drawOverlays(width, height, matched.content, rayCamera)
            applyEnvironment(p)
        }

        // grayed out with a spinner for as long as the scene's assets are on their way
        loading = (skybox?.isLoading ?: false) || models.isLoading || terrains.isLoading
        if (loading) overlay?.draw(width, height, deltaSeconds)
        corners?.draw(width, height, cornerSprites)
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
            camera.up.set(cameraUp(Vector3(direction.x, direction.y, direction.z), 0.999f))
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
    private fun drawOverlays(
        width: Int,
        height: Int,
        c: SceneContent = content,
        displayCamera: PerspectiveCamera = camera
    ) {
        val lines = lineBatch ?: return
        drewGizmo = false
        drawnCameraMarkers = c.cameras.count { it.entityId != state.viewCamera }
        val aspect = aspectOf(width, height) ?: return
        lines.begin(displayCamera, depthTest = true)
        SceneMarkers().draw(lines, c, aspect, state.viewCamera)
        drawPaths(lines, c)
        if (state.paint != null) brushCursor?.draw(lines)
        overlays?.draw(overlayView(c, displayCamera, height, onTop = false), lines)
        lines.end()
        lines.begin(displayCamera, depthTest = false)
        state.selectedWater?.let { id ->
            (c.water.firstOrNull { it.id == id } as? ValidWater)?.let { WaterOutline().draw(lines, it.settings) }
            gizmoHandlesFor(c, displayCamera, state, height)?.let {
                GizmoDraw.draw(lines, it, state.hoveredAxis)
                drewGizmo = true
            }
        }
        state.selectedId?.let { id ->
            boundsOf(c, id)?.let { SelectionBox.draw(lines, it) }
            // selecting a path arrangement selects all of it: every linked copy gets its box
            for (copy in c.copiesOf(id)) boundsOf(c, copy)?.let { SelectionBox.draw(lines, it) }
            gizmoHandlesFor(c, displayCamera, state, height)?.let {
                GizmoDraw.draw(lines, it, state.hoveredAxis)
                drewGizmo = true
            }
        }
        overlays?.draw(overlayView(c, displayCamera, height, onTop = true), lines)
        lines.end()
    }

    /** The ground under the last drawn frame, for the preview of path arrangements; the line's own height without terrain. */
    internal fun groundHeights(): GroundHeight? {
        val frame = snapshot ?: return null
        return ground(frame)
    }

    private var groundKey: List<Any>? = null
    private var groundMemo: MemoizedGround? = null

    /** [GroundHeights] over the frame's terrains, with answers kept until a terrain's data or transform changes. */
    private fun ground(frame: FrameSnapshot): GroundHeight {
        val key = frame.terrains.map {
            Triple(
                System.identityHashCode(it.data),
                it.entityId,
                it.world.values.contentHashCode()
            )
        }
        groundMemo?.takeIf { key == groundKey }?.let { return it }
        return MemoizedGround(GroundHeights(frame.terrains)).also {
            groundKey = key
            groundMemo = it
        }
    }

    private class MemoizedGround(private val inner: GroundHeights) : GroundHeight {
        private val answers = HashMap<Long, Float>()

        override fun heightAt(x: Float, z: Float, fallbackY: Float): Float {
            val key = (x.toRawBits().toLong() shl 32) or (z.toRawBits().toLong() and 0xFFFFFFFFL)
            val terrain = answers.getOrPut(key) { inner.terrainHeight(x, z) ?: Float.NaN }
            return if (terrain.isNaN()) fallbackY else terrain
        }
    }

    /**
     * The path being drawn with a handle on each point, and the arrangements of the scene: the selected one with handles
     * (at the points being dragged, when a drag is on), the others as a bare line a click can select.
     */
    private fun drawPaths(lines: LineBatch, c: SceneContent) {
        val frame = snapshot
        val ground = if (frame != null) ground(frame) else LineHeights()
        val markers = SceneMarkers()
        state.drawing?.let { markers.drawPath(lines, arrangementAlong(it.points), ground, handles = true) }
        // a line and its handles show only while its control points are edited
        for (placement in c.arrangements) {
            if (placement.entityId != state.pathEditing) continue
            val edited = (state.pathEdit ?: state.pathDraft)?.takeIf { it.entityId == placement.entityId }
            val path =
                if (edited != null) arrangementAlong(edited.points, placement.arrangement) else placement.arrangement
            markers.drawPath(lines, path, ground, handles = true, slots = true, selectedPoint = state.selectedPathPoint)
        }
    }

    internal fun overlayView(c: SceneContent, displayCamera: PerspectiveCamera, height: Int, onTop: Boolean) =
        OverlayView(
            c,
            params.ecs,
            params.projectDir,
            state.selectedId,
            displayCamera,
            height,
            state.poses.isNotEmpty() || !state.gizmosEnabled,
            onTop,
        )

    /** The world bounds of the entity [id] as the last frame drew it. */
    internal fun boundsOf(c: SceneContent, id: String): BoundingBox? {
        models.drawn.firstOrNull { it.placement.entityId == id }?.let {
            val transform =
                c.models.firstOrNull { model -> model.entityId == id }?.transform?.toMatrix() ?: it.instance.transform
            return BoundingBox(it.localBounds).mul(transform)
        }
        terrains.drawn.firstOrNull { it.placement.entityId == id }?.let {
            return BoundingBox(it.localBounds).mul(
                c.terrains.firstOrNull { terrain -> terrain.entityId == id }?.transform?.toMatrix() ?: it.world
            )
        }
        return SceneMarkers().boundsOf(c, id)
    }

    /**
     * The world box of the entity [id], or of everything the last frame drew when [id] is null; null when there is nothing
     * to frame. Reads CPU data only.
     */
    internal fun framedBounds(id: String?): BoundingBox? {
        if (id != null) return boundsOf(content, id)
        val box = BoundingBox().inf()
        for (entity in models.drawn) box.ext(BoundingBox(entity.localBounds).mul(entity.instance.transform))
        for (entity in terrains.drawn) box.ext(BoundingBox(entity.localBounds).mul(entity.world))
        return box.takeIf { it.isValid }
    }

    private fun water(): SceneWater =
        (water ?: SceneWater(shaders, log).also { water = it }).also { it.frozenSeconds = waterTime }

    /** Stands the water animation at this time (seconds) instead of letting it run; for tests that compare frames. */
    internal var waterTime: Double? = null

    /** The water plan of the last frame, or null when it drew none; for tests. */
    internal var lastWaterPlan: WaterPassPlan? = null
        private set

    /** The water state of this view (null before any water is planned), for tests. */
    internal val waterStatus: SceneWater? get() = water

    /** What the water passes read of this frame; logs each invalid surface once and never draws it. */
    private fun waterInputOf(
        p: SceneRenderParams,
        c: SceneContent,
        width: Int,
        height: Int,
        deltaSeconds: Float
    ): WaterFrameInput? {
        if (c.water.isEmpty()) return null
        for (surface in c.water) if (surface is InvalidWater && loggedInvalidWater.add(surface.id + surface.problems)) {
            log.warn("Water surface '${surface.name}' (${surface.id}) is skipped: ${surface.problems.joinToString("; ")}")
        }
        val surfaces = c.water.filterIsInstance<ValidWater>().map { it.settings }
        if (surfaces.isEmpty()) return null
        return WaterFrameInput(
            camera, width, height, deltaSeconds, surfaces, p.clear,
            if (unlit) UNLIT_AMBIENT else p.ambient, if (display.fog) p.fog else null,
            if (unlit) NO_LIGHTS else frameLights, models.drawn, terrains.drawn, foliage,
        )
    }

    /** The sky and the opaque scene through the mirrored [view], clipped to what is above the water ([clip]). */
    private fun drawReflected(
        p: SceneRenderParams,
        c: SceneContent,
        atlas: ShadowAtlasAttribute?,
        view: Camera,
        clip: FloatArray
    ) {
        wireframePass {
            skyPass.draw(view, p)
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
            renderContent(p, c, atlas, view, clip, ContentParts.OPAQUE)
        }
    }

    /** Runs [block] with `GL_LINE` polygon mode when shading is Wireframe, restoring `GL_FILL` afterwards. */
    private fun wireframePass(block: () -> Unit) {
        val wireframe = display.shading == Shading.WIREFRAME
        if (wireframe) GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
        try {
            block()
        } finally {
            if (wireframe) GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
        }
    }

    private fun applyEnvironment(p: SceneRenderParams) = contentPass.applyEnvironment(p, display)

    private fun renderContent(
        p: SceneRenderParams, c: SceneContent, atlas: ShadowAtlasAttribute?,
        view: Camera = camera, clip: FloatArray? = null, parts: ContentParts = ContentParts.ALL,
    ) = contentPass.render(p, c, atlas, view, clip, parts, display, skybox, frameLights, sunVisibility)

    /** No GL calls: used immediately when a hidden canvas loses its context, including final editor disposal. */
    internal fun abandonShadows() {
        rayPresenter = null
        presentedRayFrame = false
        shadows?.abandon()
        shadows = null
        shadowedLightIds = emptySet()
        environment.remove(ShadowAtlasAttribute.Type)
        environment.remove(DirectionalVisibilityAttribute.Type)
        skyPass.abandon()
    }

    override fun dispose() {
        snapshot = null
        posed = null
        rayPresenter?.dispose()
        rayPresenter = null
        presentedRayFrame = false
        water?.dispose()
        water = null
        shadows?.dispose()
        shadows = null
        shadowedLightIds = emptySet()
        environment.remove(ShadowAtlasAttribute.Type)
        environment.remove(DirectionalVisibilityAttribute.Type)
        skyPass.dispose()
        contentPass.dispose()
        updateDrawnVersion()
        resources.releaseAll()
        lineBatch = null
        overlay = null
        corners = null
        batch = null
        gridModel = null
        grid = null
    }

    private companion object {
        const val MIN_NEAR = 0.01f

        /** Unlit shading lights every material fully by its own colors: a white ambient and no lights. */
        val UNLIT_AMBIENT = net.nevinsky.abyssus.lib.core.editor.content.Rgba(1f, 1f, 1f, 1f)

        /** The outer share of the cloud shadow square over which its shade fades into the uniform sun dimming. */
        const val CLOUD_SHADOW_EDGE_FADE = 0.1f

        /** Texels across the ray tracing copy of the cloud shadow map (512 on the GPU, halved to keep the payload small). */
        const val RAY_SUN_VISIBILITY_SIZE = 256
    }

    private fun compatibleRayDisplay(
        display: RaySceneDisplay,
        current: SceneContent,
        width: Int,
        height: Int
    ): Boolean {
        val metadata = display.metadata
        fun keys(placements: List<AssetPlacement>) = placements.map { it.entityId to it.assetName }.toSet()
        return metadata.width == width && metadata.height == height && metadata.viewCamera == state.viewCamera &&
                metadata.projectDir == params.projectDir && keys(metadata.content.models) == keys(current.models) &&
                keys(metadata.content.terrains) == keys(current.terrains)
    }
}
