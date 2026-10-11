/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDataFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDrawable
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageModelMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.PreparedFoliage
import net.nevinsky.abyssus.lib.core.assets.foliage.foliageChunkSize
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageScatter
import net.nevinsky.abyssus.lib.core.editor.foliage.MaskRect
import net.nevinsky.abyssus.lib.core.editor.foliage.mergeFoliageChunks
import net.nevinsky.abyssus.lib.core.editor.scene.FoliagePlacement
import net.nevinsky.abyssus.lib.core.editor.testProject
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import net.nevinsky.abyssus.plugin.foliage.FoliageDrafts
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import kotlin.math.ceil

/**
 * What the scene view does with the foliage of its terrain entities: what it draws, what it skips with a reason in
 * the log, and how an uncommitted draft reaches the drawable without a file write. The drawables are the real ones
 * over the `Foliage` fixture, but with model folders the storage never built, so no test here needs GL.
 */
class SceneFoliageTest {
    init {
        GdxNativesLoader.load() // `Frustum.update` projects through the native `Matrix4.prj`
    }

    private val log = RecordingLogger()
    private val project = testProject("Foliage")
    private val terrainName = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"
    private val foliageName = "foliage_meadow"
    private val prepared = load()
    private val resolution = prepared.meta.maskResolution
    private val terrain = checkNotNull(prepared.terrain) { "the fixture terrain" }
    private val bake = checkNotNull(prepared.bake) { "the fixture bake" }
    private val scatter = FoliageScatter(FoliageFingerprint())
    private val dataFile = FoliageDataFile()
    private val drafts = FoliageDrafts()
    private val assets = FakeFoliageAssets()
    private val scene = SceneFoliage(assets, drafts, log) { it.run() }
    private val camera = PerspectiveCamera(60f, 4f, 3f).apply { // a 4 by 3 viewport: aspect 4/3
        position.set(10f, 5f, 10f)
        direction.set(1f, 0f, 0f)
        near = 0.1f
        far = 5000f
        update()
    }

    @Test
    fun theFoliageOfTheEntitiesTerrainIsDrawn() {
        assets.put(foliageName, drawable(prepared))
        tick(listOf(placement()))
        assertEquals(listOf(foliageName), scene.drawn.map { it.placement.foliageName })
        assertEquals("a healthy foliage logs nothing", emptyList<String>(), log.warnings)
    }

    @Test
    fun aWrongTerrainIsLoggedOnceAndDrawsNothing() {
        assets.put(foliageName, drawable(prepared))
        val placement = placement(terrain = "terrain_of_another_asset")
        tick(listOf(placement), times = 3)
        assertTrue("the terrain and the models around it are drawn all the same", scene.drawn.isEmpty())
        assertEquals("logged once, not once a frame", 1, warnings("bound to terrain").size)
    }

    @Test
    fun aTerrainTheProjectCannotReadIsLoggedOnceAndDrawsNothing() {
        assets.put(foliageName, drawable(prepared.with(terrain = null)))
        tick(listOf(placement()), times = 3)
        assertTrue(scene.drawn.isEmpty())
        assertEquals(1, warnings("which the project cannot read").size)
    }

    @Test
    fun aMissingModelIsLoggedAndTheRestIsStillDrawn() {
        val ghost = "model_without_a_folder"
        val meta = prepared.meta.copy(
            layers = prepared.meta.layers.mapIndexed { index, layer ->
                if (index == 0) layer.copy(models = layer.models + FoliageModelMeta(ghost)) else layer
            },
        )
        assets.put(foliageName, drawable(prepared.with(meta = meta)) { it != ghost })
        tick(listOf(placement()), times = 3)
        assertEquals("the other model's copies are drawn", 1, scene.drawn.size)
        assertEquals(1, warnings("'$ghost'").size)
        assertTrue(warnings("'$ghost'").single().contains("only its own copies are dropped"))
    }

    @Test
    fun aFoliageFolderThatIsNotThereIsLoggedOnceAndSkipped() {
        tick(listOf(placement()), times = 3)
        assertTrue(scene.drawn.isEmpty())
        assertEquals(1, warnings("is missing or its meta.json cannot be read").size)
    }

    @Test
    fun aFoliageStillBeingBuiltIsNeitherDrawnNorLogged() {
        assets.loading = true
        tick(listOf(placement()), times = 3)
        assertTrue(scene.drawn.isEmpty())
        assertEquals("nothing is wrong yet", emptyList<String>(), log.warnings)
    }

