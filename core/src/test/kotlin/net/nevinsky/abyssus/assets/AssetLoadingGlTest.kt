package net.nevinsky.abyssus.assets

import net.nevinsky.abyssus.testing.warningsTo
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.assets.loading.SceneAssets
import net.nevinsky.abyssus.assets.sky.cube.SkyboxCube
import net.nevinsky.abyssus.assets.sky.hdr.HdrSky
import net.nevinsky.abyssus.assets.sky.procedural.ProceduralSky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The `asset-loading` capability: the whole graph built by hand, with no IDE classes, loading the fixture projects in a
 * real GL context. Opt-in: `-Dabyssus.glTests=true`.
 */
class AssetLoadingGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val untitled = testProject("Untitled")

    private val mainSceneModels = listOf(
        "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb", // Model 0
        "model_fc33e1f1-015b-4524-9b10-aa417acd273c", // Model 2
        "model_900f6f61-6384-434a-be81-56ce303fbb56", // Model 6
    )
    private val mainSceneTerrain = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"

    /** Requests [names] from [dir] frame after frame, as the scene view does, until nothing is loading. */
    private fun <P : Any, T : Disposable> loadAll(assets: SceneAssets<P, T>, dir: File, names: Set<String>, frames: Int = 400) {
        repeat(frames) {
            assets.update(dir, names)
            if (!assets.isLoading) return
        }
        error("still loading after $frames frames")
    }

    @Test
    fun mainScenesContentLoadsOutsideTheIde() {
        val logged = mutableListOf<String>()
        TestGl.run {
            val loading = testLoading(log = warningsTo(logged))
            val models = loading.assets(loading.models)
            val terrains = loading.assets(loading.terrains)
            try {
                loadAll(models, untitled, mainSceneModels.toSet())
                loadAll(terrains, untitled, setOf(mainSceneTerrain))
                mainSceneModels.forEach { assertNotNull("model $it", models.get(it)) }
                assertNotNull("terrain", terrains.get(mainSceneTerrain))
            } finally {
                models.dispose()
                terrains.dispose()
            }
        }
        assertEquals(emptyList<String>(), logged)
    }

    @Test
    fun everySkyKindLoadsOutsideTheIde() {
        TestGl.run {
            val loading = testLoading()
            val skies = loading.assets(loading.skies)
            try {
                // one sky at a time, as a scene names one
                loadAll(skies, untitled, setOf("skybox_default"))
                assertTrue(skies.get("skybox_default") is SkyboxCube)
                loadAll(skies, untitled, setOf("skybox_physical"))
                assertTrue(skies.get("skybox_physical") is ProceduralSky)
                loadAll(skies, untitled, setOf("skybox_hdr"))
                assertTrue(skies.get("skybox_hdr") is HdrSky)
            } finally {
                skies.dispose()
            }
        }
    }

    @Test
    fun aMissingFolderIsLoggedOnceToTheCallersLog() {
        val logged = mutableListOf<String>()
        TestGl.run {
            val loading = testLoading(log = warningsTo(logged))
            val models = loading.assets(loading.models)
            try {
                loadAll(models, untitled, setOf("model_missing", mainSceneModels[0]))
                repeat(20) { models.update(untitled, setOf("model_missing", mainSceneModels[0])) }
                assertNull(models.get("model_missing"))
                assertNotNull(models.get(mainSceneModels[0]))
            } finally {
                models.dispose()
            }
        }
        assertEquals(1, logged.size)
        assertTrue(logged.single(), logged.single().contains("model_missing"))
    }

    @Test
    fun aBrokenAssetIsReportedOnceAcrossFrames() {
        val logged = mutableListOf<String>()
        val dir = Files.createTempDirectory("broken").toFile()
        try {
            val model = mainSceneModels[0]
            File(untitled, "assets/$model").copyRecursively(File(dir, "assets/$model"))
            File(dir, "assets/$model/model.gltf").writeText("{ this is not glTF")
            TestGl.run {
                val loading = testLoading(log = warningsTo(logged))
                val models = loading.assets(loading.models)
                try {
                    loadAll(models, dir, setOf(model))
                    repeat(50) { models.update(dir, setOf(model)) }
                    assertNull(models.get(model))
                } finally {
                    models.dispose()
                }
            }
        } finally {
            dir.deleteRecursively()
        }
        assertEquals(1, logged.size)
    }

    @Test
    fun twoProjectsLoadIndependently() {
        val loggedA = mutableListOf<String>()
        val loggedB = mutableListOf<String>()
        TestGl.run {
            val a = testLoading(log = warningsTo(loggedA))
            val b = testLoading(log = warningsTo(loggedB))
            val modelsA = a.assets(a.models)
            val modelsB = b.assets(b.models)
            try {
                loadAll(modelsA, untitled, setOf(mainSceneModels[0], "model_missing"))
                loadAll(modelsB, testProject("Animated"), setOf("model_anim"))
                assertNotNull(modelsA.get(mainSceneModels[0]))
                val anim = modelsB.get("model_anim")
                assertNotNull(anim)
                modelsA.dispose()
                assertNull(modelsA.get(mainSceneModels[0]))
                // releasing one leaves the other's assets loaded and usable
                assertTrue(anim === modelsB.get("model_anim"))
                assertFalse(anim!!.meshes.isEmpty())
            } finally {
                modelsB.dispose()
            }
        }
        assertEquals(1, loggedA.size)
        assertEquals(emptyList<String>(), loggedB)
    }
}
