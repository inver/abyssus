package net.nevinsky.abyssus.sceneview.skybox

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.sceneview.ProjectAssetFiles
import java.io.File
import java.nio.file.Files

class HdrSkyLoaderTest : BasePlatformTestCase() {
    private val fixture = File("src/test/testData/project/Untitled")

    private fun withCopy(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("hdrsky").toFile()
        try {
            File(fixture, "assets/skybox_hdr").copyRecursively(File(dir, "assets/skybox_hdr"))
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun failure(dir: File): Throwable? =
        runCatching { HdrSkyLoader().prepare(ProjectAssetFiles(dir), "skybox_hdr") }.exceptionOrNull()

    fun testPreparesTheFixture() {
        val prepared = HdrSkyLoader().prepare(ProjectAssetFiles(fixture), "skybox_hdr")!!
        assertEquals("sky.hdr", prepared.file.name)
        assertEquals(HdrFixtures.FIXTURE_WIDTH, prepared.image.width)
        assertEquals(HdrFixtures.FIXTURE_HEIGHT, prepared.image.height)
    }

    fun testAFileNamedUnderAnyKeyIsUsed() = withCopy { dir ->
        val folder = File(dir, "assets/skybox_hdr")
        HdrFixtures.write(File(folder, "other.hdr"), 16, 8, pixel = HdrFixtures.uniform(1f))
        File(folder, "meta.json").writeText("""{"version":1,"lastModified":0,"type":"SKYBOX_HDR","additional":{"panorama":"other.hdr"}}""")
        assertEquals("other.hdr", HdrSkyLoader().prepare(ProjectAssetFiles(dir), "skybox_hdr")!!.file.name)
    }

    fun testMissingFolderIsNull() {
        assertNull(HdrSkyLoader().prepare(ProjectAssetFiles(fixture), "no_such_sky"))
    }

    fun testWrongTypeIsNull() {
        assertNull(HdrSkyLoader().prepare(ProjectAssetFiles(fixture), "skybox_default"))
    }

    fun testNoHdrFailsWithAReason() = withCopy { dir ->
        File(dir, "assets/skybox_hdr/sky.hdr").delete()
        val error = failure(dir)
        assertTrue("got $error", error is IllegalStateException && error.message!!.contains("no .hdr file"))
    }

    fun testTruncatedFailsWithAReason() = withCopy { dir ->
        val file = File(dir, "assets/skybox_hdr/sky.hdr")
        file.writeBytes(file.readBytes().let { it.copyOf(it.size / 2) })
        val error = failure(dir)
        assertTrue("got $error", error is IllegalStateException && error.message!!.contains("truncated"))
    }

    fun testDispatcherPicksTheHdrLoader() {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val loader = SkyLoader()
        val files = ProjectAssetFiles(fixture)
        val hdr = loader.prepare(files, "skybox_hdr")
        val cube = loader.prepare(files, "skybox_default")
        try {
            assertTrue(hdr is PreparedSky.Hdr)
            assertTrue(cube is PreparedSky.Cube)
        } finally {
            cube?.let(loader::discard)
        }
    }
}
