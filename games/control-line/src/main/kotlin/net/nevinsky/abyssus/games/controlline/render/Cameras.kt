/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.render

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.min

/** The pilot's eyes, above the ground under the handle (m). */
const val EYE_HEIGHT = 1.65f

/** How quickly the plane-select camera glides to the next plane (per second). */
private const val GLIDE = 4f

/**
 * Where the camera is on each screen. Flying: at the pilot's eyes, turning to follow the plane. Plane select: in front
 * of the chosen parked plane, gliding between them. Menus: slowly circling the field.
 */
class Cameras(width: Float, height: Float) {
    val camera = PerspectiveCamera(67f, width, height).apply {
        near = 0.1f
        far = 600f
    }
    private val eye = Vector3()
    private val target = Vector3()
    private var menuAngle = 0f
    private var gliding = false

    fun resize(width: Float, height: Float) {
        camera.viewportWidth = width
        camera.viewportHeight = height
        camera.update()
    }

    /** At [pilot]'s eyes, looking at [plane]. */
    fun flying(pilot: Vector3, plane: Vector3) {
        gliding = false
        eye.set(pilot).add(0f, EYE_HEIGHT, 0f)
        target.set(plane)
        apply()
    }

    /** Gliding over [seconds] towards the front of the parked plane at [plane], which faces along [nose]. */
    fun planeSelect(plane: Vector3, nose: Vector3, seconds: Float) {
        val side = Vector3(nose).crs(Vector3.Y).nor()
        val wantedEye = Vector3(plane).mulAdd(nose, 2.4f).mulAdd(side, 1.4f).add(0f, 0.9f, 0f)
        if (!gliding) {
            eye.set(wantedEye)
            target.set(plane)
            gliding = true
        }
        val t = min(1f, GLIDE * seconds)
        eye.lerp(wantedEye, t)
        target.lerp(plane, t)
        apply()
    }

    /** Circling the field's centre [centre] over [seconds]. */
    fun menu(centre: Vector3, seconds: Float) {
        gliding = false
        menuAngle += seconds * 4f
        eye.set(centre).add(MathUtils.cosDeg(menuAngle) * 14f, 2.5f, MathUtils.sinDeg(menuAngle) * 14f)
        target.set(centre).add(0f, 1.5f, 0f)
        apply()
    }

    private fun apply() {
        camera.position.set(eye)
        camera.up.set(Vector3.Y)
        camera.lookAt(target)
        camera.update()
    }
}
