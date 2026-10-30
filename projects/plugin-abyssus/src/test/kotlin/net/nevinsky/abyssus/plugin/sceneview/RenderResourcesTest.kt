/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The context lifecycle with fake resources: no GL, counting what was released and what was abandoned. */
class RenderResourcesTest {
    private class Fake(val name: String)

    private val released = mutableListOf<String>()
    private val abandoned = mutableListOf<String>()
    private val resources = RenderResources()

    private fun make(name: String, failing: Boolean = false) = resources.own(
        Fake(name),
        release = { released += it.name; if (failing) throw IllegalStateException(it.name) },
        abandon = { abandoned += it.name },
    )

    @Test
    fun releaseAllReleasesNewestFirstAndAbandonsNothing() {
        make("a"); make("b"); make("c")
        resources.releaseAll()
        assertEquals(listOf("c", "b", "a"), released)
        assertTrue(abandoned.isEmpty())
        assertEquals(0, resources.size)
    }

    @Test
    fun abandonAllReleasesNothingAndRunsOnlyTheGlFreeCleanup() {
        make("a"); make("b")
        resources.abandonAll()
        assertTrue("an abandoned context's objects are never deleted", released.isEmpty())
        assertEquals(listOf("b", "a"), abandoned)
        assertEquals(0, resources.size)
    }

    @Test
    fun aHandleOfAnAbandonedGenerationIsNeverReleasedThroughTheNewContext() {
        val old = make("old")
        val before = resources.generation
        resources.abandonAll()
        assertTrue(resources.generation > before)
        val fresh = make("fresh")
        assertFalse("stale", resources.release(old))
        assertTrue(released.isEmpty())
        assertTrue(resources.release(fresh))
        assertEquals(listOf("fresh"), released)
    }

    @Test
    fun aReleasedHandleIsNotReleasedTwiceAndAReleasedGenerationIsStale() {
        val a = make("a")
        assertTrue(resources.release(a))
        assertFalse(resources.release(a))
        val b = make("b")
        resources.releaseAll()
        assertFalse("released with its generation", resources.release(b))
        assertEquals(listOf("a", "b"), released)
    }

    @Test
    fun oneFailingReleaseDoesNotStopTheOthersAndIsRethrown() {
        make("a"); make("b", failing = true); make("c")
        try {
            resources.releaseAll()
            fail("expected the failure")
        } catch (e: IllegalStateException) {
            assertEquals("b", e.message)
        }
        assertEquals(listOf("c", "b", "a"), released)
        assertEquals(0, resources.size)
    }

    @Test
    fun resourcesMadeAfterAnAbandonBelongToTheNextContext() {
        make("a")
        resources.abandonAll()
        make("b")
        resources.releaseAll()
        assertEquals(listOf("b"), released)
        assertEquals(listOf("a"), abandoned)
    }
}
