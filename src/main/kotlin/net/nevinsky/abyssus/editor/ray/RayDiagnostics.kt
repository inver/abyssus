package net.nevinsky.abyssus.editor.ray

import net.nevinsky.abyssus.raytracing.RayBackendInfo
import net.nevinsky.abyssus.raytracing.RayBackend
import net.nevinsky.abyssus.raytracing.RayUnavailableReason

enum class RayModePhase { Off, Checking, Preparing, Active, Unavailable, Failed }

data class RayModeSnapshot(
    val phase: RayModePhase = RayModePhase.Off,
    val revision: Long = 0,
    val backendInfo: RayBackendInfo? = null,
    val failure: String? = null,
    val unavailable: RayBackendSelection.Unavailable? = null,
) {
    val requested: Boolean get() = phase == RayModePhase.Checking || phase == RayModePhase.Preparing || phase == RayModePhase.Active
    val active: Boolean get() = phase == RayModePhase.Active
    val toggleEnabled: Boolean get() = phase != RayModePhase.Unavailable
}


data class RayBackendAttempt(val backend: String, val reason: RayUnavailableReason, val detail: String? = null)

/** Structured diagnostics stay independent of UI text; the toolbar localizes them at its boundary. */
sealed interface RayBackendSelection {
    data object Off : RayBackendSelection
    data class Selected(val backend: RayBackend) : RayBackendSelection
    data class Unavailable(val attempts: List<RayBackendAttempt>, val invalidPreference: String? = null) : RayBackendSelection
}


enum class RaySceneFallback { ASSET_FAILURE, RESOURCE_LIMIT, UNSUPPORTED_GEOMETRY }
