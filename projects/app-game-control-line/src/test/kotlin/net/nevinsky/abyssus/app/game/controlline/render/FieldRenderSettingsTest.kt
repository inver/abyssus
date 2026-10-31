/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import net.nevinsky.abyssus.lib.gdx.shader.OutputTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FieldRenderSettingsTest {
    @Test
    fun theGameDrawsThroughTheTransformAndLightsWithTheSkyByDefault() {
        val settings = FieldRenderSettings()
        assertEquals(OutputTransform(DEFAULT_EXPOSURE), settings.outputTransform)
        assertEquals(true, settings.skyLight)
    }

    @Test
    fun legacyIsTheGameAsItWasDrawn() {
        assertNull(FieldRenderSettings.LEGACY.outputTransform)
        assertEquals(false, FieldRenderSettings.LEGACY.skyLight)
    }

    @Test
    fun aContextWithoutOpenGl30DropsTheTransformAndKeepsTheRest() {
        val settings = FieldRenderSettings(OutputTransform(1.5f), skyLight = true)
        assertSame(settings, settings.forContext(gl30 = true))
        assertEquals(FieldRenderSettings(null, skyLight = true), settings.forContext(gl30 = false))
        assertSame(FieldRenderSettings.LEGACY, FieldRenderSettings.LEGACY.forContext(gl30 = false))
    }
}
