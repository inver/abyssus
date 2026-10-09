package net.nevinsky.abyssus.lib.gdx

import net.nevinsky.abyssus.lib.core.io.JsonProcessor

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import net.nevinsky.abyssus.lib.gdx.assets.AssetMeta
import net.nevinsky.abyssus.lib.gdx.assets.MetaType
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonProcessorTest {
    private val json = JsonProcessor()

    enum class Kind { @JsonEnumDefaultValue UNKNOWN, MODEL, TERRAIN }

    data class Meta(val version: Int, val type: Kind, val name: String? = null)

    @Test
    fun skipsUnknownProperties() {
        assertEquals(Meta(1, Kind.MODEL), json.parse("""{"format":"abyssus","formatVersion":1,"version":1,"type":"MODEL","extra":{"a":[1,2]}}""", Meta::class.java))
    }

    @Test
    fun anUnknownEnumValueTakesTheDefault() {
        assertEquals(Kind.UNKNOWN, json.parse("""{"format":"abyssus","formatVersion":1,"version":1,"type":"WIDGET"}""", Meta::class.java).type)
    }

    @Test
    fun prettyPrintingKeepsDeclarationOrderAndTheFileStyle() {
        assertEquals("{\n  \"version\": 2,\n  \"type\": \"TERRAIN\",\n  \"name\": \"t\"\n}\n", json.pretty(Meta(2, Kind.TERRAIN, "t")))
    }

    @Test
    fun parsesAMetaOfEveryTypeAndAnUnknownOne() {
        for (type in MetaType.entries) {
            val meta = json.parse("""{"format":"abyssus","formatVersion":1,"version":1,"lastModified":5,"type":"$type","additional":{}}""", AssetMeta::class.java)
            assertEquals(type, meta.type)
        }
        val unknown = json.parse("""{"format":"abyssus","formatVersion":1,"version":1,"lastModified":5,"type":"WIDGET","additional":{}}""", AssetMeta::class.java)
        assertEquals(MetaType.UNKNOWN, unknown.type)
    }

    @Test
    fun instancesShareNothing() {
        // two processors are independent objects; nothing is looked up globally
        assertEquals(json.parse("""{"format":"abyssus","formatVersion":1,"version":3,"type":"MODEL"}""", Meta::class.java), JsonProcessor().parse("""{"format":"abyssus","formatVersion":1,"version":3,"type":"MODEL"}""", Meta::class.java))
    }
}
