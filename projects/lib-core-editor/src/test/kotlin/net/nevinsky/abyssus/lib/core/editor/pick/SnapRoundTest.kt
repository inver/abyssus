/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SnapRoundTest {
    private val snap = GizmoSnap(moveStep = 0.5f, rotateDegrees = 15f)

    @Test fun movesRoundToTheNearestStep() {
        assertEquals(2.5f, snap.move(2.31f), 0f)
        assertEquals(2.0f, snap.move(2.2f), 0f)
        assertEquals(-4.5f, snap.move(-4.4f), 0f)
        assertEquals(0.5f, snap.move(0.25f + 1e-4f), 0f)
    }

    @Test fun anglesRoundToFifteenDegrees() {
        assertEquals(30f, snap.rotate(37f), 0f)
        assertEquals(45f, snap.rotate(38f), 0f)
        assertEquals(-15f, snap.rotate(-12f), 0f)
    }

    @Test fun roundingToZeroIsNeverNegativeZero() {
        assertEquals(0, java.lang.Float.floatToIntBits(snap.move(-0.1f)))
        assertEquals(0, java.lang.Float.floatToIntBits(snap.rotate(-3f)))
    }

    @Test fun stepsMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) { GizmoSnap(0f, 15f) }
        assertThrows(IllegalArgumentException::class.java) { GizmoSnap(0.5f, -1f) }
    }
}
