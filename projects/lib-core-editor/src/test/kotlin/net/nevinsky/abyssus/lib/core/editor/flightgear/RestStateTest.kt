/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.flightgear

import net.nevinsky.abyssus.lib.core.editor.flightgear.fixtureArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Element

class RestStateTest {
    @get:Rule
    val temp = TemporaryFolder()
    private val rest = RestState()

    private fun condition(xml: String): Element = FlightGearXml().parse("<condition>$xml</condition>".toByteArray(), "test")

    @Test
    fun theStaticPropellerShowsAndTheDiscDoesNot() {
        FlightGearArchive(fixtureArchive(temp.root)).use { archive ->
            val model = FlightGearModelXmlReader(archive).read("fixture/Models/fixture.xml")
            val shown = model.selects.associate { it.parts.single() to rest.shows(it.condition) }
            assertEquals(mapOf("Prop" to true, "PropDisc" to false), shown)
        }
    }

    @Test
    fun logicalOperators() {
        val below = "<less-than><property>p</property><value>1</value></less-than>"
        val above = "<greater-than><property>p</property><value>1</value></greater-than>"
        assertTrue(rest.shows(condition("<and>$below<not>$above</not></and>")))
        assertFalse(rest.shows(condition("<and>$below$above</and>")))
        assertTrue(rest.shows(condition("<or>$above$below</or>")))
        assertTrue(rest.shows(condition("<equals><property>p</property><value>0</value></equals>")))
        assertFalse(rest.shows(condition("<property>gear/gear[0]/wow</property>")))
    }

    @Test
    fun anUnknownConditionShowsThePart() {
        assertTrue(rest.shows(condition("<interpolate-table/>")))
        assertTrue(rest.shows(condition("<less-than><property>p</property><value>soon</value></less-than>")))
        assertTrue(rest.shows(null))
    }
}
