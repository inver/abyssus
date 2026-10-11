/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.jolt

import com.github.stephengold.joltjni.BoxShapeSettings
import com.github.stephengold.joltjni.CapsuleShapeSettings
import com.github.stephengold.joltjni.Jolt
import com.github.stephengold.joltjni.Quat
import com.github.stephengold.joltjni.ShapeRefC
import com.github.stephengold.joltjni.ShapeSettings
import com.github.stephengold.joltjni.SphereShapeSettings
import com.github.stephengold.joltjni.StaticCompoundShapeSettings
import com.github.stephengold.joltjni.Vec3
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageColliderShape
import net.nevinsky.abyssus.lib.physics.ColliderPlacement
import kotlin.math.min

/**
 * Builds the static compound shape of one foliage chunk: one primitive sub-shape per [ColliderPlacement], at the
 * placement's pose in world space (the chunk's body sits at the origin). Every placement was validated before this
 * runs; Jolt may still refuse a shape, and then nothing is left allocated.
 *
 * Ownership: each sub-shape's settings, result and reference are closed as soon as the compound has taken it (the
 * compound's native settings hold their own references, as the settings' `addHulls` does), and so is the compound's
 * settings object. What stays open is the compound's result and shape reference, which the caller gets in [Built.Shape]
 * and closes exactly once, after the body that uses the shape is gone. Stateless.
 */
internal class FoliageCompounds {
    sealed interface Built {
        class Shape(val result: AutoCloseable, val ref: ShapeRefC) : Built {
            /** Releases the compound; call once, after its body was removed and destroyed. */
            fun release() {
                ref.close()
                result.close()
            }
        }

        class Failed(val reason: String) : Built
    }

    fun build(placements: List<ColliderPlacement>): Built {
        if (placements.isEmpty()) return Built.Failed("a chunk has no colliding copies")
        val compound = StaticCompoundShapeSettings()
        try {
            for ((index, p) in placements.withIndex()) {
                val sub = subShape(p)
                val result = sub.create()
                try {
                    if (result.hasError()) return Built.Failed("Jolt refused copy $index's ${p.shape}: ${result.getError()}")
                    val ref = result.get()
                    try {
                        compound.addShape(Vec3(p.x, p.y, p.z), Quat(p.qx, p.qy, p.qz, p.qw), ref)
                    } finally {
                        ref.close()
                    }
                } finally {
                    result.close()
                    sub.close()
                }
            }
            val made = compound.create()
            if (made.hasError()) {
                val reason = made.getError()
                made.close()
                return Built.Failed("Jolt refused the compound of ${placements.size} copies: $reason")
            }
            return Built.Shape(made, made.get())
        } finally {
            compound.close()
        }
    }

    private fun subShape(p: ColliderPlacement): ShapeSettings = when (p.shape) {
        FoliageColliderShape.BOX -> BoxShapeSettings(
            Vec3(p.halfX, p.halfY, p.halfZ), min(Jolt.cDefaultConvexRadius, min(p.halfX, min(p.halfY, p.halfZ))),
        )
        FoliageColliderShape.SPHERE -> SphereShapeSettings(p.radius)
        FoliageColliderShape.CAPSULE -> CapsuleShapeSettings(p.halfHeight, p.radius)
    }
}
