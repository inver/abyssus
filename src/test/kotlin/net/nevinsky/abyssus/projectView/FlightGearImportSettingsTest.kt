/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import net.nevinsky.abyssus.core.flightgear.FlightGearAircraft
import net.nevinsky.abyssus.core.flightgear.FlightGearInspection
import net.nevinsky.abyssus.core.flightgear.ImportPart
import net.nevinsky.abyssus.core.flightgear.ImportSize
import net.nevinsky.abyssus.editor.terrain.FolderNameError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FlightGearImportSettingsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun settings(license: String? = null): FlightGearImportSettings {
        val assets = File(temp.newFolder(), "assets").apply { mkdirs() }
        File(assets, "model_taken").mkdirs()
        val aircraft = FlightGearAircraft("c172r", "c172r/", "c172r/c172r-set.xml", "Cessna 172R", "David Megginson", license, "c172r/Models/c172-dpm.xml")
        val parts = listOf(ImportPart("Fuselage", true), ImportPart("Propeller", true), ImportPart("Propeller.2", false))
        return FlightGearImportSettings(assets, FlightGearInspection(aircraft, parts, emptyList(), license, emptyList()))
    }

    @Test
    fun defaultsNameTheFolderAfterTheAircraftAndLeaveOutPartsHiddenAtRest() {
        val s = settings()
        assertEquals("model_c172r", s.folderName)
        assertTrue(s.originalSize)
        assertTrue(s.isTicked("Propeller"))
        assertFalse(s.isTicked("Propeller.2"))
        val request = s.request()!!
        assertEquals(ImportSize.Original, request.size)
        assertEquals(setOf("Propeller.2"), request.excludedParts)
        assertTrue(s.licenseUnknown)
        assertFalse(settings("GPL-2.0-or-later").licenseUnknown)
    }

    @Test
    fun aTakenOrInvalidNameDisablesCreate() {
        val s = settings()
        s.folderName = "model_taken"
        assertEquals(FolderNameError.EXISTS, s.nameError())
        assertNull(s.request())
        s.folderName = "a/b"
        assertEquals(FolderNameError.SEPARATOR, s.nameError())
        assertNull(s.request())
    }

    @Test
    fun aSpanMustBeAPositiveNumber() {
        val s = settings()
        s.originalSize = false
        for (bad in listOf("", "0", "-1", "abc", "NaN", "Infinity")) {
            s.spanText = bad
            assertEquals(bad, ImportSizeError.SPAN, s.sizeError())
            assertNull(s.request())
        }
        s.spanText = " 1.0 "
        assertNull(s.sizeError())
        assertEquals(ImportSize.Span(1.0), s.request()!!.size)
    }

    @Test
    fun atLeastOnePartMustBeTicked() {
        val s = settings()
        s.tick("Fuselage", false)
        s.tick("Propeller", false)
        assertNull(s.request())
        s.tick("Propeller.2", true)
        assertEquals(setOf("Fuselage", "Propeller"), s.request()!!.excludedParts)
    }
}
