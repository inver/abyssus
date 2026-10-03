/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview.gizmo

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.sceneview.LightKind
import net.nevinsky.abyssus.sceneview.SceneContent
import net.nevinsky.abyssus.sceneview.Vec3
import net.nevinsky.abyssus.sceneview.toVector3
import kotlin.math.tan

enum class GizmoMode { MOVE, ROTATE }

/** A world axis and the color its handle is drawn in. */
enum class GizmoAxis(val direction: Vec3, val red: Float, val green: Float, val blue: Float) {
    X(Vec3(1f, 0f, 0f), 0.9f, 0.2f, 0.2f),
    Y(Vec3(0f, 1f, 0f), 0.3f, 0.85f, 0.3f),
    Z(Vec3(0f, 0f, 1f), 0.25f, 0.45f, 0.95f),
}

/**
 * The handles of one gizmo: for [GizmoMode.MOVE] a segment from [origin] to `origin + axis * size` per axis, for
 * [GizmoMode.ROTATE] a circle of radius [size] around each axis. [worldPerPixel] is what one screen pixel is worth at
 * the origin's distance, so [size] and every tolerance stay constant on screen. No GL needed.
 */
class GizmoHandles(val origin: Vec3, val mode: GizmoMode, val worldPerPixel: Float) {
    val size: Float = SIZE_PIXELS * worldPerPixel

    /** The end of the arrow along [axis]. */
    fun tip(axis: GizmoAxis): Vec3 {
        val d = axis.direction
        return Vec3(origin.x + d.x * size, origin.y + d.y * size, origin.z + d.z * size)
    }

    /** The unit axis as a vector. */
    internal fun axisVector(axis: GizmoAxis): Vector3 = axis.direction.toVector3()

    companion object {
        /** The handles' length (or ring radius) on screen. */
        const val SIZE_PIXELS = 90f

        /** One pixel's worth of world units at [origin] seen from [eye] with a vertical [fovDegrees] over [viewHeight] pixels. */
        fun worldPerPixel(eye: Vec3, origin: Vec3, fovDegrees: Float, viewHeight: Int): Float {
            val distance = eye.toVector3().dst(origin.toVector3())
            return distance * 2f * tan(Math.toRadians(fovDegrees.toDouble() / 2.0)).toFloat() / viewHeight.coerceAtLeast(1)
        }

        fun of(origin: Vec3, mode: GizmoMode, eye: Vec3, fovDegrees: Float, viewHeight: Int) =
            GizmoHandles(origin, mode, worldPerPixel(eye, origin, fovDegrees, viewHeight))
    }
}

/**
 * Whether the entity [entityId] gets rotate handles. A camera that looks at an existing entity takes its direction
 * from it, and a point light has none, so they only move. A light that looks at something other than a direction
 * handle (an entity whose `TypeComponent.type` is `HANDLE`) only moves too; turning it would mean moving an
 * unrelated object. A light aimed at a handle, and one with no resolving `lookAtId`, keep their rings.
 */
fun canRotate(content: SceneContent, entityId: String): Boolean {
    val camera = content.cameras.firstOrNull { it.entityId == entityId }
    if (camera != null) return camera.lookAtId?.let(content.entityPositions::containsKey) != true
    val light = content.lights.firstOrNull { it.entityId == entityId } ?: return true
    if (light.kind == LightKind.POINT) return false
    val lookAt = light.lookAtId ?: return true
    return lookAt !in content.entityPositions || lookAt in content.handleIds
}
