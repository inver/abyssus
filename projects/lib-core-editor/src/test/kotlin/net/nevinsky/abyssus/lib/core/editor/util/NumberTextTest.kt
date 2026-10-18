/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberTextTest {
    @Test
    fun preservesEachEditorsEstablishedText() {
        assertEquals("1", numberText(1.0))
        assertEquals("1.25", numberText(1.2500))
        assertEquals("0.0000001", numberText(1e-7))
        assertEquals("1.0", numberText(1f, NumberTextStyle.FOLIAGE))
        assertEquals("0.0000001", numberText(1e-7f, NumberTextStyle.FOLIAGE))
        assertEquals("7", numberText(0.07 * 100, NumberTextStyle.DETAIL))
        assertEquals("1", numberText(1.0, NumberTextStyle.UI))
        assertEquals("1.0E-7", numberText(1e-7, NumberTextStyle.UI))
    }

    @Test
    fun uiDocumentNumbersKeepTheirIntegerThreshold() {
        assertEquals("1", uiNumberNode(1.0).toString())
        assertEquals("1.25", uiNumberNode(1.25).toString())
        assertEquals("9.999999995E8", uiNumberNode(999999999.5).toString())
        assertEquals("1.0E9", uiNumberNode(1e9).toString())
    }
}
