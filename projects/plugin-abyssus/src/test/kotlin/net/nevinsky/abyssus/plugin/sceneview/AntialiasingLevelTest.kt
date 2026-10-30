/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.plugin.settings.AntialiasingLevel

import org.junit.Assert.assertEquals
import org.junit.Test

class AntialiasingLevelTest {
    @Test fun defaultsAndUnknownValuesUseFourSamples() {
        assertEquals(AntialiasingLevel.X4, AntialiasingLevel.DEFAULT)
        for (text in listOf(null, "", "future", "3")) {
            assertEquals(AntialiasingLevel.X4, AntialiasingLevel.parse(text))
        }
    }

    @Test fun everyChoiceRoundTripsAndHasItsSampleCount() {
        assertEquals(listOf(0, 2, 4, 8), AntialiasingLevel.entries.map { it.samples })
        for (level in AntialiasingLevel.entries) assertEquals(level, AntialiasingLevel.parse(level.name))
    }
}
