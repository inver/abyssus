/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.flightgear

import net.nevinsky.abyssus.lib.core.editor.flightgear.fixtureArchive
import net.nevinsky.abyssus.lib.core.editor.flightgear.writeZip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FlightGearModelXmlTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun theFixtureModelNamesItsGeometryNestedModelAndPanel() {
        FlightGearArchive(fixtureArchive(temp.root)).use { archive ->
            val model = FlightGearModelXmlReader(archive).read("fixture/Models/fixture.xml")
            assertEquals("fixture/Models/fixture.ac", model.acPath)
            assertEquals(1, model.panels)
            val wheel = model.nested.single()
            assertEquals("fixture/Models/wheel.xml", wheel.path)
            assertEquals(-0.5, wheel.offsets.x, 1e-9)
            assertEquals(2, model.selects.size)
        }
    }

    @Test
    fun aDoctypeWithAnExternalEntityIsRefused() {
        val secret = File(temp.root, "secret.txt").apply { writeText("secret") }
        val xml = """<?xml version="1.0"?><!DOCTYPE p [<!ENTITY x SYSTEM "file://${secret.absolutePath}">]><PropertyList><path>&x;</path></PropertyList>"""
        val zip = writeZip(File(temp.root, "xxe.zip"), mapOf("a/a-set.xml" to xml.toByteArray()))
        FlightGearArchive(zip).use { archive ->
            assertThrows(FlightGearArchiveException::class.java) { archive.aircraft() }
        }
    }
}
