package net.nevinsky.abyssus.assets.sky.procedural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import net.nevinsky.abyssus.assets.sky.cube.PreparedSkybox
import net.nevinsky.abyssus.assets.testLoading
import net.nevinsky.abyssus.assets.testProject
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.files.AssetFiles
import java.io.File
import java.nio.file.Files

class ProceduralSkyLoaderTest {
    private val fixture = testProject("Untitled")

    private fun copyOfProject(): File {
        val dir = Files.createTempDirectory("sky").toFile()
        File(fixture, "assets/skybox_physical").copyRecursively(File(dir, "assets/skybox_physical"))
        return dir
    }

    @Test
    fun prepareReadsMetaAndBothShaders() {
        val prepared = ProceduralSkyLoader().prepare(AssetFiles(fixture, JsonProcessor()), "skybox_physical")!!
        assertEquals(AtmosphereParams(), prepared.params)
        assertTrue(prepared.vertex.contains("a_position"))
        assertTrue(prepared.fragment.contains("raySphere"))
    }

    @Test
    fun missingFragmentFileFailsPrepare() {
        val dir = copyOfProject()
        try {
            File(dir, "assets/skybox_physical/sky.frag").delete()
            val error = runCatching { ProceduralSkyLoader().prepare(AssetFiles(dir, JsonProcessor()), "skybox_physical") }.exceptionOrNull()
            assertTrue("expected a missing-shader error, got $error", error is IllegalStateException && error.message!!.contains("fragment"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun wrongTypeIsRejected() {
        assertNull(ProceduralSkyLoader().prepare(AssetFiles(fixture, JsonProcessor()), "skybox_default"))
    }

    @Test
    fun dispatcherPicksTheLoaderByType() {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val files = AssetFiles(fixture, JsonProcessor())
        val loader = testLoading().skies
        val procedural = loader.prepare(files, "skybox_physical")
        val cube = loader.prepare(files, "skybox_default")
        try {
            assertTrue(procedural!!.prepared is PreparedProceduralSky)
            assertTrue(cube!!.prepared is PreparedSkybox)
            assertNull(loader.prepare(files, "nope"))
        } finally {
            cube?.let(loader::discard)
        }
    }
}
