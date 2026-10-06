/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics

import net.nevinsky.abyssus.lib.runtime.schema.ComponentSchemaReader
import net.nevinsky.abyssus.lib.runtime.schema.SchemaJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsComponentsTest {
    private val schemas = ComponentSchemaReader().readAll(PhysicsComponents().components(), emptySet()).associateBy { it.name }
    private val json = SchemaJson()

    private fun defaults(name: String) = schemas.getValue(name).fields.associate { it.name to it.default }
    private fun field(component: String, name: String) = schemas.getValue(component).fields.single { it.name == name }

    @Test
    fun registersTheThreeComponentsUnderTheirShortNames() {
        assertEquals(listOf("RigidBodyComponent", "ColliderComponent", "ConstraintComponent"), schemas.keys.toList())
    }

    @Test
    fun defaultsAreWrittenAsAnEmptyObject() {
        for (name in schemas.keys) assertEquals(name, "{}", json.encode(schemas.getValue(name), defaults(name)).toString())
        val body = defaults("RigidBodyComponent")
        assertEquals("DYNAMIC", body["motionType"])
        assertEquals(1f, body["mass"])
        assertEquals(0.2f, body["friction"])
        assertEquals(0f, body["restitution"])
        assertEquals(0.05f, body["linearDamping"])
        assertEquals(0.05f, body["angularDamping"])
        assertEquals(1f, body["gravityFactor"])
        assertEquals("BOX", defaults("ColliderComponent")["shape"])
        assertEquals(-1, defaults("ConstraintComponent")["other"])
        assertEquals("DISTANCE", defaults("ConstraintComponent")["kind"])
    }

    @Test
    fun aRopeWritesOnlyTheOtherEntityAndItsLength() {
        val rope = defaults("ConstraintComponent") + mapOf("kind" to "DISTANCE", "other" to 0, "minDistance" to 0f, "maxDistance" to 3f)
        assertEquals("""{"other":0,"maxDistance":3}""", json.encode(schemas.getValue("ConstraintComponent"), rope).toString())
    }

    @Test
    fun sizesMustBeAboveZeroAndDistancesNotBelowIt() {
        for ((component, name) in listOf(
            "RigidBodyComponent" to "mass", "ColliderComponent" to "halfExtents",
            "ColliderComponent" to "radius", "ColliderComponent" to "halfHeight",
        )) {
            val f = field(component, name)
            assertEquals(name, 0.0, f.min)
            assertTrue(name, f.minExclusive)
        }
        for (name in listOf("minDistance", "maxDistance")) {
            val f = field("ConstraintComponent", name)
            assertEquals(name, 0.0, f.min)
            assertEquals(name, false, f.minExclusive)
        }
        assertNull(field("ColliderComponent", "offset").min)
    }
}
