package net.nevinsky.abyssus.lib.core.assets

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.model.ModelMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.cube.SkyboxMeta
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrSkyMeta
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSkyMeta
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.lib.core.assets.texture.TextureMeta
import org.junit.Assert.*
import org.junit.Test

class AssetMetaBinderTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)

    @Test fun everyKindBindsItsSettingsAndUnregisteredKindsKeepAMap() {
        val classes = mapOf(
            MetaType.MODEL to ModelMeta::class.java,
            MetaType.CLOUDS to CloudMeta::class.java,
            MetaType.TERRAIN to TerrainMeta::class.java,
            MetaType.SKYBOX to SkyboxMeta::class.java,
            MetaType.SKYBOX_PROCEDURAL to ProceduralSkyMeta::class.java,
            MetaType.SKYBOX_HDR to HdrSkyMeta::class.java,
            MetaType.TEXTURE to TextureMeta::class.java,
            MetaType.PIXMAP_TEXTURE to TextureMeta::class.java,
            MetaType.FOLIAGE to FoliageMeta::class.java,
        )
        val binder = AssetMetaBinder(json)
        for (type in MetaType.entries) {
            val node = json.readObject("""{"format":"abyssus","formatVersion":1,"type":"${type.name}","additional":{"file":"image.png","extension":7}}""")
            val before = node.toString()
            val meta = binder.bind("asset", node)
            assertEquals(type, meta.type)
            assertEquals("asset", meta.name)
            if (type in classes) assertTrue(type.name, classes.getValue(type).isInstance(meta.additional))
            else assertEquals(mapOf("file" to "image.png", "extension" to 7), meta.additional)
            assertEquals(before, node.toString())
        }
        val unknown = binder.bind("future", json.readObject("""{"format":"abyssus","formatVersion":1,"type":"NEW_KIND","additional":{"opaque":true}}"""))
        assertEquals(MetaType.UNKNOWN, unknown.type)
        assertEquals(mapOf("opaque" to true), unknown.additional)
    }

    @Test fun oneInjectedRegistrationBindsTypedSettings() {
        val binder = AssetMetaBinder(json, mapOf(MetaType.MATERIAL to CustomSettings::class.java))
        val meta = binder.bind("custom", json.readObject("""{"format":"abyssus","formatVersion":1,"type":"MATERIAL","additional":{"value":42}}"""))
        assertEquals(CustomSettings(42), meta.additional)
        assertNull(meta.uuid)
        val malformed = json.readObject("""{"format":"abyssus","formatVersion":1,"type":"MATERIAL","uuid":"nope","additional":{"value":42}}""")
        val before = malformed.toString()
        assertThrows(com.fasterxml.jackson.databind.exc.InvalidFormatException::class.java) { binder.bind("custom", malformed) }
        assertEquals(before, malformed.toString())
        assertEquals(1, meta.version)
        assertEquals(0L, meta.lastModified)
    }
}

data class CustomSettings(val value: Int = 0)
