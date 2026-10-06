/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.flightgear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FlightGearArchiveTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun theFixtureAircraftIsListedWithItsModel() {
        FlightGearArchive(fixtureArchive(temp.root)).use { archive ->
            val aircraft = archive.aircraft().single()
            assertEquals("fixture", aircraft.id)
            assertEquals("fixture/", aircraft.folder)
            assertEquals("Fixture Plane", aircraft.description)
            assertEquals("Test Author (3D model)", aircraft.authors)
            assertNull(aircraft.license)
            assertEquals("fixture/Models/fixture.xml", aircraft.modelPath)
        }
    }

    @Test
    fun anArchiveWithoutASetFileHasNoAircraft() {
        val zip = writeZip(File(temp.root, "none.zip"), mapOf("readme.txt" to "hello".toByteArray()))
        FlightGearArchive(zip).use { assertTrue(it.aircraft().isEmpty()) }
    }

    @Test
    fun aircraftPathsResolveNextToTheAircraftFolder() {
        val nested = writeZip(File(temp.root, "nested.zip"), fixtureFiles().mapKeys { "Aircraft/" + it.key })
        FlightGearArchive(nested).use { archive ->
            assertEquals("Aircraft/fixture/Models/fixture.xml", archive.aircraft().single().modelPath)
            assertEquals("Aircraft/fixture/Models/wheel.ac", archive.resolve("Aircraft/fixture/Models/", "wheel.ac"))
            assertNull(archive.resolve("fixture/", "../../outside.ac"))
        }
    }

    @Test
    fun texturesAreFoundByNameAndLicencesListed() {
        FlightGearArchive(fixtureArchive(temp.root, license = true)).use { archive ->
            assertEquals(listOf("fixture/Models/skin.rgb"), archive.findByName("fixture/", "SKIN.rgb"))
            assertEquals(listOf("fixture/COPYING"), archive.licenseFiles("fixture/"))
        }
    }

    @Test
    fun entriesOutsideTheArchiveAreRejected() {
        val slip = writeZip(File(temp.root, "slip.zip"), mapOf("../evil.ac" to ByteArray(1)))
        assertThrows(FlightGearArchiveException::class.java) { FlightGearArchive(slip) }
        val absolute = writeZip(File(temp.root, "abs.zip"), mapOf("/etc/evil.ac" to ByteArray(1)))
        assertThrows(FlightGearArchiveException::class.java) { FlightGearArchive(absolute) }
    }

    @Test
    fun tooManyEntriesAreRejected() {
        val many = writeZip(File(temp.root, "many.zip"), (0..MAX_ENTRIES).associate { "f/$it.txt" to ByteArray(0) })
        assertThrows(FlightGearArchiveException::class.java) { FlightGearArchive(many) }
    }

    @Test
    fun aFileThatIsNotAZipIsRejected() {
        val text = File(temp.root, "plain.zip").apply { writeText("not a zip") }
        assertThrows(FlightGearArchiveException::class.java) { FlightGearArchive(text) }
    }
}
