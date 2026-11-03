package net.nevinsky.abyssus.lib.core.assets.terrain

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.texture.TextureLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.imageio.ImageIO
import java.io.DataOutputStream
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files

/** Splat fields name texture assets by `uuid`; the loader resolves them through the project's `AssetIndex`, to folders. */
class TerrainLoaderSplatTest {
    private val dir: File = Files.createTempDirectory("splat").toFile()
    private val files = FileLoader(dir)
    private val metas = testMetaLoader(dir, fileLoader = files)
    private val loader = TerrainLoader(files, metas)

    init {
        GdxNativesLoader.load()
        File(dir, "assets/terr").mkdirs()
        DataOutputStream(File(dir, "assets/terr/terrain.data").outputStream()).use { out -> repeat(4) { out.writeFloat(it.toFloat()) } }
        File(dir, "assets/terr/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":10,"uv":2.0,"splatBase":"$BASE","splatR":"$MISSING"}}"""
        )
    }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun texture(folder: String, uuid: String, image: String = "a.png", file: String? = image, type: String = "TEXTURE") {
        File(dir, "assets/$folder").mkdirs()
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        try { PixmapIO.writePNG(FileHandle(File(dir, "assets/$folder/$image")), pixmap) } finally { pixmap.dispose() }
        val named = file?.let { "\"file\":\"$it\"" } ?: ""
        File(dir, "assets/$folder/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"uuid":"$uuid","type":"$type","additional":{$named}}""")
    }

    /** The splat fields of the terrain `terr` that resolved to a texture folder. */
    private fun splats(): Map<String, String> = loader.prepare("terr")!!.staged.splats

    @Test
    fun aSplatFieldResolvesToTheFolderOfItsTextureUuid() {
        texture("tex", BASE)
        assertEquals(mapOf("splatBase" to "tex"), splats()) // splatR names a uuid no asset has: the layer is left out
    }

    @Test
    fun aTerrainWithoutItsTextureLoadsWithoutIt() {
        assertEquals(emptyMap<String, String>(), splats())
    }

    @Test
    fun aTextureAddedLaterIsFoundWithoutRefreshingAnything() {
        assertEquals(emptyMap<String, String>(), splats())
        texture("tex", BASE)
        assertEquals(mapOf("splatBase" to "tex"), splats())
    }

    @Test
    fun aChangedTextureUuidUnresolvesTheReference() {
        texture("tex", BASE)
        assertEquals(mapOf("splatBase" to "tex"), splats())
        texture("tex", "00000000-0000-0000-0000-000000000009")
        assertEquals(emptyMap<String, String>(), splats())
    }

    @Test
    fun theTerrainNamesItsTextureFoldersAsDependencies() {
        texture("tex", BASE)
        texture("other", MISSING)
        val prepared = loader.prepare("terr")!!.staged
        assertEquals(setOf("tex", "other"), loader.dependencies(prepared))
        loader.discardStaged(prepared)
    }

    @Test
    fun aTerrainWithoutTexturesNeedsNothing() {
        assertEquals(emptySet<String>(), loader.dependencies(loader.prepare("terr")!!.staged))
    }

    /**
     * The splat map an editor stroke writes is an 8-bit RGBA PNG (written through `ImageIO` from an ARGB image, as the editor's
     * codec does). The game decodes it through [TextureLoader]; every channel, including colour under a zero alpha, must arrive intact.
     */
    @Test
    fun aStrokeWrittenSplatImageDecodesToTheSamePixels() {
        val side = 64
        val rgba = ByteArray(side * side * 4) { ((it * 37 + it / 7) and 0xFF).toByte() }
        for (t in 0 until side * side step 5) rgba[t * 4 + 3] = 0 // weights for the A layer at zero beside non-zero R, G, B
        val image = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB)
        for (t in 0 until side * side) {
            val r = rgba[t * 4].toInt() and 0xFF
            val g = rgba[t * 4 + 1].toInt() and 0xFF
            val b = rgba[t * 4 + 2].toInt() and 0xFF
            val a = rgba[t * 4 + 3].toInt() and 0xFF
            image.setRGB(t % side, t / side, (a shl 24) or (r shl 16) or (g shl 8) or b)
        }
        File(dir, "assets/splat").mkdirs()
        ImageIO.write(image, "png", File(dir, "assets/splat/splat.png"))
        File(dir, "assets/splat/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"uuid":"$BASE","type":"TEXTURE","additional":{"file":"splat.png"}}"""
        )
        val pixmap = TextureLoader(files, metas).prepare("splat")!!.staged.release()
        try {
            assertEquals(side, pixmap.width)
            assertEquals(side, pixmap.height)
            val bytes = ByteArray(side * side * 4)
            pixmap.pixels.apply { rewind(); get(bytes) }
            assertEquals(rgba.toList(), bytes.toList())
        } finally {
            pixmap.dispose()
        }
    }

    private companion object {
        const val BASE = "00000000-0000-0000-0000-000000000001"
        const val MISSING = "00000000-0000-0000-0000-000000000002"
    }
}
