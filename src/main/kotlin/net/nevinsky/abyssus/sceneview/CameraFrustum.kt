/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.math.Vector3
import kotlin.math.tan

internal fun Vec3.toVector3() = Vector3(x, y, z)

internal fun Vector3.toVec3() = Vec3(x, y, z)

/**
 * A camera's view volume. [corners] are the near plane's four corners followed by the far plane's, each ring going
 * (left, bottom), (right, bottom), (right, top), (left, top) as seen from the camera. [direction] is the unit view
 * direction. No GL needed.
 */
class CameraFrustum(val direction: Vec3, val corners: List<Vec3>) {
    companion object {
        private val FORWARD = Vec3(0f, 0f, -1f)

        /**
         * The unit direction the camera looks along: toward the position of its `lookAtId` entity in [positions] when
         * that resolves to somewhere other than the camera itself, else its own `direction`.
         */
        fun directionOf(placement: CameraPlacement, positions: Map<String, Vec3>): Vec3 {
            val target = placement.lookAtId?.let(positions::get)
            if (target != null) {
                val toTarget = target.toVector3().sub(placement.position.toVector3())
                if (toTarget.len2() > 1e-12f) return toTarget.nor().toVec3()
            }
            return normalized(placement.direction) ?: FORWARD
        }

        /** The camera's right and up axes for the unit view direction [d]; world Y is up unless looking (almost) straight along it. */
        internal fun sideAxes(d: Vector3): Pair<Vector3, Vector3> {
            val up = if (kotlin.math.abs(d.y) > 0.999f) Vector3.Z else Vector3.Y
            val right = Vector3(d).crs(up).nor()
            return right to Vector3(right).crs(d).nor()
        }

        /** The view volume of [placement] for a viewport of [aspect] (width over height). */
        fun of(placement: CameraPlacement, positions: Map<String, Vec3>, aspect: Float): CameraFrustum {
            val direction = directionOf(placement, positions)
            val d = direction.toVector3()
            val (right, top) = sideAxes(d)
            val eye = placement.position.toVector3()
            val halfTan = tan(Math.toRadians(placement.fieldOfView.toDouble() / 2.0)).toFloat()
            val corners = ArrayList<Vec3>(8)
            for (distance in listOf(placement.near, placement.far)) {
                val halfH = halfTan * distance
                val halfW = halfH * aspect
                val center = Vector3(d).scl(distance).add(eye)
                for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
                    corners += Vector3(center).mulAdd(right, sx * halfW).mulAdd(top, sy * halfH).toVec3()
                }
            }
            return CameraFrustum(direction, corners)
        }
    }
}
