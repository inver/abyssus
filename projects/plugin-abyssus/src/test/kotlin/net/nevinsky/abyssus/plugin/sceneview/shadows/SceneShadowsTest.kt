/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.shadows

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDrawable
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerKind
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.PreparedFoliage
import net.nevinsky.abyssus.lib.core.assets.foliage.layerKind
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.model.ModelMeta
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.scene.FoliagePlacement
import net.nevinsky.abyssus.lib.core.editor.scene.ModelEntity
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.testProject
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.ModelInstance
import net.nevinsky.abyssus.lib.gdx.Renderable
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.mesh.Mesh
import net.nevinsky.abyssus.lib.gdx.model.Model
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import net.nevinsky.abyssus.plugin.foliage.FoliageDrafts
import net.nevinsky.abyssus.plugin.sceneview.FakeFoliageAssets
import net.nevinsky.abyssus.plugin.sceneview.GlHarness
import net.nevinsky.abyssus.plugin.sceneview.SceneFoliage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File

/**
 * What reaches the shadow depth pass: the parts of the drawn models, and the instanced parts of a foliage's OBJECT
 * layers — never those of its DETAIL layers, which only receive a shadow (design decision 5). Building a drawable
 * means building meshes, so the main case is a GL test: opt-in `-Dabyssus.glTests=true`.
 */
class SceneShadowsTest {
    private val project = testProject("Foliage")
    private val untitled = testProject("Untitled")
    private val terrainName = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"
    private val foliageName = "foliage_meadow"
    private val treeAsset = "model_828d51e4-8427-4769-bcb6-13f8f21f23e9"

    /** The folders of the `Foliage` fixture's layers, backed by the models of `Untitled`. */
    private val layerModels = mapOf(
        "tree" to treeAsset,
        "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb" to "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb",
    )

    @Test
    fun objectLayersReachTheCasterListAndDetailLayersDoNot() {
        assumeTrue(GlHarness.enabled)
        var checked = false
        val result = GlHarness.render(SceneRenderParams.DEFAULT, 2) { _, frame ->
            if (frame != 0) return@render
            val models = loadModels()
            val drawable = FoliageDrawable(prepared(), BuiltAssets { models[it] })
            val scene = sceneOf(drawable)
            try {
                scene.update(listOf(placement()), project, camera())

                // one caster list, as the view builds it: the parts of a model and the parts of the foliage
                val model = checkNotNull(models["tree"]) { "the tree model" }
                val entity = ModelEntity(
                    AssetPlacement("2", treeAsset, PlacementTransform.IDENTITY), model, ModelInstance(model), null,
                )
                val casters = shadowCasters(listOf(entity), emptyList(), scene, Array(), RenderablePool(), ShadowCasterBounds())
                val modelCasters = casters.filter { mesh(it.renderable)?.isInstanced == false }
                val foliageCasters = casters.filter { mesh(it.renderable)?.isInstanced == true }

                assertTrue("a model's parts reach the depth pass", modelCasters.isNotEmpty())
                assertTrue(
                    "the model's own meshes cast, not the instanced copies of it",
                    modelCasters.all { mesh(it.renderable) in model.meshes },
                )
                assertTrue("the OBJECT layer's parts reach the depth pass", foliageCasters.isNotEmpty())

                val objectParts = parts(drawable, FoliageLayerKind.OBJECT)
                val detailParts = parts(drawable, FoliageLayerKind.DETAIL)
                assertTrue("both layers draw their copies", meshes(objectParts).isNotEmpty() && meshes(detailParts).isNotEmpty())
                assertEquals(
                    "every OBJECT part reaches the depth pass, and only those",
                    meshes(objectParts),
                    foliageCasters.mapNotNull { mesh(it.renderable) }.toSet(),
                )
                assertTrue(
                    "a DETAIL layer's parts never reach the depth pass",
                    foliageCasters.none { mesh(it.renderable) in meshes(detailParts) },
                )

                // they all draw the same copies, so they share the box those copies stand in
                val bounds = checkNotNull(foliageCasters.first().bounds) { "the box of the drawn copies" }
                assertTrue("every part carries that one box", foliageCasters.all { it.bounds === bounds })
                assertTrue(
                    "the box the source hands over covers what the drawable kept",
                    bounds.contains(checkNotNull(drawable.objectBounds)),
                )
                checked = true
            } finally {
                scene.dispose()
                drawable.dispose()
                models.values.forEach(Model::dispose)
            }
        }
        assertNull(result.error)
        assertTrue(checked)
    }

