package net.nevinsky.abyssus.sceneview.skybox

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.sceneview.ProjectAssetFiles
import net.nevinsky.abyssus.sceneview.skybox.procedural.AtmosphereParams
import net.nevinsky.abyssus.sceneview.skybox.procedural.ProceduralSkyLoader
import java.io.File
import java.nio.file.Files

class ProceduralSkyLoaderTest : BasePlatformTestCase() {
    private val fixture = File("src/test/testData/project/Untitled")

    private fun copyOfProject(): File {
        val dir = Files.createTempDirectory("sky").toFile()
        File(fixture, "assets/skybox_physical").copyRecursively(File(dir, "assets/skybox_physical"))
        return dir
    }

    fun testPrepareReadsMetaAndBothShaders() {
        val prepared = ProceduralSkyLoader().prepare(ProjectAssetFiles(fixture), "skybox_physical")!!
        assertEquals(AtmosphereParams.EARTH, prepared.params)
        assertTrue(prepared.vertex.contains("a_position"))
        assertTrue(prepared.fragment.contains("raySphere"))
    }

    fun testMissingFragmentFileFailsPrepare() {
        val dir = copyOfProject()
        try {
            File(dir, "assets/skybox_physical/sky.frag").delete()
            val error = runCatching { ProceduralSkyLoader().prepare(ProjectAssetFiles(dir), "skybox_physical") }.exceptionOrNull()
            assertTrue("expected a missing-shader error, got $error", error is IllegalStateException && error.message!!.contains("fragment"))
        } finally {
            dir.deleteRecursively()
        }
    }

    fun testWrongTypeIsRejected() {
        assertNull(ProceduralSkyLoader().prepare(ProjectAssetFiles(fixture), "skybox_default"))
    }

    fun testDispatcherPicksTheLoaderByType() {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val files = ProjectAssetFiles(fixture)
        val loader = SkyLoader()
        val procedural = loader.prepare(files, "skybox_physical")
        val cube = loader.prepare(files, "skybox_default")
        try {
            assertTrue(procedural is PreparedSky.Procedural)
            assertTrue(cube is PreparedSky.Cube)
            assertNull(loader.prepare(files, "nope"))
        } finally {
            cube?.let(loader::discard)
        }
    }
}
