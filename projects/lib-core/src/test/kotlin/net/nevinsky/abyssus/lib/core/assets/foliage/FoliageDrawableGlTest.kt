/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.model.ModelMeta
import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.gdx.ModelBatch
import net.nevinsky.abyssus.lib.gdx.Renderable
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.mesh.Mesh
import net.nevinsky.abyssus.lib.gdx.model.Model
import net.nevinsky.abyssus.lib.gdx.shader.DefaultShader
import net.nevinsky.abyssus.lib.gdx.shader.DefaultShaderProvider
import net.nevinsky.abyssus.lib.gdx.shader.ShaderConfig
import net.nevinsky.abyssus.lib.gdx.shader.ShaderProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

private const val CAMERA_MOVE = 0.001f

/**
 * The GL side of design decision 5: the `Foliage` fixture's layers draw their baked copies as instanced parts of the
 * layer models, the instance buffers hold exactly the copies the culling keeps, and they are refilled only when what
 * is drawn changes.
 *
 * The fixture's model folders hold no binaries, so the two models come from `Untitled` (the `tree` layer scatters
 * `model_828d51e4`, whose three meshes give the per-node-part instancing something to cut up). A `Mesh` allocates GL
 * buffers, so this is a GL test: opt-in `-Dabyssus.glTests=true`.
 */
class FoliageDrawableGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val untitled = testProject("Untitled")

    /** The folder names of the `Foliage` fixture's layers, backed by the models of `Untitled`. */
    private val modelFolders = mapOf(
        "tree" to "model_828d51e4-8427-4769-bcb6-13f8f21f23e9",
        "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb" to "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb",
    )

    private fun prepared(): PreparedFoliage {
        val project = testProject("Foliage")
        val files = testFileLoader(project)
        val loader = FoliageLoader(files, testMetaLoader(project), TerrainLoader(files, testMetaLoader(project)))
        return checkNotNull(loader.prepare("foliage_meadow")) { "the Foliage fixture" }.staged
    }

    private fun loadModels(): Map<String, Model> = modelFolders.map { (folder, asset) ->
        val meta = checkNotNull(testMetaLoader(untitled).loadBaseMeta(asset)) { "$asset has a meta" }
        val file = FileHandle(File(untitled, "assets/$asset/${checkNotNull(meta.typedAdditional<ModelMeta>().file)}"))
        folder to Model(AssimpModelLoader().loadData(file), StubTextures())
    }.toMap()

    private fun camera(): PerspectiveCamera = PerspectiveCamera(60f, 4f, 3f).apply { // a 4 by 3 viewport: aspect 4/3
        position.set(10f, 5f, 10f)
        direction.set(1f, 0f, 0f)
        near = 0.1f
        far = 5000f
        update()
    }

    /** The camera the tiniest step further on: the same chunks, but a moved camera for a DETAIL layer. */
    private fun moved(): PerspectiveCamera = camera().apply {
        position.x += CAMERA_MOVE
        update()
    }

    /** The copies the culling and the distance rules keep, computed here rather than through the drawable. */
    private fun expected(
        prepared: PreparedFoliage,
        drawable: FoliageDrawable,
        camera: Camera,
        kind: FoliageLayerKind? = null,
    ): Int = kept(prepared, drawable, camera, kind).size

    /** The copies of one [kind] (of every kind when it is null) the culling and the distance rules keep, with their world positions. */
    private fun kept(
        prepared: PreparedFoliage,
        drawable: FoliageDrawable,
        camera: Camera,
        kind: FoliageLayerKind? = null,
    ): List<Vector3> {
        val bake = checkNotNull(drawable.bake) { "a bake" }
        val ground = checkNotNull(drawable.terrain) { "a terrain" }
        val position = Vector3()
        val kept = ArrayList<Vector3>()
        for (layerMeta in prepared.meta.layers) {
            if (kind != null && layerMeta.layerKind() != kind) continue
            val layerBake = bake.layers.firstOrNull { it.id == layerMeta.id } ?: continue
            val grid = foliageChunkGrid(layerBake, bake.chunkSize, ground, drawable.chunkPad)
            val layerKind = layerMeta.layerKind()
            val visible = visibleFoliageChunks(
                grid, Matrix4(), layerKind, layerMeta.drawDistance, camera, BooleanArray(grid.boxes.size)
            )
            val distance2 = if (layerKind == FoliageLayerKind.DETAIL) layerMeta.drawDistance * layerMeta.drawDistance else -1f
            for (chunk in layerBake.chunks.indices) {
                if (!visible[chunk]) continue
                for (copy in layerBake.chunks[chunk]) {
                    val folder = layerMeta.models.getOrNull(copy.model)?.asset ?: continue
                    assertTrue("$folder is a loaded model", drawable.layerModels[folder] is Model)
                    val height = ground.heightAt(copy.x, copy.z) ?: continue
                    position.set(copy.x, height, copy.z)
                    if (distance2 >= 0f && position.dst2(camera.position) > distance2) continue
                    kept += Vector3(position)
                }
            }
        }
        return kept
    }

    /** The chunk sets of every layer, so a test can tell a real change from a camera that barely moved. */
    private fun visibleSet(prepared: PreparedFoliage, drawable: FoliageDrawable, camera: Camera): Set<String> {
        val bake = checkNotNull(drawable.bake)
        val ground = checkNotNull(drawable.terrain)
        val out = HashSet<String>()
        for (layerMeta in prepared.meta.layers) {
            val layerBake = bake.layers.firstOrNull { it.id == layerMeta.id } ?: continue
            val grid = foliageChunkGrid(layerBake, bake.chunkSize, ground, drawable.chunkPad)
            val visible = visibleFoliageChunks(
                grid, Matrix4(), layerMeta.layerKind(), layerMeta.drawDistance, camera, BooleanArray(grid.boxes.size)
            )
            visible.forEachIndexed { index, seen -> if (seen) out += "${layerMeta.id}:$index" }
        }
        return out
    }

    @Test
    fun theDrawableDrawsItsBakeAsInstancedPartsAndRefillsOnlyWhenWhatItDrawsChanges() = TestGl.run {
        val foliage = prepared()
        val models = loadModels()
        val drawable = FoliageDrawable(foliage, BuiltAssets { models[it] })
        try {
            assertEquals(modelFolders.keys, drawable.layerModels.keys)
            assertTrue("every layer model is loaded", drawable.layerModels.values.all { it is Model })
            assertTrue("the chunk boxes reach past a standing model", drawable.chunkPad > 0f)
            val camera = camera()
            val entity = Matrix4()

            // the first update fills the buffers with the copies the culling keeps
            drawable.update(camera, entity)
            assertEquals(expected(foliage, drawable, camera), drawable.drawnCopies)
            assertTrue("the DETAIL draw distance leaves copies out", drawable.drawnCopies < foliage.copyCount)
            assertEquals(1, drawable.rebuilds)

            // the same camera draws the same set, so nothing is refilled
            drawable.update(camera, entity)
            assertEquals(expected(foliage, drawable, camera), drawable.drawnCopies)
            assertEquals("a frame that draws the same set refills nothing", 1, drawable.rebuilds)

            // a camera the tiniest step further on keeps the same chunks
            val step = moved()
            assertEquals(
                "the step must be too small to change which chunks are seen",
                visibleSet(foliage, drawable, camera), visibleSet(foliage, drawable, step),
            )
            drawable.update(step, entity)
            assertEquals("a DETAIL layer is distance culled, so a moved camera refills", 2, drawable.rebuilds)
            assertEquals(expected(foliage, drawable, step), drawable.drawnCopies)

            // an object-only foliage draws to the far plane: the same step refills nothing
            val objects = objectOnly(foliage)
            val objectDrawable = FoliageDrawable(objects, BuiltAssets { models[it] })
            try {
                objectDrawable.update(camera, entity)
                assertEquals(1, objectDrawable.rebuilds)
                assertEquals(
                    "the step must be too small to change which chunks are seen",
                    visibleSet(objects, objectDrawable, camera), visibleSet(objects, objectDrawable, step),
                )
                objectDrawable.update(step, entity)
                assertEquals("an OBJECT-only foliage is not camera culled", 1, objectDrawable.rebuilds)
                assertEquals(expected(objects, objectDrawable, step), objectDrawable.drawnCopies)
            } finally {
                objectDrawable.dispose()
            }

            // a regenerated bake that holds only the OBJECT layer swaps what is drawn
            drawable.setBake(objectBake(foliage))
            drawable.update(step, entity)
            assertEquals(expected(objects, drawable, step), drawable.drawnCopies)
            assertTrue("the OBJECT layer alone draws fewer copies", drawable.drawnCopies < foliage.copyCount)

            // a terrain handed over as null drops everything, and the old one stands the copies back up
            drawable.setTerrain(null)
            drawable.update(step, entity)
            assertEquals(0, drawable.drawnCopies)
            assertRendersNothing(drawable)
            drawable.setTerrain(foliage.terrain)
            drawable.update(step, entity)
            assertEquals(expected(objects, drawable, step), drawable.drawnCopies)

            assertRenderablesAreInstancedPartsOfTheModels(drawable, models)
            assertTheyDrawWithoutGlErrors(drawable, step)
        } finally {
            drawable.dispose()
            models.values.forEach(Model::dispose)
        }
    }

    @Test
    fun eachLayerKindDrawsItsCopiesIntoItsOwnInstanceBuffers() = TestGl.run {
        val foliage = sharedFolder(prepared())
        val models = loadModels()
        val drawable = FoliageDrawable(foliage, BuiltAssets { models[it] })
        try {
            val camera = camera()
            drawable.update(camera, Matrix4())

            val objects = renderables(drawable, FoliageLayerKind.OBJECT)
            val details = renderables(drawable, FoliageLayerKind.DETAIL)
            assertTrue("the OBJECT layer draws its parts", objects.size > 0)
            assertTrue("the DETAIL layer draws its parts", details.size > 0)
            assertTrue(
                "one folder standing in both layers still gets a buffer set per kind",
                meshes(objects).none { it in meshes(details) },
            )
            // every node part of a set is its own mesh, and each of them draws that set's copies
            val keptObjects = expected(foliage, drawable, camera, FoliageLayerKind.OBJECT)
            val keptDetails = expected(foliage, drawable, camera, FoliageLayerKind.DETAIL)
            assertEquals(
                "every OBJECT part draws the OBJECT layer's copies",
                List(meshes(objects).size) { keptObjects },
                instances(objects),
            )
            assertEquals(
                "every DETAIL part draws the DETAIL layer's copies",
                List(meshes(details).size) { keptDetails },
                instances(details),
            )
        } finally {
            drawable.dispose()
            models.values.forEach(Model::dispose)
        }
    }

    @Test
    fun onlyTheObjectLayersKeepABoxOfTheCopiesTheyDraw() = TestGl.run {
        val foliage = prepared()
        val models = loadModels()
        val drawable = FoliageDrawable(foliage, BuiltAssets { models[it] })
        try {
            val camera = camera()
            drawable.update(camera, Matrix4())

            val bounds = checkNotNull(drawable.objectBounds) { "the box of the drawn OBJECT copies" }
            val modelBox = BoundingBox()
            checkNotNull(models["tree"]) { "the OBJECT layer's model" }.calculateBoundingBox(modelBox)
            assertTrue("the box only means anything if the model stands on its own origin", modelBox.contains(Vector3.Zero))
            val kept = kept(foliage, drawable, camera, FoliageLayerKind.OBJECT)
            assertTrue("the fixture draws OBJECT copies", kept.isNotEmpty())
            for (position in kept) {
                assertTrue("the box of the drawn copies holds a copy at $position", bounds.contains(position))
            }

            // a DETAIL layer only receives a shadow: its copies draw, but keep no box of their own
            val detail = FoliageDrawable(detailOnly(foliage), BuiltAssets { models[it] })
            try {
                detail.update(camera, Matrix4())
                assertTrue("the DETAIL layer draws its copies", detail.drawnCopies > 0)
                assertNull("DETAIL copies cast nothing", detail.objectBounds)
            } finally {
                detail.dispose()
            }
        } finally {
            drawable.dispose()
            models.values.forEach(Model::dispose)
        }
    }

    private fun assertRendersNothing(drawable: FoliageDrawable) {
        assertEquals("a terrainless foliage draws nothing", 0, renderables(drawable).size)
    }

    /** Every part is its own instanced mesh, holding the source model's own vertices under the identity world. */
    private fun assertRenderablesAreInstancedPartsOfTheModels(drawable: FoliageDrawable, models: Map<String, Model>) {
        val renderables = renderables(drawable)
        assertTrue("the bake has parts to draw", renderables.size > 0)
        val sources = models.values.flatMap { model ->
            model.meshes.map { mesh ->
                FloatArray(mesh.numVertices * mesh.vertexSize / 4).also { mesh.getVertices(it) }
            }
        }
        for (renderable in renderables) {
            val mesh = checkNotNull(renderable.meshPart.mesh) { "a mesh" }
            assertTrue("the part is instanced", mesh.isInstanced)
            assertTrue("the part has geometry", renderable.meshPart.size > 0)
            assertTrue("a copy carries its own transform", isIdentity(renderable.worldTransform))
            val vertices = FloatArray(mesh.numVertices * mesh.vertexSize / 4)
            mesh.getVertices(vertices)
            assertTrue("the part holds the model's own vertices", sources.any { it.contentEquals(vertices) })
        }
    }

    private fun assertTheyDrawWithoutGlErrors(drawable: FoliageDrawable, camera: Camera) {
        val provider = DefaultShaderProvider(ShaderConfig().apply { numPointLights = 0; numSpotLights = 0 })
        try {
            val renderables = renderables(drawable)
            val shader = provider.get(renderables.first()) as DefaultShader
            assertTrue("an instanced mesh is drawn by the instanced variant", shader.instanced)

            val batch = ModelBatch(null, null, provider)
            val environment = Environment().apply {
                add(DirectionalLight().set(Color(0.8f, 0.8f, 0.8f, 1f), 0f, -1f, 0f))
                set(ColorAttribute.createAmbientLight(0.25f, 0.25f, 0.25f, 1f))
            }
            while (Gdx.gl.glGetError() != GL20.GL_NO_ERROR) { // only this frame's own errors count
                Gdx.gl.glGetError()
            }
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            batch.begin(camera)
            batch.render(drawable, environment, ShaderProvider.DEFAULT_SHADER_KEY)
            batch.end()
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
        } finally {
            provider.dispose()
        }
    }

    private fun renderables(drawable: FoliageDrawable): Array<Renderable> {
        val out = Array<Renderable>()
        drawable.getRenderables(out, RenderablePool())
        return out
    }

    /** The instanced parts of one layer [kind], as the shadow depth pass asks for them. */
    private fun renderables(drawable: FoliageDrawable, kind: FoliageLayerKind): Array<Renderable> {
        val out = Array<Renderable>()
        drawable.getRenderablesOf(kind, out, RenderablePool())
        return out
    }

    /** The distinct meshes behind [renderables]; every part of one buffer set holds the same copies. */
    private fun meshes(renderables: Array<Renderable>): Set<Mesh> = renderables.mapNotNull { it.meshPart.mesh }.toSet()

    /** How many instances each distinct mesh draws, in the order [meshes] gives them. */
    private fun instances(renderables: Array<Renderable>): List<Int> =
        meshes(renderables).map { it.instances?.getNumInstances() ?: 0 }.sorted()

    /** `Matrix4` has no identity predicate in this libGDX version: read the sixteen floats. */
    private fun isIdentity(matrix: Matrix4): Boolean {
        val values = matrix.getValues()
        for (index in values.indices) if (values[index] != (if (index % 5 == 0) 1f else 0f)) return false
        return true
    }

    /** The fixture with only its OBJECT layer, as a layer toggle or a later bake would leave it. */
    private fun objectOnly(foliage: PreparedFoliage): PreparedFoliage = PreparedFoliage(
        foliage.meta.copy(layers = listOf(foliage.meta.layers[0])),
        foliage.terrain,
        mapOf(foliage.meta.layers[0].id to foliage.masks.getValue(foliage.meta.layers[0].id)),
        objectBake(foliage),
        foliage.fingerprint,
        foliage.dependencies,
    )

    /** The fixture with only its DETAIL layer: its copies are drawn, but they cast no shadow of their own. */
    private fun detailOnly(foliage: PreparedFoliage): PreparedFoliage {
        val layer = foliage.meta.layers.single { it.layerKind() == FoliageLayerKind.DETAIL }
        val bake = checkNotNull(foliage.bake) { "the fixture bake" }
        return PreparedFoliage(
            foliage.meta.copy(layers = listOf(layer)),
            foliage.terrain,
            mapOf(layer.id to foliage.masks.getValue(layer.id)),
            FoliageBake(bake.fingerprint, bake.chunkSize, listOf(bake.layers.single { it.id == layer.id })),
            foliage.fingerprint,
            foliage.dependencies,
        )
    }

    /** The fixture with both layers scattering the same model folder, as a project that reuses one asset would. */
    private fun sharedFolder(foliage: PreparedFoliage): PreparedFoliage = PreparedFoliage(
        foliage.meta.copy(layers = foliage.meta.layers.map { it.copy(models = listOf(FoliageModelMeta("tree"))) }),
        foliage.terrain,
        foliage.masks,
        foliage.bake,
        foliage.fingerprint,
        foliage.dependencies,
    )

    private fun objectBake(foliage: PreparedFoliage): FoliageBake {
        val bake = checkNotNull(foliage.bake) { "the fixture bake" }
        return FoliageBake(bake.fingerprint, bake.chunkSize, listOf(bake.layers.first { it.id == 0 }))
    }

    private class RenderablePool : Pool<Renderable>() {
        override fun newObject(): Renderable = Renderable()
    }

    /** Materials are not the subject here: one white texture per file keeps the model off the disk. */
    private class StubTextures : TextureProvider {
        private val byName = HashMap<String, Texture>()

        override fun load(fileName: String): Texture = byName.getOrPut(fileName) {
            val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
            pixmap.setColor(Color.WHITE)
            pixmap.fill()
            try {
                Texture(pixmap)
            } finally {
                pixmap.dispose()
            }
        }
    }
}
