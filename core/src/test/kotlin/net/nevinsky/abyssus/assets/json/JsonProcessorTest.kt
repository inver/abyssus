package net.nevinsky.abyssus.assets.json

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import net.nevinsky.abyssus.assets.files.MetaBase
import net.nevinsky.abyssus.assets.files.MetaType
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonProcessorTest {
    private val json = JsonProcessor()

    enum class Kind { @JsonEnumDefaultValue UNKNOWN, MODEL, TERRAIN }

    data class Meta(val version: Int, val type: Kind, val name: String? = null)

    @Test
    fun skipsUnknownProperties() {
        assertEquals(Meta(1, Kind.MODEL), json.parse("""{"version":1,"type":"MODEL","extra":{"a":[1,2]}}""", Meta::class.java))
    }

    @Test
    fun anUnknownEnumValueTakesTheDefault() {
        assertEquals(Kind.UNKNOWN, json.parse("""{"version":1,"type":"WIDGET"}""", Meta::class.java).type)
    }

    @Test
    fun prettyPrintingKeepsDeclarationOrderAndTheFileStyle() {
        assertEquals("{\n  \"version\": 2,\n  \"type\": \"TERRAIN\",\n  \"name\": \"t\"\n}\n", json.pretty(Meta(2, Kind.TERRAIN, "t")))
    }

    @Test
    fun parsesAMetaOfEveryTypeAndAnUnknownOne() {
        for (type in MetaType.entries) {
            val meta = json.parse("""{"version":1,"lastModified":5,"type":"$type","additional":{}}""", MetaBase::class.java)
            assertEquals(type, meta.type)
        }
        val unknown = json.parse("""{"version":1,"lastModified":5,"type":"WIDGET","additional":{}}""", MetaBase::class.java)
        assertEquals(MetaType.UNKNOWN, unknown.type)
    }

    @Test
    fun instancesShareNothing() {
        // two processors are independent objects; nothing is looked up globally
        assertEquals(json.parse("""{"version":3,"type":"MODEL"}""", Meta::class.java), JsonProcessor().parse("""{"version":3,"type":"MODEL"}""", Meta::class.java))
    }
}
