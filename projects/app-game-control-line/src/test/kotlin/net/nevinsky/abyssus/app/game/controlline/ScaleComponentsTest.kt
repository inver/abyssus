/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import net.nevinsky.abyssus.app.game.controlline.components.CONTROL_LINE_COMPONENTS
import net.nevinsky.abyssus.app.game.controlline.components.PlaneClass
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.components.ScaleComponent
import net.nevinsky.abyssus.app.game.controlline.components.VitrineTableComponent
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class ScaleComponentsTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)

    @Test
    fun theGameRegistersBothComponentsByShortName() {
        assertSame(ScaleComponent::class.java, CONTROL_LINE_COMPONENTS["ScaleComponent"])
        assertSame(VitrineTableComponent::class.java, CONTROL_LINE_COMPONENTS["VitrineTableComponent"])
    }

    @Test
    fun aScaleComponentBindsItsValues() {
        val node = json.readObject("""{"prototype": "MiG-3", "firstFlight": 1940, "wingspan": 10.3, "scale": 8}""")
        val c = json.bindComponent(node, ScaleComponent::class.java)
        assertEquals("MiG-3", c.prototype)
        assertEquals(1940, c.firstFlight)
        assertEquals(10.3f, c.wingspan, 0f)
        assertEquals(8, c.scale)
    }

    @Test
    fun aVitrineTableBindsItsTopHeight() {
        val c = json.bindComponent(json.readObject("""{"topHeight": 0.9}"""), VitrineTableComponent::class.java)
        assertEquals(0.9f, c.topHeight, 0f)
        assertEquals(0.75f, VitrineTableComponent().topHeight, 0f)
    }

    @Test
    fun scaleBindsAsAPlaneClass() {
        val c = json.bindComponent(json.readObject("""{"planeClass": "SCALE"}"""), PlaneComponent::class.java)
        assertEquals(PlaneClass.SCALE, c.planeClass)
    }
}
