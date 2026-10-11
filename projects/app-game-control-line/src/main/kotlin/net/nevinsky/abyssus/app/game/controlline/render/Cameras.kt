/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefFrame
import net.nevinsky.abyssus.app.game.controlline.pattern.DebriefView
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min

/** The pilot's eyes, above the ground under the handle (m). */
const val EYE_HEIGHT = 1.65f

/** The field of view of every camera but the debrief's (degrees). */
private const val DEFAULT_FOV = 67f

/** The debrief widens its field of view up to this much to fit a figure (degrees), and never narrows it below [DEBRIEF_MIN_FOV]. */
const val DEBRIEF_MAX_FOV = 100f
const val DEBRIEF_MIN_FOV = 30f

/** The debrief's outside camera stands this many line lengths from the pilot, [OUTSIDE_HEIGHT] metres up. */
const val OUTSIDE_DISTANCE = 1.6f
const val OUTSIDE_HEIGHT = 6f

/** The picture is this much wider than the figure it fits. */
private const val DEBRIEF_MARGIN = 1.08f

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

    /**
     * The debrief's camera on [frame], a figure flown round the pilot at [pilot]'s feet (design decision 4): from the
     * pilot's eyes, or from outside the circle on the figure's azimuth, looking at the middle of the figure. The field of
     * view is widened, up to [DEBRIEF_MAX_FOV], to take in all of its points.
     */
    fun debrief(view: DebriefView, pilot: Vector3, frame: DebriefFrame) {
        gliding = false
        val azimuth = frame.azimuth * MathUtils.degreesToRadians
        val outward = Vector3(MathUtils.cos(azimuth), 0f, -MathUtils.sin(azimuth))
        when (view) {
            DebriefView.PILOT -> eye.set(pilot).add(0f, EYE_HEIGHT, 0f)
            DebriefView.OUTSIDE -> eye.set(pilot).mulAdd(outward, OUTSIDE_DISTANCE * frame.lineLength).add(0f, OUTSIDE_HEIGHT, 0f)
        }
        target.set(pilot).add(frame.centre)
        var fit = fit(frame, pilot, outward)
        if (fit.fov >= DEBRIEF_MAX_FOV) {
            // too wide from the middle of the figure: aim at the middle of what the eye sees of it instead
            target.set(eye).add(enclosingAxis(frame, pilot))
            fit = fit(frame, pilot, outward)
        }
        apply(fit.fov, fit.up)
    }

    private class Fit(val fov: Float, val up: Vector3)

    /** The field of view that takes in the [frame] from the eye looking at the target, and the camera's up there. */
    private fun fit(frame: DebriefFrame, pilot: Vector3, outward: Vector3): Fit {
        val forward = Vector3(target).sub(eye).nor()
        // looking straight up, the picture's top is away from the figure's side
        val up = if (abs(forward.y) > 0.99f) Vector3(outward).scl(-forward.y) else Vector3.Y
        val right = Vector3(forward).crs(up).nor()
        val top = Vector3(right).crs(forward).nor()
        val aspect = camera.viewportWidth / camera.viewportHeight
        var need = 0f
        val v = Vector3()
        for (point in frame.points) {
            v.set(pilot).add(point).sub(eye)
            val depth = v.dot(forward)
            if (depth < 0.05f) {
                need = Float.MAX_VALUE
                break
            }
            need = max(need, max(abs(v.dot(top)) / depth, abs(v.dot(right)) / (depth * aspect)))
        }
        val fov = (2f * atan(need * DEBRIEF_MARGIN) * MathUtils.radiansToDegrees).coerceIn(DEBRIEF_MIN_FOV, DEBRIEF_MAX_FOV)
        return Fit(fov, up)
    }

    /** The direction from the eye in the middle of the cone that holds the [frame]'s points (an approximate smallest one). */
    private fun enclosingAxis(frame: DebriefFrame, pilot: Vector3): Vector3 {
        val directions = frame.points.map { Vector3(pilot).add(it).sub(eye).nor() }
        val axis = Vector3()
        directions.forEach { axis.add(it) }
        axis.nor()
        if (axis.isZero) axis.set(Vector3(target).sub(eye).nor())
        for (i in 1..40) {
            val farthest = directions.minByOrNull { it.dot(axis) } ?: break
            axis.lerp(farthest, 1f / (i + 1)).nor()
        }
        return axis
    }

    private fun apply(fov: Float = DEFAULT_FOV, up: Vector3 = Vector3.Y) {
        camera.fieldOfView = fov
        camera.position.set(eye)
        camera.up.set(up)
        camera.lookAt(target)
        camera.update()
    }
}
