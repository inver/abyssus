/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.document


import org.junit.Assert.*
import org.junit.Test

class SceneRaySettingsTest {
    private val codec = SceneRaySettingsCodec()
    private fun root(fields: String = "") = SceneJson().parseObject("""{"format":"abyssus","formatVersion":1$fields}""")

    @Test fun absentFieldsReadDefaultsWithoutWriting() {
        val r = root(); val before = SceneJson().compact(r)
        assertEquals(SceneRaySettings(), codec.read(r).settings)
        assertTrue(codec.read(r).errors.isEmpty())
        assertEquals(before, SceneJson().compact(r))
    }
    @Test fun allLimitsAreAcceptedAndAdjacentValuesRejected() {
        for (field in SceneRayField.entries) {
            for (value in listOf(field.minimum, field.maximum)) {
                val r = root(",\"rayTracing\":{\"${field.key}\":$value}")
                assertEquals(value, codec.read(r).values[field])
                assertTrue(codec.read(r).errors.isEmpty())
            }
            for (value in listOf(field.minimum - 1, field.maximum + 1)) {
                assertTrue(codec.read(root(",\"rayTracing\":{\"${field.key}\":$value}")).errors.containsKey(field.key))
            }
        }
    }
    @Test fun malformedPresentFieldsNeverBecomeValidDefaults() {
        for (field in SceneRayField.entries) for (value in listOf("null", "1.0", "\"1\"", "true", "[]", "999999999999999999999")) {
            val state = codec.read(root(",\"rayTracing\":{\"${field.key}\":$value}"))
            assertNull(state.settings); assertTrue(state.errors.containsKey(field.key))
        }
        for (value in listOf("null", "[]", "7")) assertNull(codec.read(root(",\"rayTracing\":$value")).settings)
    }
    @Test fun editsPruneDefaultsAndOnlyEmptyContainers() {
        val r = root(",\"rayTracing\":{\"maxReflectionBounces\":3,\"future\":2.500},\"other\":-0.00")
        assertEquals(RayDataEdit.Changed, codec.edit(r, SceneRayField.REFLECTIONS, r["rayTracing"]["maxReflectionBounces"], "1"))
        assertEquals("""{"format":"abyssus","formatVersion":1,"rayTracing":{"future":2.500},"other":-0.00}""", SceneJson().compact(r))
        val empty = root(",\"rayTracing\":{\"maxRefractionBounces\":2}")
        assertEquals(RayDataEdit.Changed, codec.edit(empty, SceneRayField.REFRACTIONS, empty["rayTracing"]["maxRefractionBounces"], "0"))
        assertFalse(empty.has("rayTracing"))
    }
    @Test fun staleInvalidAndEqualEditsWriteNothing() {
        val r = root(",\"rayTracing\":{\"targetSamplesPerPixel\":512}")
        val before=SceneJson().compact(r)
        assertEquals(RayDataEdit.Conflict, codec.edit(r, SceneRayField.SAMPLES, null, "1024"))
        assertEquals(RayDataEdit.Unchanged, codec.edit(r, SceneRayField.SAMPLES, r["rayTracing"]["targetSamplesPerPixel"], "512"))
        for (value in listOf("abc", "1.1", "0", "4097")) assertTrue(codec.edit(r, SceneRayField.SAMPLES, r["rayTracing"]["targetSamplesPerPixel"], value) is RayDataEdit.Rejected)
        assertEquals(before,SceneJson().compact(r))
    }
    @Test fun malformedFieldCanBeCorrectedWithoutRepairingOtherFields() {
        val r=root(",\"rayTracing\":{\"maxReflectionBounces\":null,\"maxRaysPerFrame\":-1}")
        assertEquals(RayDataEdit.Changed,codec.edit(r,SceneRayField.REFLECTIONS,r["rayTracing"]["maxReflectionBounces"],"2"))
        assertEquals(2,r["rayTracing"]["maxReflectionBounces"].intValue())
        assertEquals(-1,r["rayTracing"]["maxRaysPerFrame"].intValue())
        assertNull(codec.read(r).settings)
    }
}
