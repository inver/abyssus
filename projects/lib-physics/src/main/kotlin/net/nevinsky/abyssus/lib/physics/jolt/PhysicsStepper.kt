/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.jolt

import com.github.stephengold.joltjni.BodyInterface
import com.github.stephengold.joltjni.JobSystemSingleThreaded
import com.github.stephengold.joltjni.PhysicsSystem
import com.github.stephengold.joltjni.TempAllocatorImpl
import net.nevinsky.abyssus.lib.physics.Contact
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP

/** Runs fixed steps with pending forces and rope measurements, then clears forces and publishes poses. */
internal class PhysicsStepper(
    private val system: PhysicsSystem,
    private val bodies: BodyInterface,
    private val allocator: TempAllocatorImpl,
    private val jobs: JobSystemSingleThreaded,
    private val records: Collection<PhysicsWorld.BodyRecord>,
    private val constraints: List<PhysicsWorld.ConstraintRecord>,
    private val contacts: MutableMap<Pair<Int, Int>, Contact>,
    private val ropeTension: RopeTension,
    private val poseWriter: PhysicsPoseWriter,
    private val clock: FixedStepClock,
) {
    fun advance(seconds: Float): Int {
        val steps = clock.advance(seconds)
        runSteps(steps)
        return steps
    }

    fun step() = runSteps(1)

    private fun runSteps(steps: Int) {
        contacts.clear()
        repeat(steps) {
            for (record in records) record.applyPending(bodies)
            val ropes = ropeTension.before(constraints)
            val error = system.update(PHYSICS_STEP, 1, allocator, jobs)
            check(error == 0) { "Jolt failed to step (error $error)" }
            ropes.after()
        }
        for (record in records) record.clearPending()
        poseWriter.writeBack()
    }
}
