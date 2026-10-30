/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

data class LightingSunDirection(val x: Double, val y: Double, val z: Double)

data class LightingRevision(
    val skyIdentity: String,
    val skyRevision: Long,
    val cloudIdentity: String?,
    val cloudRevision: Long,
    val cloudsVisible: Boolean,
)

data class LightingRefreshRequest(
    val sun: LightingSunDirection,
    val revision: LightingRevision,
    val cloudTimeSeconds: Double,
    val cloudsDrift: Boolean,
)

data class LightingBuildRequest(val id: Long, val request: LightingRefreshRequest)

data class LightingRefreshState(
    val current: LightingBuildRequest?,
    val next: LightingBuildRequest?,
    val build: LightingBuildRequest?,
    val fadeWeight: Double,
    val canStepBuild: Boolean,
)

private const val SUN_THRESHOLD_DEGREES = 1.0
/** Seconds of cloud time between refreshes of anything built from drifting clouds (sky light, ray sky bakes). */
const val CLOUD_DRIFT_REFRESH_SECONDS = 2.0
private const val FADE_SECONDS = 1.0
private const val EPSILON = 1e-9

/**
 * Decides when the procedural lighting is rebuilt and how the result fades in. Pure: no GL, no clock; the caller feeds
 * it the sky's own time. Holds at most the current and next results plus one active build; newer requests only replace
 * the stored target, so they coalesce. A first build is published at once, later ones fade in over one second.
 * A failed build keeps the current result and is retried after [retryDelaySeconds] of visible time, at most
 * [maxRetries] times, until the revision changes.
 */
class LightingRefreshPolicy(private val maxRetries: Int = 2, private val retryDelaySeconds: Double = 2.0) {
    private var current: LightingBuildRequest? = null
    private var next: LightingBuildRequest? = null
    private var build: LightingBuildRequest? = null
    private var fade = 0.0
    private var visible = false
    private var nextId = 1L
    private var failures = 0
    private var backoff = 0.0
    private var attemptRevision: LightingRevision? = null

    val state: LightingRefreshState
        get() = LightingRefreshState(current, next, build, if (next != null) fade else 0.0, build != null && visible)

    fun update(request: LightingRefreshRequest, visible: Boolean, deltaSeconds: Double): LightingRefreshState {
        this.visible = visible
        val revision = request.revision
        if (next?.request?.revision.let { it != null && it != revision }) {
            next = null
            fade = 0.0
        }
        if (build?.request?.revision.let { it != null && it != revision }) build = null
        if (attemptRevision != revision) {
            failures = 0
            backoff = 0.0
        }
        if (visible) {
            if (next != null) {
                fade += deltaSeconds / FADE_SECONDS
                if (fade >= 1.0 - EPSILON) {
                    current = next
                    next = null
                    fade = 0.0
                }
            }
            if (failures > 0 && build == null) backoff += deltaSeconds
            if (build == null && next == null && failures <= maxRetries &&
                (failures == 0 || backoff >= retryDelaySeconds - EPSILON) && needsBuild(request)
            ) {
                build = LightingBuildRequest(nextId++, request)
                attemptRevision = revision
                backoff = 0.0
            }
        }
        return state
    }

    /** Publishes [build] if it is still the active one; returns false for an obsolete build. */
    fun completed(build: LightingBuildRequest): Boolean {
        if (this.build != build) return false
        this.build = null
        failures = 0
        backoff = 0.0
        if (current == null) {
            current = build
        } else {
            next = build
            fade = 0.0
        }
        return true
    }

    /** Records a failure of [build] if it is still the active one; returns false for an obsolete build. */
    fun failed(build: LightingBuildRequest): Boolean {
        if (this.build != build) return false
        this.build = null
        failures++
        backoff = 0.0
        return true
    }

    /** Forgets every result and build, as after a lost GL context. Build ids keep increasing. */
    fun resetContext() {
        current = null
        next = null
        build = null
        fade = 0.0
        failures = 0
        backoff = 0.0
        attemptRevision = null
    }

    private fun needsBuild(request: LightingRefreshRequest): Boolean {
        val basis = current?.request ?: return true
        if (basis.revision != request.revision) return true
        if (angleDegrees(basis.sun, request.sun) >= SUN_THRESHOLD_DEGREES - EPSILON) return true
        return request.cloudsDrift && request.revision.cloudsVisible &&
            Math.abs(request.cloudTimeSeconds - basis.cloudTimeSeconds) >= CLOUD_DRIFT_REFRESH_SECONDS - EPSILON
    }

    private fun angleDegrees(a: LightingSunDirection, b: LightingSunDirection): Double {
        val length = Math.sqrt((a.x * a.x + a.y * a.y + a.z * a.z) * (b.x * b.x + b.y * b.y + b.z * b.z))
        if (length == 0.0) return 0.0
        val cos = ((a.x * b.x + a.y * b.y + a.z * b.z) / length).coerceIn(-1.0, 1.0)
        return Math.toDegrees(Math.acos(cos))
    }
}
