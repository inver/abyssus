/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoliageAdmissionTest {
    @Test
    fun twoLayersOf150000CopiesAdmitTheFirstAndRefuseTheSecondWhole() {
        val admission = FoliageAdmission(bodyCapacity = 10_000)
        assertNull(admission.admit(150_000, 400))
        assertEquals(FoliageRefusal.COPY_CAP, admission.admit(150_000, 400))
        assertEquals(150_000, admission.copies)
        assertEquals(400, admission.bodies)
        // a smaller layer still fits behind the refused one
        assertNull(admission.admit(50_000, 10))
    }

    @Test
    fun aSyntheticBakeOf300000CopiesIsRefusedBeforeAnyBodyIsCounted() {
        val admission = FoliageAdmission(bodyCapacity = 10_000)
        assertEquals(FoliageRefusal.COPY_CAP, admission.admit(300_000, 1))
        assertEquals(0, admission.bodies)
    }

    @Test
    fun theBodyCapacityLeftAfterOrdinaryBodiesIsAHardLimit() {
        val admission = FoliageAdmission(bodyCapacity = 10)
        assertNull(admission.admit(100, 6))
        assertEquals(FoliageRefusal.BODY_CAPACITY, admission.admit(100, 5))
        assertNull(admission.admit(100, 4))
        assertEquals(FoliageRefusal.BODY_CAPACITY, admission.admit(1, 1))
    }

    @Test
    fun theResultDependsOnlyOnTheOrderLayersAreOffered() {
        fun run(vararg layers: Long): List<FoliageRefusal?> {
            val admission = FoliageAdmission(bodyCapacity = 100)
            return layers.map { admission.admit(it, 1) }
        }
        assertEquals(run(150_000, 100_000, 40_000), run(150_000, 100_000, 40_000))
        assertEquals(listOf(null, FoliageRefusal.COPY_CAP, null), run(150_000, 100_000, 40_000))
        assertEquals(listOf(null, null, FoliageRefusal.COPY_CAP), run(100_000, 40_000, 150_000))
    }
}
