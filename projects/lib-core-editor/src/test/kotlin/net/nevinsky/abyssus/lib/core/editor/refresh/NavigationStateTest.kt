/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.refresh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationStateTest {
    private val scene = "/p/scenes/Main.scene"

    @Test
    fun survivingRowsAreRestoredByIdentityNotLabel() {
        val state = NavigationState(selected = listOf("$scene/ecs/0"), expanded = listOf("$scene/ecs/0", "$scene/ecs"))
        val restore = restoreNavigation(state, setOf(scene, "$scene/ecs", "$scene/ecs/0", "$scene/ecs/1"))
        assertEquals(listOf("$scene/ecs", "$scene/ecs/0"), restore.expand)
        assertEquals(listOf("$scene/ecs/0"), restore.select)
        assertFalse(restore.selectionLost)
    }

    @Test
    fun aRemovedSelectionIsClearedNotMovedToANeighbour() {
        val state = NavigationState(selected = listOf("$scene/ecs/3"), expanded = listOf("$scene/ecs/3", "$scene/ecs"))
        val restore = restoreNavigation(state, setOf(scene, "$scene/ecs", "$scene/ecs/0"))
        assertEquals(listOf("$scene/ecs"), restore.expand)
        assertTrue(restore.select.isEmpty())
        assertTrue(restore.selectionLost)
    }

    @Test
    fun aRowCapturedTwiceAppearsOnce() {
        val state = NavigationState(selected = listOf("a/b", "a/b"), expanded = listOf("a", "a", "a/b"))
        val restore = restoreNavigation(state, setOf("a", "a/b"))
        assertEquals(listOf("a", "a/b"), restore.expand)
        assertEquals(listOf("a/b"), restore.select)
    }

    @Test
    fun anEmptyCaptureRestoresNothing() {
        val restore = restoreNavigation(NavigationState(emptyList(), emptyList()), setOf("a"))
        assertTrue(restore.expand.isEmpty() && restore.select.isEmpty())
    }
}
