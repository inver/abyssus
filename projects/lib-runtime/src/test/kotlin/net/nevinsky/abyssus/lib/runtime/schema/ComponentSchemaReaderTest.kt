/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.runtime.schema

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.runtime.schema.BUILT_IN_COMPONENTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentSchemaReaderTest {
    private val reader = ComponentSchemaReader()

    @Test fun planeSchemaHasEveryFieldWithTypeDefaultLimitsAndGroup() {
        val schema = reader.read(PlaneComponent::class.java)
        assertEquals("PlaneComponent", schema.name)
        assertEquals(PlaneComponent::class.java.name, schema.className)
        assertEquals("Plane", schema.label)
        val fields = schema.fields.associateBy { it.name }
        assertEquals(listOf("lineLength", "fuelSeconds", "hasTipWeight", "name", "kind", "leadout", "paint", "pilot", "model"), schema.fields.map { it.name })
        assertEquals(SchemaField("lineLength", "Line length", FieldType.DECIMAL, 18f, "Lines", 5.0, 30.0), fields["lineLength"])
        assertEquals(SchemaField("fuelSeconds", "Fuel seconds", FieldType.WHOLE, 60, min = 0.0), fields["fuelSeconds"])
        assertEquals(SchemaField("hasTipWeight", "Tip weight", FieldType.BOOLEAN, true), fields["hasTipWeight"])
        assertEquals(SchemaField("name", "Name", FieldType.TEXT, ""), fields["name"])
        assertEquals(SchemaField("kind", "Kind", FieldType.CHOICE, "TRAINER", choices = listOf("TRAINER", "STUNT", "SPEED")), fields["kind"])
        assertEquals(SchemaField("leadout", "Leadout", FieldType.VECTOR, SchemaVector(0f, 0f, -0.3f), "Lines"), fields["leadout"])
        assertEquals(SchemaField("paint", "Paint", FieldType.COLOR, SchemaColor(1f, 1f, 1f, 1f)), fields["paint"])
        assertEquals(SchemaField("pilot", "Pilot", FieldType.ENTITY, -1), fields["pilot"])
        assertEquals(SchemaField("model", "Model", FieldType.ASSET, "", assetType = "MODEL"), fields["model"])
        assertNull(fields["speed"])
    }

    @SceneComponent("NameComponent")
    class TakenName : Component

    @Test fun aTakenNameFailsNamingTheComponent() {
        val error = assertThrows(ComponentRegistrationException::class.java) {
            reader.readAll(listOf(TakenName::class.java), BUILT_IN_COMPONENTS)
        }
        assertTrue(error.message, error.message!!.contains("NameComponent is a built-in component"))
        val twice = assertThrows(ComponentRegistrationException::class.java) {
            reader.readAll(listOf(PlaneComponent::class.java, PlaneComponent::class.java), BUILT_IN_COMPONENTS)
        }
        assertTrue(twice.message, twice.message!!.contains("PlaneComponent is registered twice"))
    }

    @SceneComponent("RouteComponent")
    class WithList : Component {
        @Field var points: List<Float> = emptyList()
    }

    @Test fun aListFieldFailsNamingTheField() {
        val error = assertThrows(ComponentRegistrationException::class.java) { reader.read(WithList::class.java) }
        assertTrue(error.message, error.message!!.contains("RouteComponent field points has the unsupported type List"))
    }

    @SceneComponent("WingComponent")
    class NoDefaults(@Field var span: Float) : Component

    @Test fun aClassWithoutANoArgumentConstructorFails() {
        val error = assertThrows(ComponentRegistrationException::class.java) { reader.read(NoDefaults::class.java) }
        assertTrue(error.message, error.message!!.contains("WingComponent has no no-argument constructor"))
    }
}
