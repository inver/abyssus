package net.nevinsky.abyssus.lib.gdx.assets.terrain

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.gdx.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.assets.testMetaLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.DataOutputStream
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

    private companion object {
        const val BASE = "00000000-0000-0000-0000-000000000001"
        const val MISSING = "00000000-0000-0000-0000-000000000002"
    }
}
