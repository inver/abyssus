/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.flightgear

import net.nevinsky.abyssus.lib.gdx.editor.flightgear.fixtureAc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** The real Cessna 172R archive, when it is on this machine (`ABYSSUS_C172R_ZIP`, or the shared download cache). */
fun cessnaArchive(): File? = (System.getenv("ABYSSUS_C172R_ZIP")?.let(::File)
    ?: File(System.getProperty("user.home"), ".cache/abyssus-flightgear/c172r.zip")).takeIf { it.isFile }

class Ac3dReaderTest {
    private val reader = Ac3dReader()

    private fun Ac3dObject.flatten(): List<Ac3dObject> = listOf(this) + kids.flatMap { it.flatten() }

    @Test
    fun theFixtureHasItsMaterialsPartsAndSurfaces() {
        val model = reader.read(fixtureAc(), "fixture.ac")
        assertEquals(listOf("grey", "white", "glass"), model.materials.map { it.name })
        assertEquals(0.5f, model.materials[2].transparency)
        val parts = model.world.flatten().mapNotNull { it.name }
        assertEquals(listOf("Body", "Wing", "Prop", "PropDisc", "Tail"), parts)
        val body = model.world.kids.first()
        assertEquals("/home/author/src/skin.rgb", body.texture)
        assertEquals(45f, body.crease)
        assertEquals(9, body.vertices.size / 3)
        assertEquals(6, body.surfaces.count { it.type == 0 })
        assertEquals(1, body.surfaces.count { it.type == 2 })
        assertTrue(body.surfaces.first().smooth)
        val wing = model.world.kids[1]
        assertTrue(wing.surfaces.single().twoSided)
        assertFalse(wing.surfaces.single().smooth)
        assertEquals(1, wing.surfaces.single().material)
        assertEquals(DEFAULT_CREASE, wing.crease)
    }

    @Test
    fun uvsAreReadPerReference() {
        val wing = reader.read(fixtureAc(), "fixture.ac").world.kids[1]
        assertEquals(listOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f), wing.surfaces.single().uvs.toList())
    }

    @Test
    fun aBrokenFileSaysWhere() {
        val broken = fixtureAc().replace("numvert 9", "numvert x")
        val e = assertThrows(Ac3dException::class.java) { reader.read(broken, "broken.ac") }
        assertTrue(e.message!!, e.message!!.startsWith("broken.ac line"))
        assertThrows(Ac3dException::class.java) { reader.read("hello", "x.ac") }
    }

    @Test
    fun theCessnaModelReads() {
        val zip = cessnaArchive()
        assumeTrue("c172r.zip is not on this machine", zip != null)
        FlightGearArchive(zip!!).use { archive ->
            val model = reader.read(String(archive.read("c172r/Models/c172-dpm.ac")!!), "c172-dpm.ac")
            val parts = model.world.flatten().filter { it.type == "poly" }
            assertEquals(33, parts.size)
            assertEquals(862, parts.sumOf { it.vertices.size / 3 })
            assertTrue(parts.any { it.name == "Propeller.2" })
        }
    }
}
