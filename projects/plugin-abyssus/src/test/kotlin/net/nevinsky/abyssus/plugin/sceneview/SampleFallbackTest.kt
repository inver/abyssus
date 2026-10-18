/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.plugin.settings.AntialiasingLevel

import org.junit.Assert.assertEquals
import org.junit.Test

class SampleFallbackTest {
    @Test fun triesEveryLowerChoiceEndingWithOff() {
        assertEquals(listOf(8, 4, 2, 0), SampleFallback().counts(AntialiasingLevel.X8))
        assertEquals(listOf(4, 2, 0), SampleFallback().counts(AntialiasingLevel.X4))
        assertEquals(listOf(2, 0), SampleFallback().counts(AntialiasingLevel.X2))
        assertEquals(listOf(0), SampleFallback().counts(AntialiasingLevel.OFF))
    }
}