    @Test
    fun aTruncatedBakeIsRegeneratedFromTheSettings() {
        assets.put(foliageName, drawable(prepared.with(bake = null)))
        tick(listOf(placement()))
        val generated = checkNotNull(scene.drawn.single().foliage.bake) { "a regenerated bake" }
        assertArrayEquals(
            "the same inputs give the bytes the committed bake holds",
            dataFile.write(bake), dataFile.write(generated),
        )
        assertEquals(1, warnings("no readable foliage.data").size)
    }

    @Test
    fun removingTheComponentRemovesTheDrawnFoliage() {
        assets.put(foliageName, drawable(prepared))
        tick(listOf(placement()))
        assertEquals(1, scene.drawn.size)
        tick(emptyList())
        assertTrue(scene.drawn.isEmpty())
        assertEquals("the view no longer asks for the asset", emptySet<String>(), assets.wanted)
    }

    @Test
    fun aNewDraftRevisionReScattersOnlyTheDirtyChunks() {
        val stored = synthetic(prepared)
        assets.put(foliageName, drawable(prepared.with(bake = stored)))
        val draft = drafts.open(foliageName, terrain, resolution)
        val editable = draft.editableMask(0, prepared.masks.getValue(0))
        for (z in 0..5) for (x in 0..5) editable[z * resolution + x] = 0
        val rect = MaskRect(0, 0, 5, 5)
        draft.stamped(0, rect)
        val dirty = HashSet(draft.dirtyChunks)
        assertTrue("the stamp touched a handful of chunks", dirty.isNotEmpty() && dirty.size < 64)

        tick(listOf(placement()))

        val masks = prepared.masks + draft.masks
        val patch = scatter.scatter(terrain, prepared.meta, masks, chunks = dirty)
        val expected = mergeFoliageChunks(stored, patch.bake, dirty)
        val shown = checkNotNull(scene.drawn.single().foliage.bake) { "the merged bake" }
        assertArrayEquals(dataFile.write(expected), dataFile.write(shown))
        assertEquals(
            "a chunk the stamp never touched keeps the copies it had",
            stored.layers[0].chunk(49, 49), shown.layers[0].chunk(49, 49),
        )
        assertNotEquals("the dirty chunks were re-scattered", stored.layers[0].chunk(0, 0), shown.layers[0].chunk(0, 0))
    }

    /** Runs [update] [times] frames; generation is synchronous here, but the result is applied on the next one. */
    private fun tick(placements: List<FoliagePlacement>, times: Int = 2) {
        repeat(times) { scene.update(placements, project, camera) }
    }

    private fun placement(
        terrain: String = terrainName,
        foliage: String = foliageName,
        entity: String = "1",
    ): FoliagePlacement = FoliagePlacement(entity, foliage, terrain, PlacementTransform.IDENTITY)

    /** A drawable whose model folders are all built ([has] says which of them the storage managed to build). */
    private fun drawable(staged: PreparedFoliage, has: (String) -> Boolean = { true }): FoliageDrawable =
        FoliageDrawable(staged, BuiltAssets { name -> if (has(name)) unbuiltModel else null })

    private fun warnings(fragment: String): List<String> = log.warnings.filter { fragment in it }

    /** The fixture's foliage read the way the storage reads it: settings, masks and the committed bake. */
    private fun load(): PreparedFoliage {
        val files = FileLoader(project)
        val metas = AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files)
        val loader = FoliageLoader(files, metas, TerrainLoader(files, metas))
        return checkNotNull(loader.prepare(foliageName)) { "the Foliage fixture" }.staged
    }

    /** The fixture with one thing changed: the bake it holds or the terrain it stands on. */
    private fun PreparedFoliage.with(
        meta: FoliageMeta = this.meta,
        terrain: TerrainData? = this.terrain,
        bake: FoliageBake? = this.bake,
    ): PreparedFoliage = PreparedFoliage(meta, terrain, masks, bake, fingerprint, dependencies)

    /**
     * A bake of the right shape that no scatter would produce: one copy per chunk, at the chunk's own corner. A draft
     * that re-scatters only its dirty chunks leaves these copies everywhere else, a full re-scatter would not.
     */
    private fun synthetic(staged: PreparedFoliage): FoliageBake {
        val chunkSize = foliageChunkSize(terrain.size)
        val across = ceil(terrain.size.toDouble() / chunkSize).toInt()
        val layers = staged.meta.layers.map { layer ->
            FoliageLayerBake(layer.id, layer.models.size, across, across, List(across * across) { index ->
                val i = index % across
                val j = index / across
                listOf(FoliageCopy(0, i * chunkSize + 1f, j * chunkSize + 1f, 0f, 1f))
            })
        }
        return FoliageBake(staged.fingerprint, chunkSize, layers)
    }
}
