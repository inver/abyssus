/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import com.fasterxml.jackson.databind.json.JsonMapper
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FoliageColliderReadTest {
    private val reader = FoliageColliderReader()
    private val json = JsonMapper()

    private fun read(text: String?) = reader.read(text?.let(json::readTree))

    private fun valid(text: String) = (read(text) as FoliageColliderRead.Valid).collider

    private fun reason(text: String) = (read(text) as FoliageColliderRead.Invalid).reason

    @Test
    fun anAbsentOrNullMemberIsNoCollider() {
        assertSame(FoliageColliderRead.Absent, read(null))
        assertSame(FoliageColliderRead.Absent, read("null"))
    }

    @Test
    fun anEmptyObjectHasTheColliderComponentDefaults() {
        assertEquals(FoliageCollider(), valid("{}"))
        assertEquals(FoliageColliderShape.BOX, valid("{}").shape)
    }

    @Test
    fun bindsEachShape() {
        assertEquals(
            FoliageCollider(FoliageColliderShape.BOX, 1f, 2f, 3f, offsetX = 0.5f, offsetY = -1f, offsetZ = 2f),
            valid("""{"shape":"BOX","halfExtents":{"x":1,"y":2,"z":3},"offset":{"x":0.5,"y":-1,"z":2}}"""),
        )
        assertEquals(FoliageCollider(FoliageColliderShape.SPHERE, radius = 2f), valid("""{"shape":"SPHERE","radius":2}"""))
        assertEquals(
            FoliageCollider(FoliageColliderShape.CAPSULE, radius = 0.4f, halfHeight = 3f),
            valid("""{"shape":"CAPSULE","radius":0.4,"halfHeight":3}"""),
        )
    }

    @Test
    fun anUnknownShapeIsInvalidNotAbsent() {
        for (shape in listOf("\"CONVEX_HULL\"", "\"HEIGHT_FIELD\"", "\"box\"", "5", "null".let { "[]" })) {
            assertTrue(shape, read("""{"shape":$shape}""") is FoliageColliderRead.Invalid)
        }
    }

    @Test
    fun malformedPayloadsAreInvalid() {
        for (text in listOf("5", "[]", """{"radius":"big"}""", """{"halfExtents":3}""", """{"halfExtents":{"x":"a"}}""", """{"offset":[1,2,3]}""")) {
            assertTrue(text, read(text) is FoliageColliderRead.Invalid)
        }
    }

    @Test
    fun activeSizesMustBeFinitePositiveAndOffsetsFinite() {
        assertTrue(reason("""{"shape":"SPHERE","radius":0}""").contains("radius"))
        assertTrue(reason("""{"shape":"CAPSULE","radius":0.4,"halfHeight":-1}""").contains("half height"))
        assertTrue(reason("""{"shape":"BOX","halfExtents":{"x":1,"y":0,"z":1}}""").contains("half extents"))
        assertTrue(reason("""{"shape":"SPHERE","radius":1e50}""").contains("radius"))
        assertTrue(reason("""{"offset":{"x":1e50}}""").contains("offset"))
    }

    @Test
    fun inactiveMembersAreNotChecked() {
        assertEquals(FoliageColliderShape.SPHERE, valid("""{"shape":"SPHERE","radius":1,"halfExtents":{"x":-5,"y":0,"z":0},"halfHeight":0}""").shape)
    }

    @Test
    fun aLayerMetaKeepsItsRawColliderIncludingUnknownMembers() {
        val dir: File = Files.createTempDirectory("collider-meta").toFile()
        try {
            File(dir, "assets/foliage_meadow").mkdirs()
            File(dir, "assets/foliage_meadow/meta.json").writeText(
                """{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","additional":{"terrain":"t","layers":[""" +
                    """{"id":1,"kind":"OBJECT","collider":{"shape":"CAPSULE","radius":0.4,"halfHeight":3,"vendor":{"k":1}}},{"id":2}]}}"""
            )
            val files = FileLoader(dir)
            val meta = testMetaLoader(dir, warningsTo(mutableListOf()), files).loadBaseMeta("foliage_meadow")!!.additional as FoliageMeta
            assertEquals(1, meta.layers[0].collider!!["vendor"]["k"].asInt())
            assertEquals(FoliageColliderShape.CAPSULE, valid(meta.layers[0].collider.toString()).shape)
            assertSame(FoliageColliderRead.Absent, reader.read(meta.layers[1].collider))
        } finally {
            dir.deleteRecursively()
        }
    }
}
