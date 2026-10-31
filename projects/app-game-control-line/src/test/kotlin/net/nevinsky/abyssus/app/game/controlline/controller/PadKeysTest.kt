/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import com.badlogic.gdx.Input
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PadKeysTest {
    private val log = ArrayList<String>()

    private fun send(keycode: Int, takes: Set<Int> = emptySet(), tab: Boolean = false, space: Boolean = false) =
        navigateKey(keycode, { log += "key $it"; it in takes }, { log += "tab shift=$it"; tab }, { log += "space"; space })

    @Test
    fun aKeyTheScreenTakesIsNotRepeatedAsAFallback() {
        assertTrue(send(Input.Keys.DOWN, takes = setOf(Input.Keys.DOWN)))
        assertEquals(listOf("key ${Input.Keys.DOWN}"), log)
    }

    @Test
    fun downOnATabScreenBecomesTab() {
        assertTrue(send(Input.Keys.DOWN, tab = true))
        assertEquals(listOf("key ${Input.Keys.DOWN}", "tab shift=false"), log)
    }

    @Test
    fun upOnATabScreenBecomesShiftTab() {
        assertTrue(send(Input.Keys.UP, tab = true))
        assertEquals(listOf("key ${Input.Keys.UP}", "tab shift=true"), log)
    }

    @Test
    fun anEnterNothingTookBecomesSpaceForACheckbox() {
        assertTrue(send(Input.Keys.ENTER, space = true))
        assertEquals(listOf("key ${Input.Keys.ENTER}", "space"), log)
    }

    @Test
    fun anEnterAButtonTookIsNotSpace() {
        assertTrue(send(Input.Keys.ENTER, takes = setOf(Input.Keys.ENTER)))
        assertEquals(listOf("key ${Input.Keys.ENTER}"), log)
    }

    @Test
    fun leftRightAndEscapeHaveNoFallback() {
        for (key in listOf(Input.Keys.LEFT, Input.Keys.RIGHT, Input.Keys.ESCAPE)) assertFalse(send(key, tab = true, space = true))
        assertEquals(3, log.size)
    }
}
