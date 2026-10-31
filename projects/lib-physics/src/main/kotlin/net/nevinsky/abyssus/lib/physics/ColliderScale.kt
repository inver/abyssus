/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.physics

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.max

/**
 * How an entity's scale applies to its collider: a box takes the absolute scale per axis, a sphere or capsule takes the
 * largest axis for its radius and half height. The physics world builds shapes by these rules and the editor draws them,
 * so both read them here.
 */

/** The largest absolute axis of [scale]. */
fun largestScaleAxis(scale: Vector3): Float = max(abs(scale.x), max(abs(scale.y), abs(scale.z)))

/** The half extents of a box collider with [halfExtents] under [scale]. */
fun scaledBoxHalfExtents(halfExtents: Vector3, scale: Vector3): Vector3 =
    Vector3(halfExtents).scl(abs(scale.x), abs(scale.y), abs(scale.z))

/** The radius of a sphere or capsule collider with [radius] under [scale]. */
fun scaledRadius(radius: Float, scale: Vector3): Float = radius * largestScaleAxis(scale)

/** The half height of a capsule collider with [halfHeight] under [scale]. */
fun scaledHalfHeight(halfHeight: Float, scale: Vector3): Float = halfHeight * largestScaleAxis(scale)
