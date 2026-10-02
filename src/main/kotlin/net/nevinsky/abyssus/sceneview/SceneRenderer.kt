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
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.AppExecutorUtil
import net.nevinsky.abyssus.core.model.Model as ContentModel
import net.nevinsky.abyssus.core.shader.DefaultShaderProvider
import net.nevinsky.abyssus.core.shader.ShaderProvider
import java.util.concurrent.Executor
import net.nevinsky.abyssus.core.ModelBatch as ContentBatch

/** How the scene view turns asset folders into GPU objects; replaceable in tests. */
class SceneLoaders(
    val models: AssetLoader<PreparedModel, ContentModel> = ModelLoader(),
    val terrains: AssetLoader<PreparedTerrain, TerrainMesh> = TerrainLoader(),
    val skyboxes: AssetLoader<PreparedSkybox, SkyboxCube> = SkyboxLoader(),
)

/**
 * Draws a scene's environment, a ground grid and the content the scene places (skybox, terrains, models), and picks
 * entities under the cursor. Assets are prepared on [executor] by [loaders]. Call only inside [GdxRuntime.withContext]
 * with the GL context current, except [pick].
 */
class SceneRenderer(
    private val executor: Executor = AppExecutorUtil.getAppExecutorService(),
    private val loaders: SceneLoaders = SceneLoaders(),
) : Disposable {
    @Volatile
    var params: SceneRenderParams = SceneRenderParams.DEFAULT

    private var batch: ModelBatch? = null
    private var contentBatch: ContentBatch? = null
    private var contentShaders: DefaultShaderProvider? = null
    private val models = SceneModels(executor, loaders.models)
    private val terrains = SceneTerrains(executor, loaders.terrains)
    private var terrainShader: TerrainShader? = null
    private var skybox: SceneSkybox? = null
    private var overlay: LoadingOverlay? = null
    private var lightsKey: Pair<List<LightPlacement>, Vec3>? = null
    private var lights = LightSet.NONE
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

    /**
     * The entity under the pixel ([screenX], [screenY]) of a [width] x [height] view, as of the last rendered frame;
     * null when there is none. Uses CPU-side data only, so it needs no GL context.
     */
    fun pick(screenX: Int, screenY: Int, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val ray = ScenePicker.pickRay(camera, screenX, screenY, width, height)
        val boxes = models.drawn.map { e ->
            // the model's bounds moved into the world
            BoxTarget(e.placement.entityId, BoundingBox(e.localBounds).mul(e.instance.transform))
        }
        val grounds = terrains.drawn.map { TerrainTarget(it.placement.entityId, it.terrain.data, it.world) }
        return ScenePicker.pick(ray, boxes, grounds, camera.far)
    }

    /**
     * Starts with a clean slate. When the previous GL context was abandoned instead of released (the view was hidden),
     * the models and terrains cached from it are invalid in the new context and must be loaded again.
     */
    fun create() {
        models.abandon()
        terrains.abandon()
        batch = ModelBatch(FogShaderProvider { fogCoefficient })
        contentShaders = DefaultShaderProvider().also { contentBatch = ContentBatch(it) }
        terrainShader = TerrainShader()
        skybox = SceneSkybox(executor, loaders.skyboxes)
        overlay = LoadingOverlay()
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

        val eye = orbit.position()
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.fieldOfView = p.camera.fieldOfView
        camera.near = p.camera.near
        camera.far = p.camera.far
        camera.position.set(eye.x, eye.y, eye.z)
        camera.up.set(Vector3.Y)
        camera.lookAt(orbit.target.x, orbit.target.y, orbit.target.z)
        camera.update()

        skybox?.draw(camera, p.content.skybox, p.projectDir)

        batch.begin(camera)
        batch.render(grid, environment)
        batch.end()

        renderContent(p, orbit, deltaSeconds)

        // grayed out with a spinner for as long as the scene's assets are on their way
        loading = (skybox?.isLoading ?: false) || models.isLoading || terrains.isLoading
        if (loading) overlay?.draw(width, height, deltaSeconds)
    }

    private fun renderContent(p: SceneRenderParams, orbit: OrbitCamera, deltaSeconds: Float) {
        val contentBatch = contentBatch ?: return
        applyLights(p, orbit)
        terrains.update(p.content.terrains, p.projectDir)
        terrainShader?.draw(camera, terrains.drawn, p.ambient, p.fog, lights)
        models.update(p.content.models, p.projectDir, deltaSeconds)
        contentBatch.begin(camera)
        for (entity in models.drawn) contentBatch.render(entity.instance, environment, ShaderProvider.DEFAULT_SHADER_KEY)
        contentBatch.end()
    }

    /** Recomputed only when the scene's lights or the orbit target (which decides the nearest point lights) change. */
    private fun applyLights(p: SceneRenderParams, orbit: OrbitCamera) {
        val target = Vec3(orbit.target.x, orbit.target.y, orbit.target.z)
        val key = p.content.lights to target
        if (key == lightsKey) return
        lightsKey = key
        lights = LightSet.of(p.content.lights, target)
        lights.applyTo(environment)
    }

    /** Fog color comes from the environment; density through [FogShader] (see [FogParams] for what cannot be matched). */
    private fun applyEnvironment(p: SceneRenderParams) {
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
        terrainShader?.dispose()
        terrainShader = null
        skybox?.dispose()
        skybox = null
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
    }
}
