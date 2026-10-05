package net.nevinsky.abyssus.core.assets.texture

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.assets.testMetaLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TextureLoaderTest {
    private val dir: File = Files.createTempDirectory("texture").toFile()
    private val files = FileLoader(dir)
    private val loader = TextureLoader(files, testMetaLoader(dir, fileLoader = files))

    init {
        GdxNativesLoader.load()
    }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun asset(folder: String, type: String = "TEXTURE", file: String? = "a.png", write: Boolean = true) {
        File(dir, "assets/$folder").mkdirs()
        if (write) {
            val pixmap = Pixmap(2, 3, Pixmap.Format.RGBA8888)
            try { PixmapIO.writePNG(FileHandle(File(dir, "assets/$folder/a.png")), pixmap) } finally { pixmap.dispose() }
        }
        val named = file?.let { "\"file\":\"$it\"" } ?: ""
        File(dir, "assets/$folder/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"type":"$type","additional":{$named}}""")
    }

    @Test
    fun decodesTheImageOfATextureAsset() {
        asset("tex")
        val pixmap = loader.prepare("tex")!!.release()
        try {
            assertEquals(2, pixmap.width)
            assertEquals(3, pixmap.height)
        } finally {
            pixmap.dispose()
        }
    }

    @Test
    fun aPixmapTextureIsATexture() {
        asset("tex", type = "PIXMAP_TEXTURE")
        loader.prepare("tex")!!.dispose()
    }

    @Test
    fun otherKindsAndUnknownAssetsPrepareNothing() {
        asset("model", type = "MODEL")
        assertNull(loader.prepare("model"))
        assertNull(loader.prepare("nope"))
    }

    @Test
    fun aMissingFileOrAnUndecodableImageFails() {
        asset("nofile", file = null)
        asset("gone", file = "gone.png", write = false)
        assertThrows(IllegalArgumentException::class.java) { loader.prepare("nofile") }
        assertThrows(IllegalStateException::class.java) { loader.prepare("gone") }
        asset("broken")
        File(dir, "assets/broken/a.png").writeText("not an image")
        assertThrows(Exception::class.java) { loader.prepare("broken") }
    }

    @Test
    fun releaseHandsOverTheImageOnceAndDisposeIsIdempotent() {
        asset("tex")
        val prepared = loader.prepare("tex")!!
        prepared.release().dispose()
        assertThrows(IllegalStateException::class.java) { prepared.release() }
        prepared.dispose()
        prepared.dispose()
        val other = loader.prepare("tex")!!
        loader.discard(other)
        loader.discard(other)
    }
}
