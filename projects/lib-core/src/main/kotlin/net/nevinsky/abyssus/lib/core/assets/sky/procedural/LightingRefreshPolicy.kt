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

class LightingRefreshPolicy(private val maxRetries: Int = 2, private val retryDelaySeconds: Double = 2.0) {
    val state: LightingRefreshState get() = LightingRefreshState(null, null, null, 0.0, false)
    fun update(request: LightingRefreshRequest, visible: Boolean, deltaSeconds: Double): LightingRefreshState = state
    fun completed(build: LightingBuildRequest): Boolean = false
    fun failed(build: LightingBuildRequest): Boolean = false
    fun resetContext() = Unit
}
