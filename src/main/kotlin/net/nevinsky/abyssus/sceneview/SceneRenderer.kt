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
import com.intellij.openapi.Disposable

/** Draws a scene's environment and a ground grid. Call only inside [GdxRuntime.withContext] with the GL context current. */
class SceneRenderer : Disposable {
    @Volatile
    var params: SceneRenderParams = SceneRenderParams.DEFAULT

    private var batch: ModelBatch? = null
    private var gridModel: Model? = null
    private var grid: ModelInstance? = null
    private val camera = PerspectiveCamera()
    private val environment = Environment()

    @Volatile
    private var fogCoefficient: Float? = null

    fun create() {
        batch = ModelBatch(FogShaderProvider { fogCoefficient })
        gridModel = buildGrid().also { grid = ModelInstance(it) }
    }

    fun render(width: Int, height: Int, orbit: OrbitCamera) {
        val batch = batch ?: return
        val grid = grid ?: return
        aspectOf(width, height) ?: return
        val p = params

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

        batch.begin(camera)
        batch.render(grid, environment)
        batch.end()
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
