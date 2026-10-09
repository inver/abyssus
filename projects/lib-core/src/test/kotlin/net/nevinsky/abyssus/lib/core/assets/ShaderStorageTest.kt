package net.nevinsky.abyssus.lib.gdx.assets

import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.gdx.io.FileLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ShaderStorageTest {
    private val dir: File = Files.createTempDirectory("shader-storage").toFile()
    private val bare = ShaderStorage()
    private val test = bare.withResources("/shader/test", ShaderStorageTest::class.java)

    /** The host's folder was added first, so it is searched before `/shader/test`. */
    private val layered = bare.withResources("/shader/test_host", ShaderStorageTest::class.java)
        .withResources("/shader/test", ShaderStorageTest::class.java)

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun assetFile(asset: String, file: String, text: String) {
        File(dir, "assets/$asset").mkdirs()
        File(dir, "assets/$asset/$file").writeText(text)
    }

    @Test
    fun defaultsAreUsedWhenNothingMoreSpecificHasTheFile() {
        assertTrue(bare.read("skybox.vert").isNotBlank())
        assertTrue(bare.fragment("hdr_common.glsl", "hdrsky.frag").isNotBlank())
        assertNull(bare.readOrNull("nope.vert"))
    }

    @Test
    fun fragmentsAreJoinedInOrder() {
        assertEquals("float a() { return 1.0; }\n\nvoid main() { gl_FragColor = vec4(a()); }\n", test.fragment("common.glsl", "main.frag"))
    }

    @Test
    fun resourcesOverrideDefaultsFileByFile() {
        val shaders = layered
        assertEquals("host main\n", shaders.read("main.frag"))
        assertEquals("host only\n", shaders.read("host.vert"))
        assertTrue("a file the host lacks still comes from the earlier resources", shaders.read("common.glsl").startsWith("float a()"))
        assertTrue("and then from the defaults", shaders.read("skybox.vert").isNotBlank())
    }

    @Test
    fun anAssetsOwnFileOverridesResourcesAndDefaults() {
        assetFile("sky", "main.frag", "asset main")
        val shaders = layered.withAssets(FileLoader(dir))
        assertEquals("asset main", shaders.read("main.frag", asset = "sky"))
        assertEquals("other assets and plain reads do not see it", "host main\n", shaders.read("main.frag", asset = "other"))
        assertEquals("host main\n", shaders.read("main.frag"))
        assertTrue("an asset without the file falls back", shaders.read("common.glsl", asset = "sky").startsWith("float a()"))
    }

    @Test
    fun anAssetNameCannotLeaveTheAssetsFolder() {
        File(dir, "outside.frag").writeText("secret")
        val shaders = bare.withAssets(FileLoader(dir))
        assertNull(shaders.readOrNull("outside.frag", asset = ".."))
        assertNull(shaders.readOrNull("main.vert", asset = "../x"))
    }

    @Test
    fun aMissingFileNamesEveryPlaceItLookedIn() {
        val error = runCatching { layered.withAssets(FileLoader(dir)).read("nope.vert", asset = "sky") }.exceptionOrNull()
        val message = error?.message.orEmpty()
        assertTrue("got $error", error is IllegalStateException)
        assertTrue(message, "nope.vert" in message && "asset 'sky'" in message && "/shader/test_host" in message && "/shader/test" in message)
    }

    @Test
    fun theBundledDefaultsHaveTheSkyShaders() {
        val shaders = ShaderStorage()
        assertTrue(shaders.read("skybox.vert").isNotBlank())
        assertTrue(shaders.fragment("hdr_common.glsl", "hdrsky.frag").isNotBlank())
    }
}
