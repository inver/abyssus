/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ray

import net.nevinsky.abyssus.app.game.controlline.RayFieldResult
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.ray.RayBackendSelection
import net.nevinsky.abyssus.lib.core.editor.ray.RayBackendService
import net.nevinsky.abyssus.lib.core.editor.ray.rayBackendSelectorFromStartup
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.raytracing.MetalRayBackendFactory
import net.nevinsky.abyssus.lib.raytracing.RayLimits
import net.nevinsky.abyssus.lib.raytracing.RayQualityLimits
import net.nevinsky.abyssus.lib.raytracing.VulkanRayBackendFactory
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.Executors

/** The reflective entry point for the opt-in ray-traced field view, loaded by `Main` from the `ray` source set. */
object RayFieldEntry {
    private val log = LoggerFactory.getLogger("control-line.ray")

    /**
     * Creates the ray field view for [project], or [RayFieldResult.Unavailable] with the reason when no backend is
     * usable. The backend is probed here, before the window exists, so the game can start the raster field at once
     * and name the reason; the service reuses the probed backend.
     */
    @JvmStatic
    fun fieldView(project: Path): RayFieldResult {
        val selector = rayBackendSelectorFromStartup(
            providers = mapOf(
                "metal" to { MetalRayBackendFactory(log = log) },
                "vulkan" to { VulkanRayBackendFactory(log = log) },
            ),
            log = log,
        )
        // the backend belongs to the thread that probes it: probe on the service's own native worker
        val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "abyssus-ray-worker").apply { isDaemon = true } }
        val selection = try {
            worker.submit<RayBackendSelection> { selector.select() }.get()
        } catch (e: Exception) {
            worker.shutdownNow()
            return RayFieldResult.Unavailable("ray probe failed: ${e.cause?.message ?: e.message ?: e}")
        }
        when (selection) {
            RayBackendSelection.Off -> return RayFieldResult.Unavailable("ray tracing is switched off").also { worker.shutdownNow() }
            is RayBackendSelection.Unavailable -> return RayFieldResult.Unavailable(
                selection.attempts.joinToString("; ") { "${it.backend}: ${it.reason}${it.detail?.let { d -> " ($d)" } ?: ""}" }
                    .ifEmpty { selection.invalidPreference ?: "no ray backend for this OS" },
            ).also { worker.shutdownNow() }
            is RayBackendSelection.Selected -> Unit
        }
        val service = RayBackendService(selector, ResourceEditorMessages(), worker = worker, publish = { it() }, log = log)
        val executor = Executors.newFixedThreadPool(2) { r -> Thread(r, "ray-asset-prepare").apply { isDaemon = true } }
        return try {
            val assets = RayFieldAssets(project.toFile(), JsonProcessor(log), log, executor)
            val view = service.newView<Unit>("field", RayLimits(), RayQualityLimits())
            RayFieldResult.Available(RayFieldPresenter(assets, RayFieldRuntime(view), service, executor))
        } catch (e: RuntimeException) {
            service.close()
            executor.shutdownNow()
            RayFieldResult.Unavailable("ray field view could not start: ${e.message ?: e}")
        }
    }
}