    @Test
    fun detailLayersOfAFoliageThatOnlyHasThemGiveNoCastersAtAll() {
        assumeTrue(GlHarness.enabled)
        var checked = false
        val result = GlHarness.render(SceneRenderParams.DEFAULT, 2) { _, frame ->
            if (frame != 0) return@render
            val models = loadModels()
            val drawable = FoliageDrawable(detailOnly(prepared()), BuiltAssets { models[it] })
            val scene = sceneOf(drawable)
            try {
                scene.update(listOf(placement()), project, camera())
                assertTrue("the DETAIL layer draws its copies", drawable.drawnCopies > 0)
                assertNull("DETAIL copies receive a shadow and cast none", drawable.objectBounds)
                assertTrue("so the depth pass gets nothing from the foliage", casters(scene).isEmpty())
                checked = true
            } finally {
                scene.dispose()
                drawable.dispose()
                models.values.forEach(Model::dispose)
            }
        }
        assertNull(result.error)
        assertTrue(checked)
    }

    @Test
    fun aSourceThatDrawsNothingGivesTheCasterListNothing() {
        val bounds = BoundingBox()
        val drawn = object : FoliageCastSource {
            override fun getCastRenderables(out: Array<Renderable>, pool: Pool<Renderable>) {
                out.add(Renderable())
                out.add(Renderable())
            }

            override fun castBounds(): BoundingBox? = bounds
        }
        val casters = shadowCasters(emptyList(), emptyList(), drawn, Array(), RenderablePool(), ShadowCasterBounds())
        assertEquals(2, casters.size)
        assertTrue("each part gets the box the source gave", casters.all { it.bounds === bounds })

        val nothing = object : FoliageCastSource {
            override fun getCastRenderables(out: Array<Renderable>, pool: Pool<Renderable>) {
                out.add(Renderable())
            }

            override fun castBounds(): BoundingBox? = null
        }
        assertTrue(
            "without a box of drawn copies there is nothing to cast",
            shadowCasters(emptyList(), emptyList(), nothing, Array(), RenderablePool(), ShadowCasterBounds()).isEmpty(),
        )
        assertTrue(
            "a view without foliage leaves the list to the models and the terrains",
            shadowCasters(emptyList(), emptyList(), null, Array(), RenderablePool(), ShadowCasterBounds()).isEmpty(),
        )
    }

    /** The caster list of a view whose scene is [foliage], with no models and no terrains around it. */
    private fun casters(foliage: FoliageCastSource): List<ShadowCaster> =
        shadowCasters(emptyList(), emptyList(), foliage, Array(), RenderablePool(), ShadowCasterBounds())

    private fun parts(drawable: FoliageDrawable, kind: FoliageLayerKind): Array<Renderable> {
        val out = Array<Renderable>()
        drawable.getRenderablesOf(kind, out, RenderablePool())
        return out
    }

    private fun meshes(renderables: Array<Renderable>): Set<Mesh> = renderables.mapNotNull { it.meshPart.mesh }.toSet()

    private fun mesh(renderable: Renderable): Mesh? = renderable.meshPart.mesh

    private fun sceneOf(drawable: FoliageDrawable): SceneFoliage {
        val assets = FakeFoliageAssets()
        assets.put(foliageName, drawable)
        return SceneFoliage(assets, FoliageDrafts(), RecordingLogger()) { it.run() }
    }

    private fun placement() = FoliagePlacement("1", foliageName, terrainName, PlacementTransform.IDENTITY)

    private fun camera(): PerspectiveCamera = PerspectiveCamera(60f, 4f, 3f).apply {
        position.set(10f, 5f, 10f)
        direction.set(1f, 0f, 0f)
        near = 0.1f
        far = 5000f
        update()
    }

    /** The fixture's foliage read the way the storage reads it: settings, masks and the committed bake. */
    private fun prepared(): PreparedFoliage {
        val files = FileLoader(project)
        val metas = AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files)
        val loader = FoliageLoader(files, metas, TerrainLoader(files, metas))
        return checkNotNull(loader.prepare(foliageName)) { "the Foliage fixture" }.staged
    }

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

    /** The layer models, read from `Untitled` the way the drawable's storage would hand them over. */
    private fun loadModels(): Map<String, Model> {
        val metas = AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), FileLoader(untitled))
        return layerModels.map { (folder, asset) ->
            val meta = checkNotNull(metas.loadBaseMeta(asset)) { "$asset has a meta" }
            val file = FileHandle(File(untitled, "assets/$asset/${checkNotNull(meta.typedAdditional<ModelMeta>().file)}"))
            folder to Model(AssimpModelLoader().loadData(file), StubTextures())
        }.toMap()
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
