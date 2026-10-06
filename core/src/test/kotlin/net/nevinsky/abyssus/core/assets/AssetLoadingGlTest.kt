package net.nevinsky.abyssus.core.assets

import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.Texture
import net.nevinsky.abyssus.core.io.FileLoader
import net.nevinsky.abyssus.core.assets.loading.CompositeAssetLoader
import net.nevinsky.abyssus.core.assets.loading.AssetStorage
import net.nevinsky.abyssus.core.assets.model.ModelLoader
import net.nevinsky.abyssus.core.assets.sky.cube.SkyboxCube
import net.nevinsky.abyssus.core.assets.sky.cube.SkyboxLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.ExrLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.HdrSky
import net.nevinsky.abyssus.core.assets.sky.hdr.HdrSkyLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.ToneCurve
import net.nevinsky.abyssus.core.assets.sky.procedural.ProceduralSky
import net.nevinsky.abyssus.core.assets.sky.procedural.ProceduralSkyLoader
import net.nevinsky.abyssus.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.core.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.core.assets.texture.TextureLoader
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.core.model.Model
import net.nevinsky.abyssus.testing.warningsTo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor

/**
 * The `asset-loading` capability: the whole graph built by hand, with no IDE classes, loading the fixture projects in a
 * real GL context. Opt-in: `-Dabyssus.glTests=true`.
 */
class AssetLoadingGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val untitled = testProject("Untitled")
    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private val mainSceneModels = listOf(
        "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb", // Model 0
        "model_fc33e1f1-015b-4524-9b10-aa417acd273c", // Model 2
        "model_900f6f61-6384-434a-be81-56ce303fbb56", // Model 6
    )
    private val mainSceneTerrain = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"

    /** The loaders of one project folder, wired by hand as the plugin does: one composite over every kind of asset. */
    private inner class Project(val dir: File, val log: Logger = NOPLogger.NOP_LOGGER) {
        val files = FileLoader(dir)
        val metas = testMetaLoader(dir, log, files)
        private val composite = CompositeAssetLoader(
            metas,
            mapOf(
                MetaType.MODEL to ModelLoader(metas, AssimpModelLoader(), files),
                MetaType.TERRAIN to TerrainLoader(files, metas),
                MetaType.TEXTURE to TextureLoader(files, metas),
                MetaType.PIXMAP_TEXTURE to TextureLoader(files, metas),
                MetaType.SKYBOX to SkyboxLoader(files, metas, skyShaders()),
                MetaType.SKYBOX_PROCEDURAL to ProceduralSkyLoader(files, metas),
                MetaType.SKYBOX_HDR to HdrSkyLoader(metas, ExrLoader(files), skyShaders(), ToneCurve()),
            ),
        )

        /** The one storage of this project: it builds and owns every kind of asset. */
        fun assets() = AssetStorage(Executor(Runnable::run), composite, log)
    }

    /** Requests [names] and pumps frame after frame, as the scene view does, until nothing is loading. */
    private fun loadAll(assets: AssetStorage<*, *>, names: Set<String>, frames: Int = 400) {
        repeat(frames) {
            names.forEach(assets::request)
            assets.pump()
            if (!assets.isLoading()) return
        }
        error("still loading after $frames frames")
    }

    @Test
    fun mainScenesContentLoadsOutsideTheIde() {
        val logged = mutableListOf<String>()
        TestGl.run {
            val assets = Project(untitled, warningsTo(logged)).assets()
            try {
                loadAll(assets, mainSceneModels.toSet() + mainSceneTerrain)
                mainSceneModels.forEach { assertNotNull("model $it", assets.getAs<Model>(it)) }
                assertNotNull("terrain", assets.getAs<TerrainMesh>(mainSceneTerrain))
            } finally {
                assets.dispose()
            }
        }
        assertEquals(emptyList<String>(), logged)
    }

    @Test
    fun aTerrainLoadsItsSplatTexturesFirstAndDrawsFromThem() {
        val dir = Files.createTempDirectory("splat").toFile().also(dirs::add)
        File(untitled, "assets/$mainSceneTerrain").copyRecursively(File(dir, "assets/terr"))
        File(dir, "assets/tex").mkdirs()
        val pixmap = Pixmap(2, 2, Pixmap.Format.RGBA8888)
        try { PixmapIO.writePNG(FileHandle(File(dir, "assets/tex/a.png")), pixmap) } finally { pixmap.dispose() }
        File(dir, "assets/tex/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"uuid":"00000000-0000-0000-0000-000000000001","type":"TEXTURE","additional":{"file":"a.png"}}"""
        )
        val terrainMeta = File(dir, "assets/terr/meta.json")
        terrainMeta.writeText(terrainMeta.readText().replace("\"splatBase\":null", "\"splatBase\":\"00000000-0000-0000-0000-000000000001\""))
        val logged = mutableListOf<String>()
        TestGl.run {
            val assets = Project(dir, warningsTo(logged)).assets()
            try {
                loadAll(assets, setOf("terr"))
                assertNotNull(assets.getAs<TerrainMesh>("terr"))
                assertNotNull("the dependency was loaded by the same storage", assets.getAs<Texture>("tex"))
                val before = assets.getAs<Texture>("tex")
                assets.retain(setOf("terr"))
                assertSame("retaining the terrain keeps its texture", before, assets.getAs<Texture>("tex"))
            } finally {
                assets.dispose()
            }
        }
        assertEquals(emptyList<String>(), logged)
    }

    @Test
    fun everySkyKindLoadsOutsideTheIde() {
        val hdrProject = Files.createTempDirectory("hdrsky").toFile().also(dirs::add)
        File(hdrProject, "assets/sky").mkdirs()
        exrFixture().copyTo(File(hdrProject, "assets/sky/sky.exr"))
        File(hdrProject, "assets/sky/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":0,"type":"SKYBOX_HDR","additional":{"file":"sky.exr"}}"""
        )
        TestGl.run {
            val assets = Project(untitled).assets()
            val hdr = Project(hdrProject).assets()
            try {
                // one sky at a time, as a scene names one
                loadAll(assets, setOf("skybox_default"))
                assertTrue(assets.get("skybox_default") is SkyboxCube)
                loadAll(assets, setOf("skybox_physical"))
                assertTrue(assets.get("skybox_physical") is ProceduralSky)
                loadAll(hdr, setOf("sky"))
                assertTrue(hdr.get("sky") is HdrSky)
            } finally {
                assets.dispose()
                hdr.dispose()
            }
        }
    }

    @Test
    fun aMissingFolderIsLoggedOnceToTheCallersLog() {
        val logged = mutableListOf<String>()
        TestGl.run {
            val models = Project(untitled, warningsTo(logged)).assets()
            try {
                loadAll(models, setOf("model_missing", mainSceneModels[0]))
                repeat(20) { models.request("model_missing"); models.request(mainSceneModels[0]); models.pump() }
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
        val dir = Files.createTempDirectory("broken").toFile().also(dirs::add)
        val model = mainSceneModels[0]
        File(untitled, "assets/$model").copyRecursively(File(dir, "assets/$model"))
        File(dir, "assets/$model/model.gltf").writeText("{ this is not glTF")
        TestGl.run {
            val models = Project(dir, warningsTo(logged)).assets()
            try {
                loadAll(models, setOf(model))
                repeat(50) { models.request(model); models.pump() }
                assertNull(models.get(model))
            } finally {
                models.dispose()
            }
        }
        assertEquals(1, logged.size)
    }

    @Test
    fun twoProjectsLoadIndependently() {
        val loggedA = mutableListOf<String>()
        val loggedB = mutableListOf<String>()
        TestGl.run {
            val modelsA = Project(untitled, warningsTo(loggedA)).assets()
            val modelsB = Project(testProject("Animated"), warningsTo(loggedB)).assets()
            try {
                loadAll(modelsA, setOf(mainSceneModels[0], "model_missing"))
                loadAll(modelsB, setOf("model_anim"))
                assertNotNull(modelsA.get(mainSceneModels[0]))
                val anim = modelsB.getAs<Model>("model_anim")
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
