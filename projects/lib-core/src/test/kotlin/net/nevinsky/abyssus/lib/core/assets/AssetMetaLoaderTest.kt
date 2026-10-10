package net.nevinsky.abyssus.lib.core.assets

import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.assets.foliage.FOLIAGE_DATA_FILE
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerKind
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageModelMeta
import net.nevinsky.abyssus.lib.core.assets.sky.cube.SkyboxMeta
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.lib.core.assets.texture.TextureMeta
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
        val loader = AssetMetaLoader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), FileLoader(dir))
        val additional = loader.loadBaseMeta("terr")!!.typedAdditional<TerrainMeta>()
        assertEquals("terrain.data", additional.terrainFile)
        assertEquals(10, additional.size)
        dir.deleteRecursively()
    }

    @Test
    fun anUnknownTypeIsUnknownAndABrokenMetaIsNull() {
        val dir = project {
            it.meta("odd", """{"format":"abyssus","formatVersion":1,"type":"SOMETHING_NEW","additional":{}}""")
            it.meta("bad", "{ not json")
        }
        val loader = AssetMetaLoader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), FileLoader(dir))
        assertEquals(MetaType.UNKNOWN, loader.loadBaseMeta("odd")!!.type)
        assertNull(loader.loadBaseMeta("bad"))
        dir.deleteRecursively()
    }

    @Test
    fun aBrokenMetaIsReportedOncePerRevision() {
        val messages = mutableListOf<String>()
        val dir = project { it.meta("bad", "{ not json") }
        val loader = AssetMetaLoader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), FileLoader(dir), warningsTo(messages))
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
        val loader = AssetMetaLoader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), FileLoader(dir))
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
        val loader = AssetMetaLoader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), FileLoader(dir))
        assertEquals(
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
            loader.loadBaseMeta("with")!!.uuid
        )
        assertNull(loader.loadBaseMeta("without")!!.uuid)
        assertNull(loader.loadBaseMeta("bad"))
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
        val loader = AssetMetaLoader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), FileLoader(dir))
        assertEquals("a.png", loader.loadBaseMeta("a")!!.typedAdditional<TextureMeta>().file)
        assertEquals("b.png", loader.loadBaseMeta("b")!!.typedAdditional<TextureMeta>().file)
        dir.deleteRecursively()
    }

    @Test
    fun theFolderIsTheNameAndASparseMetaGetsDefaults() {
        val dir = project { it.meta("sparse", """{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{}}""") }
        val meta = AssetMetaLoader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), FileLoader(dir)).loadBaseMeta("sparse")!!
        assertEquals("sparse", meta.name)
        assertEquals(1, meta.version)
        assertEquals(MetaType.MODEL, meta.type)
        dir.deleteRecursively()
    }
    @Test
    fun aFoliageMetaBindsEveryField() {
        val dir = project {
            it.meta(
                "foliage_meadow",
                """{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","additional":{"terrain":"terrain_x",""" +
                    """"dataFile":"foliage.data","maskResolution":512,"layers":[""" +
                    """{"id":7,"kind":"DETAIL","models":[{"asset":"tree","weight":3},{"asset":"model_y","weight":1}],""" +
                    """"density":0.25,"scale":{"min":0.5,"max":1.5},"alignToNormal":1.0,"minHeight":5.0,"maxHeight":40.0,""" +
                    """"maxSlope":20.0,"drawDistance":120.0,"seed":-956189611}]}}"""
            )
        }
        val meta = AssetMetaLoader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), FileLoader(dir))
            .loadBaseMeta("foliage_meadow")!!
        assertEquals(MetaType.FOLIAGE, meta.type)
        val additional = meta.typedAdditional<FoliageMeta>()
        assertEquals("terrain_x", additional.terrain)
        assertEquals("foliage.data", additional.dataFileName())
        assertEquals(512, additional.maskResolution)
        val layer = additional.layers.single()
        assertEquals(7, layer.id)
        assertEquals(FoliageLayerKind.DETAIL, FoliageLayerKind.valueOf(layer.kind))
        assertEquals(listOf(FoliageModelMeta("tree", 3f), FoliageModelMeta("model_y", 1f)), layer.models)
        assertEquals(0.25f, layer.density, 0f)
        assertEquals(0.5f, layer.scale.min, 0f)
        assertEquals(1.5f, layer.scale.max, 0f)
        assertEquals(1f, layer.alignToNormal, 0f)
        assertEquals(5f, layer.minHeight!!, 0f)
        assertEquals(40f, layer.maxHeight!!, 0f)
        assertEquals(20f, layer.maxSlope!!, 0f)
        assertEquals(120f, layer.drawDistance, 0f)
        assertEquals(-956189611, layer.seed)
        dir.deleteRecursively()
    }

    @Test
    fun omittedFoliageMembersTakeTheirDefaults() {
        val dir = project {
            it.meta(
                "foliage_empty",
                """{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","additional":{"terrain":"terr"}}"""
            )
        }
        val additional = AssetMetaLoader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), FileLoader(dir))
            .loadBaseMeta("foliage_empty")!!.typedAdditional<FoliageMeta>()
        assertEquals("terr", additional.terrain)
        assertEquals(FOLIAGE_DATA_FILE, additional.dataFileName())
        assertEquals(256, additional.maskResolution)
        assertEquals(emptyList<FoliageLayerMeta>(), additional.layers)
        val layer = FoliageLayerMeta()
        assertEquals(FoliageLayerKind.OBJECT.name, layer.kind)
        assertEquals(0.05f, layer.density, 0f)
        assertEquals(0.8f, layer.scale.min, 0f)
        assertEquals(1.2f, layer.scale.max, 0f)
        assertEquals(0.5f, layer.alignToNormal, 0f)
        assertNull(layer.minHeight)
        assertNull(layer.maxHeight)
        assertNull(layer.maxSlope)
        assertEquals(80f, layer.drawDistance, 0f)
        assertEquals(0, layer.seed)
        dir.deleteRecursively()
    }

    @Test
    fun anUnknownFoliageLayerMemberDoesNotFailBinding() {
        val dir = project {
            it.meta(
                "foliage_note",
                """{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","additional":{"terrain":"terr","layers":[""" +
                    """{"id":1,"kind":"OBJECT","density":0.01,"note":"north slope"}]}}"""
            )
        }
        val additional = AssetMetaLoader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), FileLoader(dir))
            .loadBaseMeta("foliage_note")!!.typedAdditional<FoliageMeta>()
        assertEquals(1, additional.layers.size)
        assertEquals(1, additional.layers[0].id)
        assertEquals(0.01f, additional.layers[0].density, 0f)
        dir.deleteRecursively()
    }

    @Test
    fun aMissingAdditionalBlockIsReportedWithoutChangingTheFile() {
        val text = """{"format":"abyssus","formatVersion":1,"type":"MODEL"}"""
        val dir = project { it.meta("missing", text) }
        try {
            val messages = mutableListOf<String>()
            val loader = AssetMetaLoader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), FileLoader(dir), warningsTo(messages))
            assertNull(loader.loadBaseMeta("missing"))
            assertNull(loader.loadBaseMeta("missing"))
            assertEquals(1, messages.size)
            assertTrue(messages.single().contains("additional"))
            assertEquals(text, File(dir, "assets/missing/meta.json").readText())
        } finally { dir.deleteRecursively() }
    }

}
