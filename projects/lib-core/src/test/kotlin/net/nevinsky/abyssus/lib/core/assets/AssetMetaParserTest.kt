package net.nevinsky.abyssus.lib.core.assets

import java.util.UUID
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class AssetMetaParserTest {
    private val parser = AssetMetaParser(JsonProcessor(NOPLogger.NOP_LOGGER))
    private val text =
        """{"type":"TERRAIN","uuid":"00000000-0000-0000-0000-000000000001","additional":{"terrainFile":"t.bin",
"size":64}}"""

    @Test
    fun readBindsTypeNameAndTypedAdditional() {
        val meta = parser.read("hills", text)
        assertEquals(MetaType.TERRAIN, meta.type)
        assertEquals("hills", meta.name)
        assertEquals(TerrainMeta(terrainFile = "t.bin", size = 64), meta.additional)
    }

    @Test(expected = IllegalArgumentException::class)
    fun readRefusesTextThatIsNoObject() {
        parser.read("x", "[]")
    }

    @Test
    fun withAdditionalKeepsEveryOtherField() {
        val meta = parser.read("hills", text)
        val changed = parser.withAdditional(meta, TerrainMeta(terrainFile = "t.bin", size = 128))
        assertEquals(128, (changed.additional as TerrainMeta).size)
        assertEquals(meta.type, changed.type)
        assertEquals(meta.name, changed.name)
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), changed.uuid)
    }

    @Test
    fun writeThenReadRoundTrips() {
        val meta = parser.read("hills", text)
        assertEquals(meta.additional, parser.read("hills", parser.write(meta)).additional)
        assertEquals(meta.additional, parser.read("hills", parser.write(meta, pretty = true)).additional)
    }
}
