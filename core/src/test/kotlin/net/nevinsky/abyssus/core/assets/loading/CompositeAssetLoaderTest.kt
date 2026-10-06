package net.nevinsky.abyssus.core.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.core.io.FileLoader
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.MetaType
import net.nevinsky.abyssus.core.assets.testMetaLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CompositeAssetLoaderTest {
    private val dir: File = Files.createTempDirectory("composite").toFile()
    private val files = FileLoader(dir)
    private val metas = testMetaLoader(dir, fileLoader = files)

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private class Res(val kind: String) : Disposable {
        var disposed = false
        override fun dispose() {
            disposed = true
        }
    }

    /** Prepared data is "kind:name"; every step is recorded as "kind.step". */
    private class Fake(val kind: String, val log: MutableList<String>, val needs: Set<String> = emptySet()) : AssetLoader<String, Res> {
        override fun loadPrepared(meta: AssetMeta<Any>): String? = "$kind:${meta.name}".also { log += "$kind.prepare" }
        override fun prepare(name: String): String? = error("the composite prepares through the meta")
        override fun upload(prepared: String): Boolean { log += "$kind.upload"; return true }
        override fun dependencies(prepared: String): Set<String> = needs
        override fun build(prepared: String, assets: BuiltAssets): Res { log += "$kind.build"; return Res(prepared) }
        override fun discard(prepared: String) { log += "$kind.discard" }
    }

    private fun asset(folder: String, type: String) {
        File(dir, "assets/$folder").mkdirs()
        File(dir, "assets/$folder/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"type":"$type","additional":{}}""")
    }

    @Test
    fun anAssetGoesToTheLoaderOfItsMetaType() {
        val log = mutableListOf<String>()
        val loader = CompositeAssetLoader(metas, mapOf(MetaType.MODEL to Fake("model", log), MetaType.TERRAIN to Fake("terrain", log, setOf("tex"))))
        asset("m", "MODEL"); asset("t", "TERRAIN")
        val model = loader.prepare("m")!!
        val terrain = loader.prepare("t")!!
        assertEquals(emptySet<String>(), loader.dependencies(model))
        assertEquals(setOf("tex"), loader.dependencies(terrain))
        assertTrue(loader.upload(terrain))
        val built = loader.build(model, BuiltAssets { null }) as Res
        assertEquals("model:m", built.kind)
        loader.discard(terrain)
        assertEquals(listOf("model.prepare", "terrain.prepare", "terrain.upload", "model.build", "terrain.discard"), log)
    }

    @Test
    fun aTypeWithoutALoaderAndAnUnknownAssetPrepareNothing() {
        val log = mutableListOf<String>()
        val loader = CompositeAssetLoader(metas, mapOf(MetaType.MODEL to Fake("model", log)))
        asset("t", "TERRAIN")
        assertNull(loader.prepare("t"))
        assertNull(loader.prepare("nope"))
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun oneStorageOverTheCompositeBuildsEveryKindAndLoadsDependenciesFirst() {
        val log = mutableListOf<String>()
        val loader = CompositeAssetLoader(
            metas,
            mapOf(MetaType.TEXTURE to Fake("texture", log), MetaType.TERRAIN to Fake("terrain", log, setOf("tex"))),
        )
        asset("tex", "TEXTURE"); asset("terr", "TERRAIN")
        val storage = AssetStorage(java.util.concurrent.Executor(Runnable::run), loader, org.slf4j.helpers.NOPLogger.NOP_LOGGER)
        storage.request("terr")
        repeat(5) { storage.pump(2) }
        assertEquals(listOf("texture.build", "terrain.build"), log.filter { it.endsWith(".build") })
        assertEquals("terrain:terr", storage.getAs<Res>("terr")!!.kind)
        assertSame(storage.get("tex"), storage.get("tex"))
        assertEquals("texture:tex", storage.getAs<Res>("tex")!!.kind)
    }
}
