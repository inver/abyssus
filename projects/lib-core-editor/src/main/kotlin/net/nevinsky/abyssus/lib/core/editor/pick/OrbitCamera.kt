/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.pick

import net.nevinsky.abyssus.lib.gdx.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.gdx.editor.content.Vec3

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

fun aspectOf(width: Int, height: Int): Float? = if (width > 0 && height > 0) width.toFloat() / height else null

private const val ORBIT_SPEED = 0.005f
private const val PAN_SPEED = 0.0015f
private const val ZOOM_BASE = 0.9f
private const val MIN_DISTANCE = 0.1f
private const val MAX_DISTANCE = 5000f
private const val MAX_PITCH = 1.5533f // 89 degrees
private const val DEFAULT_DISTANCE = 10f

/**
 * Orbit camera around [target]. The eye sits at `target + (cos p sin y, sin p, cos p cos y) * distance`,
 * so yaw 0 / pitch 0 looks down -Z.
 */
class OrbitCamera(var target: Vec3, var distance: Float, var yaw: Float, var pitch: Float) {
    /** An orbit [DEFAULT_DISTANCE] in front of [camera], looking where it looks. */
    constructor(camera: CameraParams) : this(
        Vec3(
            camera.position.x + camera.direction.x * DEFAULT_DISTANCE,
            camera.position.y + camera.direction.y * DEFAULT_DISTANCE,
            camera.position.z + camera.direction.z * DEFAULT_DISTANCE,
        ),
        DEFAULT_DISTANCE,
        atan2(-camera.direction.x, -camera.direction.z),
        asin((-camera.direction.y).coerceIn(-1f, 1f)).coerceIn(-MAX_PITCH, MAX_PITCH),
    )


    fun position(): Vec3 {
        val cp = cos(pitch)
        return Vec3(
            target.x + cp * sin(yaw) * distance,
            target.y + sin(pitch) * distance,
            target.z + cp * cos(yaw) * distance,
        )
    }

    fun orbit(dxPixels: Float, dyPixels: Float) {
        yaw -= dxPixels * ORBIT_SPEED
        pitch = (pitch + dyPixels * ORBIT_SPEED).coerceIn(-MAX_PITCH, MAX_PITCH)
    }

    fun pan(dxPixels: Float, dyPixels: Float) {
        val k = distance * PAN_SPEED
        val right = Vec3(cos(yaw), 0f, -sin(yaw))
        val up = Vec3(-sin(pitch) * sin(yaw), cos(pitch), -sin(pitch) * cos(yaw))
        target = Vec3(
            target.x - right.x * dxPixels * k + up.x * dyPixels * k,
            target.y - right.y * dxPixels * k + up.y * dyPixels * k,
            target.z - right.z * dxPixels * k + up.z * dyPixels * k,
        )
    }

    fun reset(camera: CameraParams) {
        val fresh = OrbitCamera(camera)
        target = fresh.target
        distance = fresh.distance
        yaw = fresh.yaw
        pitch = fresh.pitch
    }

    fun zoom(wheelClicks: Float) {
        distance = (distance * ZOOM_BASE.pow(wheelClicks)).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }
}
