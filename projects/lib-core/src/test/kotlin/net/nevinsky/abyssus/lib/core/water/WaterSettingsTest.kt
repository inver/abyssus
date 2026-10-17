/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterSettingsTest {
    private val presets = WaterPresets()
    private val validation = WaterValidation()
    private val lake = presets.defaults(WaterPreset.LAKE, "a", "Lake")
    private val sea = presets.defaults(WaterPreset.SEA, "b", "Sea")

    @Test fun lakeIsSmallerAndCalmerThanSea() {
        assertTrue(lake.width < sea.width && lake.length < sea.length)
        assertTrue(lake.waveAmplitude < sea.waveAmplitude && lake.waveLength < sea.waveLength)
        assertEquals(WaterPreset.LAKE, lake.preset)
        assertEquals(WaterPreset.SEA, sea.preset)
    }

    @Test fun presetsPlaceTheCentreAndLevel() {
        val placed = presets.defaults(WaterPreset.LAKE, "a", "Lake", 3.0, 4.0, 5.0)
        assertEquals(Triple(3.0, 4.0, 5.0), Triple(placed.x, placed.level, placed.z))
    }

    @Test fun onlyKnownPresetNamesParse() {
        assertEquals(WaterPreset.SEA, presets.parse("SEA"))
        for (bad in listOf(null, "", "sea", "OCEAN")) assertNull(presets.parse(bad))
    }

    @Test fun presetsAreValid() {
        assertTrue(validation.isValid(lake))
        assertTrue(validation.isValid(sea))
    }

    @Test fun nonFiniteValuesAreRefusedEverywhere() {
        for (field in WaterField.entries) for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY))
            assertEquals("$field $bad", WaterIssue.NOT_FINITE, validation.check(field, bad))
    }

    @Test fun positiveFieldsRefuseZeroAndNegatives() {
        for (field in listOf(WaterField.WIDTH, WaterField.LENGTH, WaterField.CLARITY, WaterField.WAVE_LENGTH, WaterField.FOAM_WIDTH)) {
            assertEquals(WaterIssue.NOT_POSITIVE, validation.check(field, 0.0))
            assertEquals(WaterIssue.NOT_POSITIVE, validation.check(field, -1.0))
            assertNull(validation.check(field, 0.001))
        }
    }

    @Test fun amplitudeAndSpeedMayBeZeroButNotNegative() {
        for (field in listOf(WaterField.WAVE_AMPLITUDE, WaterField.WAVE_SPEED)) {
            assertNull(validation.check(field, 0.0))
            assertEquals(WaterIssue.NEGATIVE, validation.check(field, -0.1))
        }
    }

    @Test fun tintAndFoamAmountStayInTheUnitRange() {
        for (field in listOf(WaterField.TINT_R, WaterField.TINT_G, WaterField.TINT_B, WaterField.FOAM_AMOUNT)) {
            assertNull(validation.check(field, 0.0))
            assertNull(validation.check(field, 1.0))
            assertEquals(WaterIssue.OUT_OF_UNIT_RANGE, validation.check(field, 1.01))
            assertEquals(WaterIssue.OUT_OF_UNIT_RANGE, validation.check(field, -0.01))
        }
    }

    @Test fun positionIsUnconstrainedButFinite() {
        for (field in listOf(WaterField.X, WaterField.LEVEL, WaterField.Z)) {
            assertNull(validation.check(field, -1e6))
            assertNull(validation.check(field, 1e6))
        }
    }

    @Test fun settingsReportEveryBrokenField() {
        val broken = lake.with(WaterField.WIDTH, 0.0).with(WaterField.FOAM_AMOUNT, 2.0)
        assertEquals(listOf(WaterField.WIDTH, WaterField.FOAM_AMOUNT), validation.check(broken).keys.toList())
    }

    @Test fun withReplacesOnlyTheNamedField() {
        for (field in WaterField.entries) {
            val changed = lake.with(field, 0.5)
            assertEquals(0.5, changed.value(field), 0.0)
            assertEquals(lake, changed.with(field, lake.value(field)))
        }
    }
}
