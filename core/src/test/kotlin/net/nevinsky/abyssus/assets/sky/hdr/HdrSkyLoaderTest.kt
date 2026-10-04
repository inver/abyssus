package net.nevinsky.abyssus.assets.sky.hdr

import net.nevinsky.abyssus.testing.warningsTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import net.nevinsky.abyssus.assets.skyShaders
import net.nevinsky.abyssus.assets.sky.cube.PreparedSkybox
import net.nevinsky.abyssus.assets.testLoading
import net.nevinsky.abyssus.assets.testProject
import java.io.File
import java.nio.file.Files

class HdrSkyLoaderTest {
    private val fixture = testProject("Untitled")
    private val logged = mutableListOf<String>()
    private val loading = testLoading(log = warningsTo(logged))
    private val loader = HdrSkyLoader(loading.decoder, loading.hdrFiles, skyShaders(), loading.toneCurve, loading.log)

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
        runCatching { loader.prepare(loading.files(dir), "skybox_hdr") }.exceptionOrNull()

    @Test
    fun preparesTheFixture() {
        val prepared = loader.prepare(loading.files(fixture), "skybox_hdr")!!
        assertEquals("sky.hdr", prepared.file.name)
        assertEquals(HdrFixtures.FIXTURE_WIDTH, prepared.image.width)
        assertEquals(HdrFixtures.FIXTURE_HEIGHT, prepared.image.height)
    }

    @Test
    fun aFileNamedUnderAnyKeyIsUsed() = withCopy { dir ->
        val folder = File(dir, "assets/skybox_hdr")
        HdrFixtures.write(File(folder, "other.hdr"), 16, 8, pixel = HdrFixtures.uniform(1f))
        File(folder, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"version":1,"lastModified":0,"type":"SKYBOX_HDR","additional":{"panorama":"other.hdr"}}""")
        assertEquals("other.hdr", loader.prepare(loading.files(dir), "skybox_hdr")!!.file.name)
    }

    @Test
    fun severalHdrFilesWarnThroughTheLog() = withCopy { dir ->
        HdrFixtures.write(File(dir, "assets/skybox_hdr/a.hdr"), 16, 8, pixel = HdrFixtures.uniform(1f))
        assertEquals("a.hdr", loader.prepare(loading.files(dir), "skybox_hdr")!!.file.name)
        assertEquals(1, logged.size)
        assertTrue(logged.single(), logged.single().startsWith("HDR sky 'skybox_hdr': several .hdr files"))
    }

    @Test
    fun missingFolderIsNull() {
        assertNull(loader.prepare(loading.files(fixture), "no_such_sky"))
    }

    @Test
    fun wrongTypeIsNull() {
        assertNull(loader.prepare(loading.files(fixture), "skybox_default"))
    }

    @Test
    fun noHdrFailsWithAReason() = withCopy { dir ->
        File(dir, "assets/skybox_hdr/sky.hdr").delete()
        val error = failure(dir)
        assertTrue("got $error", error is IllegalStateException && error.message!!.contains("no .hdr file"))
    }

    @Test
    fun truncatedFailsWithAReason() = withCopy { dir ->
        val file = File(dir, "assets/skybox_hdr/sky.hdr")
        file.writeBytes(file.readBytes().let { it.copyOf(it.size / 2) })
        val error = failure(dir)
        assertTrue("got $error", error is IllegalStateException && error.message!!.contains("truncated"))
    }

    @Test
    fun dispatcherPicksTheHdrLoader() {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val loader = loading.skies
        val files = loading.files(fixture)
        val hdr = loader.prepare(files, "skybox_hdr")
        val cube = loader.prepare(files, "skybox_default")
        try {
            assertTrue(hdr!!.prepared is PreparedHdrSky)
            assertTrue(cube!!.prepared is PreparedSkybox)
        } finally {
            cube?.let(loader::discard)
        }
    }
}
