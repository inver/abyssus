package net.nevinsky.abyssus.runtime.schema

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.core.JsonProcessor
import org.junit.Assert.*
import org.junit.Test

class FieldTypeHandlerPrototypeTest {
    private val json = JsonProcessor()

    @Test fun decimalDefaultsNumbersAndLimitFailuresMatchTheCurrentContract() {
        val handler = DecimalHandlerPrototype()
        val field = SchemaField("speed", "Speed", FieldType.DECIMAL, 2f, min = 1.0, max = 5.0)
        assertEquals(0f, handler.defaultFor())
        assertEquals(3f, handler.fromJava(3f))
        assertEquals("3", handler.encode(3f).toString())
        assertEquals(SchemaJson.Decoded.Value(3f), handler.decode(field, json.mapper.readTree("3")))
        assertEquals(SchemaJson.Decoded.Unusable("0 is below the minimum 1"), handler.decode(field, json.mapper.readTree("0")))
        assertEquals(SchemaJson.Decoded.Unusable("6 is above the maximum 5"), handler.decode(field, json.mapper.readTree("6")))
        assertEquals(SchemaJson.Decoded.Unusable("true is not a decimal number"), handler.decode(field, json.mapper.readTree("true")))
        assertEquals(SchemaJson.Decoded.Unusable("1 is not greater than the minimum 1"), handler.decode(field.copy(minExclusive = true), json.mapper.readTree("1")))
    }

    @Test fun vectorDefaultsPartialAxesAndAxisLimitsMatchTheCurrentContract() {
        val handler = VectorHandlerPrototype()
        val field = SchemaField("position", "Position", FieldType.VECTOR, SchemaVector(1f, 2f, 3f), max = 5.0)
        assertEquals(listOf("x", "y", "z"), handler.parts)
        assertEquals(SchemaVector(0f, 0f, 0f), handler.defaultFor())
        assertEquals(SchemaVector(2f, 3f, 4f), handler.fromJava(Vector3(2f, 3f, 4f)))
        assertEquals("{\"x\":1,\"y\":2,\"z\":3}", handler.encode(field.default).toString())
        assertEquals(SchemaJson.Decoded.Value(SchemaVector(4f, 2f, 3f)), handler.decode(field, json.readObject("""{"x":4}""")))
        assertEquals(SchemaJson.Decoded.Unusable("y: 6 is above the maximum 5"), handler.decode(field, json.readObject("""{"y":6}""")))
        assertEquals(SchemaJson.Decoded.Unusable("{\"z\":false} is not an {x, y, z} vector"), handler.decode(field, json.readObject("""{"z":false}""")))
    }
}
