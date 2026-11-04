/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HullInputTest {
    private val corners = listOf(-1f, 1f).flatMap { x -> listOf(-1f, 1f).flatMap { y -> listOf(-1f, 1f).map { z -> Vector3(x, y, z) } } }

    @Test fun aSmallModelKeepsEveryPoint() {
        val points = corners + List(100) { Vector3(0.1f * it / 100, 0f, 0f) }
        assertSame(points, hullInput(points))
    }

    @Test fun aDetailedModelKeepsItsHullAndDropsItsInside() {
        val random = Random(20261104)
        val inside = List(80_000) { Vector3(random.nextFloat() * 1.8f - 0.9f, random.nextFloat() * 1.8f - 0.9f, random.nextFloat() * 1.8f - 0.9f) }
        val points = inside.subList(0, 40_000) + corners + inside.subList(40_000, 80_000)

        val kept = hullInput(points)

        assertTrue("bounded: ${kept.size}", kept.size <= MAX_HULL_INPUT)
        assertEquals("a box's hull is its corners", corners.toSet(), kept.toSet())
    }

    @Test fun aRoundModelIsBoundedAndKeepsItsExtent() {
        val random = Random(7)
        val sphere = List(50_000) {
            Vector3(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).nor()
        }
        val kept = hullInput(sphere)
        assertTrue(kept.size in 200..MAX_HULL_INPUT)
        assertTrue("every kept point is on the surface", kept.all { kotlin.math.abs(it.len() - 1f) < 1e-4f })
        // the hull still reaches out in every axis direction
        for (axis in listOf(Vector3.X, Vector3.Y, Vector3.Z)) {
            assertTrue(kept.maxOf { it.dot(axis) } > 0.99f)
            assertTrue(kept.minOf { it.dot(axis) } < -0.99f)
        }
        assertEquals("deterministic", kept, hullInput(sphere))
    }
}
