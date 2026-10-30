/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilterTable
import com.github.stephengold.joltjni.PhysicsSystem
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import org.junit.Assert.assertTrue
import org.junit.Test

class NativesTest {
    @Test
    fun loadsTheLibraryAndCreatesAnEmptySystem() {
        val natives = JoltNatives()
        natives.load()
        natives.load()
        assertTrue(natives.loaded())

        val pairs = ObjectLayerPairFilterTable(2).apply { enableCollision(0, 1); enableCollision(1, 1) }
        val broad = BroadPhaseLayerInterfaceTable(2, 2).apply { mapObjectToBroadPhaseLayer(0, 0); mapObjectToBroadPhaseLayer(1, 1) }
        val filter = ObjectVsBroadPhaseLayerFilterTable(broad, 2, pairs, 2)
        val system = PhysicsSystem()
        system.init(16, 0, 16, 16, broad, filter, pairs)
        assertTrue(system.getNumBodies() == 0)
        system.close()
        filter.close(); broad.close(); pairs.close()
    }
}
