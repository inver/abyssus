package net.nevinsky.abyssus.lib.gdx.assets

import net.nevinsky.abyssus.lib.gdx.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.assets.sky.cube.SkyboxMeta
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.lib.gdx.assets.texture.TextureMeta
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AssetMetaLoaderTest {
    private fun project(block: (File) -> Unit): File = Files.createTempDirectory("proj").toFile().also(block)

    private fun File.meta(folder: String, text: String) {
        File(this, "assets/$folder").mkdirs()
        File(this, "assets/$folder/meta.json").writeText(text)
    }

    @Test
    fun readsTheMetaOfAFixtureAsset() {
        val meta = testMetaLoader(testProject("Untitled")).loadBaseMeta("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb")!!
        assertEquals(MetaType.MODEL, meta.type)
    }

    @Test
    fun blankMissingAndOutOfFolderNamesHaveNoMeta() {
        val loader = testMetaLoader(testProject("Untitled"))
        for (name in listOf(null, "", " ", "model_nope", "../Untitled")) assertNull(name, loader.loadBaseMeta(name))
    }

    @Test
    fun terrainAdditionalIsBoundToItsTypedBlock() {
        val dir = project {
            it.meta(
                "terr",
                """{"format":"abyssus","formatVersion":1,"type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":10,"uv":2.0}}"""
            )
        }
        val loader = AssetMetaLoader(JsonProcessor(), FileLoader(dir))
        val additional = loader.loadBaseMeta("terr")!!.typedAdditional<TerrainMeta>()
        assertEquals("terrain.data", additional.terrainFile)
        assertEquals(10, additional.size)
        dir.deleteRecursively()
    }

    @Test
    fun anUnknownTypeIsUnknownAndABrokenMetaIsNull() {
        val dir = project {
            it.meta("odd", """{"format":"abyssus","formatVersion":1,"type":"SOMETHING_NEW"}""")
            it.meta("bad", "{ not json")
        }
        val loader = AssetMetaLoader(JsonProcessor(), FileLoader(dir))
        assertEquals(MetaType.UNKNOWN, loader.loadBaseMeta("odd")!!.type)
        assertNull(loader.loadBaseMeta("bad"))
        dir.deleteRecursively()
    }

    @Test
    fun aBrokenMetaIsReportedOncePerRevision() {
        val messages = mutableListOf<String>()
        val dir = project { it.meta("bad", "{ not json") }
        val loader = AssetMetaLoader(JsonProcessor(), FileLoader(dir), warningsTo(messages))
        assertNull(loader.loadBaseMeta("bad"))
        assertNull(loader.loadBaseMeta("bad"))
        assertEquals(1, messages.size)
        assertTrue(messages.single().contains("meta.json"))
        dir.deleteRecursively()
    }

    @Test
    fun aChangedMetaIsReadAgainAndAnUnchangedOneIsServedFromTheCache() {
        val dir = project {
            it.meta(
                "sky",
                """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"type":"SKYBOX","additional":{"top":"t.png"}}"""
            )
        }
        val file = File(dir, "assets/sky/meta.json")
        val loader = AssetMetaLoader(JsonProcessor(), FileLoader(dir))
        assertEquals("t.png", loader.loadBaseMeta("sky")!!.typedAdditional<SkyboxMeta>().top)
        // same length and modification time: the stamp is unchanged, so the cached read answers
        val stamp = file.lastModified()
        val text = file.readText()
        file.writeText(text.replace("t.png", "u.png"))
        file.setLastModified(stamp)
        assertEquals("t.png", loader.loadBaseMeta("sky")!!.typedAdditional<SkyboxMeta>().top)
        file.writeText(text.replace("t.png", "other.png"))
        assertEquals("other.png", loader.loadBaseMeta("sky")!!.typedAdditional<SkyboxMeta>().top)
        dir.deleteRecursively()
    }

    @Test
    fun theUuidIsNullWhenTheMetaDeclaresNone() {
        val dir = project {
            it.meta(
                "with",
                """{"format":"abyssus","formatVersion":1,"uuid":"00000000-0000-0000-0000-000000000001","type":"MODEL","additional":{}}"""
            )
            it.meta("without", """{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{}}""")
            it.meta("bad", """{"format":"abyssus","formatVersion":1,"uuid":"nope","type":"MODEL","additional":{}}""")
        }
        val loader = AssetMetaLoader(JsonProcessor(), FileLoader(dir))
        assertEquals(
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
            loader.loadBaseMeta("with")!!.uuid
        )
        assertNull(loader.loadBaseMeta("without")!!.uuid)
        assertNull(loader.loadBaseMeta("bad")!!.uuid)
        dir.deleteRecursively()
    }

    @Test
    fun textureMetasBindTheirFile() {
        val dir = project {
            it.meta("a", """{"format":"abyssus","formatVersion":1,"type":"TEXTURE","additional":{"file":"a.png"}}""")
            it.meta(
                "b",
                """{"format":"abyssus","formatVersion":1,"type":"PIXMAP_TEXTURE","additional":{"file":"b.png"}}"""
            )
        }
        val loader = AssetMetaLoader(JsonProcessor(), FileLoader(dir))
        assertEquals("a.png", loader.loadBaseMeta("a")!!.typedAdditional<TextureMeta>().file)
        assertEquals("b.png", loader.loadBaseMeta("b")!!.typedAdditional<TextureMeta>().file)
        dir.deleteRecursively()
    }

    @Test
    fun theFolderIsTheNameAndASparseMetaGetsDefaults() {
        val dir = project { it.meta("sparse", """{"format":"abyssus","formatVersion":1,"type":"MODEL"}""") }
        val meta = AssetMetaLoader(JsonProcessor(), FileLoader(dir)).loadBaseMeta("sparse")!!
        assertEquals("sparse", meta.name)
        assertEquals(1, meta.version)
        assertEquals(MetaType.MODEL, meta.type)
        dir.deleteRecursively()
    }
}
