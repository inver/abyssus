/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import net.nevinsky.abyssus.lib.core.ecs.component.LIGHT_INTENSITY
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class LightComponentBindingTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)

    private fun light(body: String) = json.bindComponent(json.readObject(body), LightComponent::class.java).light

    @Test
    fun anEmptyComponentIsADefaultLight() {
        val light = light("{}")
        assertEquals(LIGHT_INTENSITY, light.intensity, 0f)
        assertEquals(1f, light.color.r, 0f)
    }

    @Test
    fun anEmptyLightObjectIsADefaultLight() {
        assertEquals(LIGHT_INTENSITY, light("""{"light":{}}""").intensity, 0f)
    }

    @Test
    fun writtenValuesStillBind() {
        assertEquals(2f, light("""{"light":{"intensity":2}}""").intensity, 0f)
    }
}
