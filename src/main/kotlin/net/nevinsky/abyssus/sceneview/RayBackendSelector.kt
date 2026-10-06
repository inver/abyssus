/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.ray.RayBackendAttempt
import net.nevinsky.abyssus.editor.ray.RayBackendSelection

import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import net.nevinsky.abyssus.raytracing.*
import java.util.Locale

/**
 * Application-owned selection cache. Constructing a selector never constructs a native provider. Selection and
 * invalidation belong to the application's serial native worker; views cannot cause probes by rendering a frame.
 */
internal class RayBackendSelector(
    requestedBackend: String?,
    private val osName: String,
    providers: Map<String, () -> RayBackendProvider>,
    private val log: Logger = NOPLogger.NOP_LOGGER,
) {
    private val preference = (requestedBackend ?: "auto").trim().lowercase(Locale.ROOT)
    private val factories = providers.mapKeys { it.key.lowercase(Locale.ROOT) }
    private val initialized = mutableMapOf<String, RayBackendProvider>()
    private val cached = mutableMapOf<String, RayCapability>()
    private val lost = mutableSetOf<String>()

    fun select(retry: Boolean = false): RayBackendSelection {
        if (preference == "off") return RayBackendSelection.Off
        val names = when (preference) {
            "metal", "vulkan" -> listOf(preference)
            "auto" -> when {
                osName.startsWith("Mac", ignoreCase = true) -> listOf("metal", "vulkan")
                osName.startsWith("Windows", ignoreCase = true) || osName.equals("Linux", ignoreCase = true) -> listOf("vulkan")
                else -> emptyList()
            }
            else -> return RayBackendSelection.Unavailable(emptyList(), preference)
        }
        if (retry) {
            lost.forEach { name -> cached.remove(name); initialized.remove(name) }
            lost.clear()
        }
        val attempts = mutableListOf<RayBackendAttempt>()
        for (name in names) {
            when (val capability = cached.getOrPut(name) { probe(name) }) {
                is RayCapability.Available -> return RayBackendSelection.Selected(capability.backend)
                is RayCapability.Unavailable -> attempts += RayBackendAttempt(name, capability.reason, capability.detail)
            }
        }
        return RayBackendSelection.Unavailable(attempts.toList())
    }

    /** Marks the cache unusable immediately. Only an explicit retry evicts a lost probe result. */
    fun markDeviceLost(backend: RayBackend, detail: String?) {
        cached.entries.filter { (_, capability) -> capability is RayCapability.Available && capability.backend === backend }
            .map { it.key }.forEach { name ->
                cached[name] = RayCapability.Unavailable(RayUnavailableReason.INITIALIZATION_FAILED, detail)
                lost += name
            }
    }

    private fun probe(name: String): RayCapability {
        val factory = factories[name] ?: return RayCapability.Unavailable(RayUnavailableReason.RUNTIME_NOT_FOUND).also {
            log.warn("Ray tracing backend '$name' is not registered")
        }
        log.info("Probing the '$name' ray tracing backend (preference '$preference', os '$osName')")
        val capability = runCatchingKeepingCancellation {
            initialized.getOrPut(name, factory).probe()
        }.getOrElse { failure ->
            log.warn("Probing the '$name' ray tracing backend threw", failure)
            RayCapability.Unavailable(
                if (failure is UnsatisfiedLinkError) RayUnavailableReason.RUNTIME_NOT_FOUND else RayUnavailableReason.INITIALIZATION_FAILED,
                failure.message,
            )
        }
        when (capability) {
            is RayCapability.Available -> log.info("Ray tracing backend '$name' is available: ${capability.backend.info}, ${capability.backend.capabilities}")
            is RayCapability.Unavailable -> log.warn("Ray tracing backend '$name' is unavailable: ${capability.reason}${capability.detail?.let { " ($it)" } ?: ""}")
        }
        return capability
    }

    companion object {
        /** The composition root calls this once at startup, before any scene view or native work exists. */
        fun fromStartup(
            property: () -> String? = { System.getProperty("abyssus.raytracing.backend") },
            osName: String = System.getProperty("os.name"),
            providers: Map<String, () -> RayBackendProvider>,
            log: Logger = NOPLogger.NOP_LOGGER,
        ) = RayBackendSelector(property(), osName, providers, log)
    }
}
