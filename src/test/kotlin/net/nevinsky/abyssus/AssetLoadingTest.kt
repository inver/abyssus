package net.nevinsky.abyssus

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.assets.MetaType
import net.nevinsky.abyssus.core.assets.model.ModelMeta
import net.nevinsky.abyssus.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.core.assets.loading.CompositeAssetLoader
import net.nevinsky.abyssus.testing.warningsTo
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AssetLoadingTest {
    @Test fun savedAndUnsavedMetadataBindTheFixtureAndMalformedValuesTheSameWay() {
        val dir = Files.createTempDirectory("meta-parity").toFile()
        try {
            val json = JsonProcessor()
            val files = FileLoader(dir)
            val name = "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb"
            val fixture = File("src/test/testData/project/Untitled/assets/$name/meta.json").readText()
            val file = File(dir, "assets/$name/meta.json").absoluteFile.also { it.parentFile.mkdirs() }
            val capture = object : AssetLoader<AssetMeta<Any>, Disposable> {
                override fun prepare(name: String): AssetMeta<Any>? = error("Metadata must be supplied")
                override fun loadPrepared(meta: AssetMeta<Any>) = meta
                override fun build(prepared: AssetMeta<Any>, assets: BuiltAssets): Disposable = error("No GL needed")
                override fun discard(prepared: AssetMeta<Any>) = Unit
            }
            val variants = listOf(fixture, "{ not json", fixture.replace("\"MODEL\"", "\"FUTURE_KIND\""), fixture.replace("29e9be61-6594-4f82-a6cf-44ccf09f71fb", "nope"))
            for ((index, text) in variants.withIndex()) {
                file.writeText(text)
                val saved = AssetMetaLoader(json, files).loadBaseMeta(name)
                val delegate = CompositeAssetLoader(AssetMetaLoader(json, files), MetaType.entries.associateWith { capture })
                val unsaved = UnsavedMetaLoader(json, files, { mapOf(file to text) }, delegate).prepare(name)?.value as AssetMeta<*>?
                assertEquals("variant $index", saved?.name, unsaved?.name)
                assertEquals("variant $index", saved?.type, unsaved?.type)
                assertEquals("variant $index", saved?.uuid, unsaved?.uuid)
                assertEquals("variant $index", saved?.additional, unsaved?.additional)
                assertEquals(saved?.version, unsaved?.version)
                assertEquals(saved?.lastModified, unsaved?.lastModified)
                when (index) {
                    0 -> {
                        assertEquals(MetaType.MODEL, saved!!.type)
                        assertEquals(ModelMeta("model.gltf", ModelMeta.Format.GLTF, true, emptyList<String>()), saved.additional)
                        assertEquals(java.util.UUID.fromString("29e9be61-6594-4f82-a6cf-44ccf09f71fb"), saved.uuid)
                    }
                    1 -> assertNull(saved)
                    2 -> { assertEquals(MetaType.UNKNOWN, saved!!.type); assertTrue(saved.additional is Map<*, *>) }
                    3 -> { assertEquals(MetaType.MODEL, saved!!.type); assertNull(saved.uuid) }
                }
                assertEquals(text, file.readText())
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun unsavedUnsupportedMetadataCannotFallBackToSavedOrReachAssetPreparation() {
        val dir = Files.createTempDirectory("unsaved-meta").toFile()
        try {
            val json = JsonProcessor()
            val files = FileLoader(dir)
            val file = File(dir, "assets/model/meta.json").absoluteFile.also { it.parentFile.mkdirs() }
            val saved = """{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{}}"""
            file.writeText(saved)
            var preparations = 0
            val model = object : AssetLoader<AssetMeta<Any>, Disposable> {
                override fun prepare(name: String): AssetMeta<Any>? = error("Metadata must be supplied")
                override fun loadPrepared(meta: AssetMeta<Any>): AssetMeta<Any> { preparations++; return meta }
                override fun build(prepared: AssetMeta<Any>, assets: BuiltAssets): Disposable = error("No GL needed")
                override fun discard(prepared: AssetMeta<Any>) = Unit
            }
            val delegate = CompositeAssetLoader(AssetMetaLoader(json, files), mapOf(MetaType.MODEL to model))
            var unsaved = emptyMap<File, String>()
            val messages = mutableListOf<String>()
            val loader = UnsavedMetaLoader(json, files, { unsaved }, delegate, warningsTo(messages))
            assertEquals("model", (loader.prepare("model")!!.value as AssetMeta<*>).name)
            assertEquals(1, preparations)
            for (invalid in listOf("""{"type":"MODEL"}""", saved.replace("abyssus", "foreign"), saved.replace("\"formatVersion\":1", "\"formatVersion\":1.0"), saved.replace("\"formatVersion\":1", "\"formatVersion\":2"))) {
                unsaved = mapOf(file to invalid)
                assertNull(loader.prepare("model"))
                assertEquals(1, preparations)
                assertEquals(saved, file.readText())
                val unsavedReason = messages.last().substringAfter("Unsupported asset format:")
                file.writeText(invalid)
                val savedMessages = mutableListOf<String>()
                assertNull(AssetMetaLoader(json, files, warningsTo(savedMessages)).loadBaseMeta("model"))
                assertEquals(unsavedReason, savedMessages.single().substringAfter("Unsupported asset format:"))
                assertEquals(invalid, file.readText())
                file.writeText(saved)
            }
        } finally { dir.deleteRecursively() }
    }
}
