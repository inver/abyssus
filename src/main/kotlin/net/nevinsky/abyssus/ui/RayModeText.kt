/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.ui

import net.nevinsky.abyssus.editor.ray.message
import net.nevinsky.abyssus.EditorBundle
import net.nevinsky.abyssus.editor.ray.RayModePhase
import net.nevinsky.abyssus.editor.ray.RayModeSnapshot
import net.nevinsky.abyssus.editor.ray.RayBackendAttempt
import net.nevinsky.abyssus.editor.ray.RayBackendSelection
import net.nevinsky.abyssus.editor.ray.RaySceneFallback

import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.raytracing.RayUnavailableReason

/** Localized wording for the ray tracing toolbar; the backend module itself only reports reason codes. */
internal object RayModeText {
    private fun backendName(name: String) = name.replaceFirstChar { it.uppercase() }

    fun reason(attempt: RayBackendAttempt): String {
        val name = backendName(attempt.backend)
        val key = when (attempt.reason) {
            RayUnavailableReason.ACCELERATION_STRUCTURES -> "rayReasonAccelerationStructures"
            RayUnavailableReason.RAY_QUERIES -> "rayReasonRayQueries"
            RayUnavailableReason.COLOR_DEPTH_READBACK -> "rayReasonColorDepthReadback"
            RayUnavailableReason.RESOURCE_LIMITS -> "rayReasonResourceLimits"
            RayUnavailableReason.RUNTIME_NOT_FOUND -> "rayReasonRuntimeNotFound"
            RayUnavailableReason.INITIALIZATION_FAILED -> "rayReasonInitializationFailed"
            RayUnavailableReason.DISABLED -> "rayReasonDisabled"
        }
        val text = AbyssusBundle.message(key, name)
        return attempt.detail?.takeIf { attempt.reason != RayUnavailableReason.DISABLED && it.isNotBlank() }?.let { "$text ($it)" } ?: text
    }

    /** Every probed backend's reason, or why none was probed. */
    fun reason(unavailable: RayBackendSelection.Unavailable): String {
        val invalid = unavailable.invalidPreference
        return when {
            invalid != null -> AbyssusBundle.message("rayReasonUnknownBackend", invalid)
            unavailable.attempts.isEmpty() -> AbyssusBundle.message("rayReasonNoBackend")
            else -> unavailable.attempts.joinToString("; ") { reason(it) }
        }
    }

    fun fallback(reason: RaySceneFallback): String = reason.message(EditorBundle)

    /** The inline label for the mode, or null when ray tracing is simply off. */
    fun status(snapshot: RayModeSnapshot): String? = when (snapshot.phase) {
        RayModePhase.Off -> null
        RayModePhase.Checking -> AbyssusBundle.message("sceneViewRayChecking")
        RayModePhase.Preparing -> AbyssusBundle.message("sceneViewRayPreparing", snapshot.backendInfo?.name ?: "")
        RayModePhase.Active -> AbyssusBundle.message("sceneViewRayActive", snapshot.backendInfo?.name ?: "")
        RayModePhase.Failed -> AbyssusBundle.message("sceneViewRayFailed", snapshot.failure ?: "")
        RayModePhase.Unavailable -> AbyssusBundle.message("sceneViewRayUnavailable")
    }

    /** The toggle's tooltip: the backend and GPU when usable, or the reason none is. */
    fun tooltip(snapshot: RayModeSnapshot): String = when (snapshot.phase) {
        RayModePhase.Unavailable -> AbyssusBundle.message("sceneViewRayUnavailableTooltip", snapshot.unavailable?.let(::reason) ?: "")
        RayModePhase.Active, RayModePhase.Preparing ->
            snapshot.backendInfo?.let { AbyssusBundle.message("sceneViewRayActiveTooltip", it.name, it.gpu) } ?: AbyssusBundle.message("sceneViewRayTracingTooltip")
        else -> AbyssusBundle.message("sceneViewRayTracingTooltip")
    }
}
