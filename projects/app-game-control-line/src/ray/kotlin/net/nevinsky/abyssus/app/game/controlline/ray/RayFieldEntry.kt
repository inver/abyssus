/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ray

import net.nevinsky.abyssus.app.game.controlline.RayFieldResult
import net.nevinsky.abyssus.lib.raytracing.RayBackend
import net.nevinsky.abyssus.lib.raytracing.RayCapability
import net.nevinsky.abyssus.lib.raytracing.RayLimits
import net.nevinsky.abyssus.lib.raytracing.RayUnavailableReason
import net.nevinsky.abyssus.lib.raytracing.VulkanRayBackendFactory
import java.nio.file.Path

/** The reflective entry point for the opt-in ray-traced field view, loaded by `Main` from the `ray` source set. */
object RayFieldEntry {
    /** Creates the ray field view for the bundled project, or [RayFieldResult.Unavailable] when no backend is usable. */
    fun fieldView(project: Path): RayFieldResult {
        return try {
            val factory = VulkanRayBackendFactory()
            when (val result = factory.probe()) {
                is RayCapability.Available -> {
                    // The backend and its session outlive this call: the presenter owns and disposes them.
                    val backend = result.backend
                    val session = backend.openSession("field", RayLimits())
                    RayFieldResult.Available(RayFieldPresenter(project, backend, session))
                }
                is RayCapability.Unavailable -> {
                    RayFieldResult.Unavailable(reasonText(result.reason))
                }
            }
        } catch (e: Exception) {
            RayFieldResult.Unavailable("ray field view could not start: ${e.message ?: e.toString()}")
        }
    }

    private fun reasonText(reason: RayUnavailableReason): String = when (reason) {
        RayUnavailableReason.RUNTIME_NOT_FOUND -> "no Vulkan loader or runtime"
        RayUnavailableReason.INITIALIZATION_FAILED -> "Vulkan initialization failed"
        RayUnavailableReason.DISABLED -> "ray tracing is disabled for this session"
        else -> "ray-tracing backend unavailable (${reason.name})"
    }
}
