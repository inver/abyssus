/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.content.LightKind
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import kotlin.collections.contains
import kotlin.math.tan

enum class GizmoMode { SELECT, MOVE, ROTATE }

/** A world axis and the color its handle is drawn in. */
enum class GizmoAxis(val direction: Vector3, val red: Float, val green: Float, val blue: Float) {
    X(Vector3(1f, 0f, 0f), 0.9f, 0.2f, 0.2f),
    Y(Vector3(0f, 1f, 0f), 0.3f, 0.85f, 0.3f),
    Z(Vector3(0f, 0f, 1f), 0.25f, 0.45f, 0.95f),
}

/** A pair of gizmo axes whose plane a handle moves the object in. */
enum class GizmoPlane(val first: GizmoAxis, val second: GizmoAxis) {
    XY(GizmoAxis.X, GizmoAxis.Y),
    XZ(GizmoAxis.X, GizmoAxis.Z),
    YZ(GizmoAxis.Y, GizmoAxis.Z),
}

/** Where the plane handle's square starts and ends along each of its axes, as fractions of the gizmo size. */
const val PLANE_INNER = 0.25f
const val PLANE_OUTER = 0.5f

/** The handles' length (or ring radius) on screen. */
const val GIZMO_SIZE_PIXELS = 90f

/** One pixel's worth of world units at [origin] seen from [eye] with a vertical [fovDegrees] over [viewHeight] pixels. */
fun worldPerPixel(eye: Vector3, origin: Vector3, fovDegrees: Float, viewHeight: Int): Float {
    val distance = eye.cpy().dst(origin.cpy())
    return distance * 2f * tan(Math.toRadians(fovDegrees.toDouble() / 2.0)).toFloat() / viewHeight.coerceAtLeast(1)
}

/**
 * The handles of one gizmo: for [GizmoMode.MOVE] a segment from [origin] to `origin + axis * size` per axis, for
 * [GizmoMode.ROTATE] a circle of radius [size] around each axis. [worldPerPixel] is what one screen pixel is worth at
 * the origin's distance, so [size] and every tolerance stay constant on screen. No GL needed.
 */
class GizmoHandles(val origin: Vector3, val mode: GizmoMode, val worldPerPixel: Float, val axes: GizmoAxes = WORLD_AXES) {
    /** The gizmo at [origin] seen from [eye] with a vertical [fovDegrees] over [viewHeight] pixels. */
    constructor(origin: Vector3, mode: GizmoMode, eye: Vector3, fovDegrees: Float, viewHeight: Int, axes: GizmoAxes = WORLD_AXES) :
        this(origin, mode, worldPerPixel(eye, origin, fovDegrees, viewHeight), axes)

    val size: Float = GIZMO_SIZE_PIXELS * worldPerPixel

    /** The end of the arrow along [axis]. */
    fun tip(axis: GizmoAxis): Vector3 {
        val d = direction(axis)
        return Vector3(origin.x + d.x * size, origin.y + d.y * size, origin.z + d.z * size)
    }

    /** The corner of [plane]'s square at [a] and [b] (fractions of [size]) along its first and second axis. */
    fun planePoint(plane: GizmoPlane, a: Float, b: Float): Vector3 =
        Vector3(origin).mulAdd(direction(plane.first), a * size).mulAdd(direction(plane.second), b * size)

    /** The unit axis as a vector. */
    fun axisVector(axis: GizmoAxis): Vector3 = direction(axis).cpy()

    private fun direction(axis: GizmoAxis): Vector3 = axes[axis] ?: axis.direction
}

/**
 * Whether the entity [entityId] gets rotate handles. A camera that looks at an existing entity takes its direction
 * from it, and a point light has none, so they only move. A light that looks at something other than a direction
 * handle (an entity whose `type` is `HANDLE`) only moves too; turning it would mean moving an
 * unrelated object. A light aimed at a handle, and one with no resolving `lookAtId`, keep their rings.
 */
fun canRotate(content: SceneContent, entityId: String): Boolean {
    val camera = content.cameras.firstOrNull { it.entityId == entityId }
    if (camera != null) return camera.lookAtId?.let(content.entityPositions::containsKey) != true
    val light = content.lights.firstOrNull { it.entityId == entityId } ?: return true
    if (light.kind == LightKind.POINT) return false
    val lookAt = light.lookAtId ?: return true
    return lookAt !in content.entityPositions || content.aimHandleOf(light) != null
}
