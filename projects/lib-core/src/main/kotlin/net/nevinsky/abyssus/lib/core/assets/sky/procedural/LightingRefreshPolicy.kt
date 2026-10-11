/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.acos

/** The sun moving more than this many degrees from the last lighting makes it stale. */
const val LIGHTING_SUN_THRESHOLD_DEGREES = 1.0

/** Seconds of cloud time after which drifting clouds make the last lighting stale. */
const val LIGHTING_DRIFT_PERIOD_SECONDS = 2.0

/** Seconds over which a new lighting replaces the last one. */
const val LIGHTING_FADE_SECONDS = 1.0

/**
 * What the policy sees of one frame of a lit procedural sky: where the [sun] is, the sky clock's [time], which revision
 * of the sky's cloud asset is displayed ([cloudRevision]: it changes when the asset is reloaded, replaced or removed) and
 * whether it draws clouds at all ([cloudsVisible]), whether those clouds [drifting] (some band has wind), and whether the
 * view is on screen ([visible]).
 */
class LightingFrame(
    val sun: Vector3,
    val time: Double,
    val cloudRevision: Long,
    val cloudsVisible: Boolean,
    val drifting: Boolean,
    val visible: Boolean = true,
)

/**
 * What to do this frame. [build] is the ticket of a build to start now (null: none); [abandon] says the build in progress
 * answers an outdated question and must be disposed; [weight] is how far the lighting is faded from the current
 * environment (0) to the next (1); [promoted] says that the fade ended, so the next environment is now the current one.
 */
class LightingStep(val build: Int?, val abandon: Boolean, val weight: Float, val promoted: Boolean)

/** What to do with a finished build. */
enum class Completion {
    /** The result is stale or for an unknown build: dispose it. */
    DISCARD,

    /** There was no lighting before, or fades are off: use the result as the current environment at once. */
    PUBLISH,

    /** Use the result as the next environment and fade to it. */
    FADE,
}

/**
 * When a procedural sky's lighting is rebuilt, and how its new version fades in. Pure: no GL, no clock of its own, no
 * Swing; the owner calls [update] once per frame with the sky's clock and does the GPU work.
 *
 * Lighting goes stale when the sun moves more than [sunThresholdDegrees] from where it was built, when the cloud asset
 * revision or visibility changes, or (while the clouds [LightingFrame.drifting]) every [driftPeriodSeconds] of cloud time;
 * stationary clouds are never rebuilt for time. A build runs only when nothing else does: one build, then the fade over
 * [fadeSeconds] (none when it is 0: a finished build is published at once), then idle, so state stays bounded to the current environment, the next one and one build. Requests that
 * arrive meanwhile coalesce, because staleness is measured against the frame at the moment a build can start. A hidden
 * view starts nothing and its fade does not advance.
 */
class LightingRefreshPolicy(
    private val sunThresholdDegrees: Double = LIGHTING_SUN_THRESHOLD_DEGREES,
    private val driftPeriodSeconds: Double = LIGHTING_DRIFT_PERIOD_SECONDS,
    private val fadeSeconds: Double = LIGHTING_FADE_SECONDS,
) {
    private enum class Phase { IDLE, BUILDING, FADING }

    private class Key(val sun: Vector3, val time: Double, val revision: Long, val visible: Boolean)

    private var phase = Phase.IDLE
    private var tickets = 0
    private var active = 0
    private var applied: Key? = null
    private var building: Key? = null
    private var hasCurrent = false
    private var fade = 0.0
    private var lastTime = Double.NaN
    private var latest: LightingFrame? = null

    /** True while a build is in progress. */
    val isBuilding: Boolean get() = phase == Phase.BUILDING

    /** True while the lighting fades. */
    val isFading: Boolean get() = phase == Phase.FADING

    /** Advances to [frame] and says what to do. */
    fun update(frame: LightingFrame): LightingStep {
        latest = frame
        val dt = if (lastTime.isNaN()) 0.0 else abs(frame.time - lastTime)
        lastTime = frame.time
        if (!frame.visible) return LightingStep(null, false, weight(), false)

        var promoted = false
        var abandon = false
        if (phase == Phase.FADING) {
            fade += dt
            if (fade >= fadeSeconds) {
                phase = Phase.IDLE
                promoted = true
            }
        }
        if (phase == Phase.BUILDING && building!!.let { outdated(it, frame) }) {
            // the question changed under the build (a reload, a visibility change): its answer is worthless
            active = 0
            building = null
            phase = Phase.IDLE
            abandon = true
        }
        var ticket: Int? = null
        if (phase == Phase.IDLE && stale(frame, applied)) {
            ticket = ++tickets
            active = ticket
            building = key(frame)
            phase = Phase.BUILDING
        }
        return LightingStep(ticket, abandon, weight(), promoted)
    }

    /** Says what to do with the result of build [ticket], which just finished. */
    fun completed(ticket: Int): Completion {
        if (ticket != active || phase != Phase.BUILDING) return Completion.DISCARD
        val key = building!!
        active = 0
        building = null
        if (latest?.let { outdated(key, it) } == true) {
            phase = Phase.IDLE
            return Completion.DISCARD
        }
        applied = key
        if (!hasCurrent || fadeSeconds <= 0.0) {
            hasCurrent = true
            phase = Phase.IDLE
            return Completion.PUBLISH
        }
        phase = Phase.FADING
        fade = 0.0
        return Completion.FADE
    }

    /**
     * Records that build [ticket] failed. The failed question counts as asked, so it is not retried until the sun, the
     * clouds or the revision change again; the lighting in use stays.
     */
    fun failed(ticket: Int) {
        if (ticket != active || phase != Phase.BUILDING) return
        applied = building
        active = 0
        building = null
        phase = Phase.IDLE
    }

    /** Forgets everything: the sky was replaced or the GL context was lost, and all environments are gone. */
    fun reset() {
        phase = Phase.IDLE
        active = 0
        applied = null
        building = null
        hasCurrent = false
        fade = 0.0
        lastTime = Double.NaN
        latest = null
    }

    private fun weight(): Float = if (phase == Phase.FADING) (fade / fadeSeconds).coerceIn(0.0, 1.0).toFloat() else 0f

    private fun key(frame: LightingFrame) = Key(Vector3(frame.sun), frame.time, frame.cloudRevision, frame.cloudsVisible)

    private fun outdated(key: Key, frame: LightingFrame) =
        key.revision != frame.cloudRevision || key.visible != frame.cloudsVisible

    private fun stale(frame: LightingFrame, key: Key?): Boolean {
        if (key == null || outdated(key, frame)) return true
        val cos = key.sun.dot(frame.sun).toDouble().coerceIn(-1.0, 1.0)
        if (Math.toDegrees(acos(cos)) > sunThresholdDegrees) return true
        return frame.drifting && frame.cloudsVisible && abs(frame.time - key.time) >= driftPeriodSeconds
    }
}
