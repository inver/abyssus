/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.jolt

import com.github.stephengold.joltjni.BoxShape
import com.github.stephengold.joltjni.CapsuleShape
import com.github.stephengold.joltjni.CompoundShape
import com.github.stephengold.joltjni.SphereShape
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageColliderShape
import net.nevinsky.abyssus.lib.physics.ColliderPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** The static compounds built for foliage chunks: sub-shape poses and primitive sizes as placed. */
class ShapeFactoryTest {
    init {
        JoltNatives().load()
    }

    private val s = sqrt(0.5f) // a quarter turn about +Y

    private val box = ColliderPlacement(FoliageColliderShape.BOX, 1f, 2f, 3f, 0f, s, 0f, s, 0.5f, 1f, 1.5f, 0f, 0f, false)
    private val sphere = ColliderPlacement(FoliageColliderShape.SPHERE, -4f, 0f, 2f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0.75f, 0f, false)
    private val capsule = ColliderPlacement(FoliageColliderShape.CAPSULE, 10f, 1f, -3f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0.4f, 2f, false)

    @Test
    fun aCompoundHoldsEachPlacementAtItsPoseWithItsSize() {
        val built = FoliageCompounds().build(listOf(box, sphere, capsule)) as FoliageCompounds.Built.Shape
        try {
            val compound = built.ref.getPtr() as CompoundShape
            assertEquals(3, compound.numSubShapes)
            val com = compound.centerOfMass
            val placements = listOf(box, sphere, capsule)
            for ((i, p) in placements.withIndex()) {
                val sub = compound.getSubShape(i)
                // positions are kept relative to the compound's own centre of mass
                assertEquals(p.x, sub.positionCom.x + com.x, 1e-4f)
                assertEquals(p.y, sub.positionCom.y + com.y, 1e-4f)
                assertEquals(p.z, sub.positionCom.z + com.z, 1e-4f)
                assertEquals(1f, kotlin.math.abs(sub.rotation.x * p.qx + sub.rotation.y * p.qy + sub.rotation.z * p.qz + sub.rotation.w * p.qw), 1e-4f)
            }
            val sizes = (0 until 3).map { compound.getSubShape(it).shape }
            val b = sizes.filterIsInstance<BoxShape>().single().halfExtent
            assertEquals(0.5f, b.x, 1e-4f)
            assertEquals(1f, b.y, 1e-4f)
            assertEquals(1.5f, b.z, 1e-4f)
            assertEquals(0.75f, sizes.filterIsInstance<SphereShape>().single().radius, 1e-4f)
            val c = sizes.filterIsInstance<CapsuleShape>().single()
            assertEquals(0.4f, c.radius, 1e-4f)
            assertEquals(2f, c.halfHeightOfCylinder, 1e-4f)
        } finally {
            built.release()
        }
    }

    @Test
    fun aSingleCopyAndAnEmptyChunkBehave() {
        val one = FoliageCompounds().build(listOf(sphere))
        assertTrue(one is FoliageCompounds.Built.Shape)
        (one as FoliageCompounds.Built.Shape).release()
        assertTrue(FoliageCompounds().build(emptyList()) is FoliageCompounds.Built.Failed)
    }

    @Test
    fun aLargeChunkIsBuiltAndReleasedOnce() {
        val many = List(5_000) { i -> sphere.let { ColliderPlacement(it.shape, i * 0.1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0.05f, 0f, false) } }
        val built = FoliageCompounds().build(many) as FoliageCompounds.Built.Shape
        assertEquals(5_000, (built.ref.getPtr() as CompoundShape).numSubShapes)
        built.release()
    }
}
