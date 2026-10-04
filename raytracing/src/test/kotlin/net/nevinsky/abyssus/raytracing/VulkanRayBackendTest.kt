/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

/**
 * Opt-in device test: `-Dabyssus.vulkanTests=true`. With `-Dabyssus.raytracing.validation=true` the Khronos validation
 * layer is enabled and any validation error fails the test that caused it.
 */
class VulkanRayBackendTest : RayBackendConformanceKit() {
    override val sceneMaterials = true
    override val colorStorageError = 1f / 1024 // R16G16B16A16_SFLOAT
    private var factory: VulkanRayBackendFactory? = null

    override fun provider(devicePresent: Boolean, health: RayDeviceHealth): RayBackendProvider {
        assumeTrue(java.lang.Boolean.getBoolean("abyssus.vulkanTests"))
        return VulkanRayBackendFactory({ devicePresent }, health).also { factory = it }
    }

    @After
    fun validationReportsNoErrors() {
        val used = factory?.takeIf { it.probed } ?: return
        println("Vulkan device: ${used.deviceName}")
        if (java.lang.Boolean.getBoolean("abyssus.raytracing.validation")) {
            assertTrue("Validation layer was requested but is not active", used.validationActive)
        }
        assertEquals(emptyList<String>(), used.validationErrors)
    }

    @org.junit.Test fun probeAndSessionsAreLogged() {
        assumeTrue(java.lang.Boolean.getBoolean("abyssus.vulkanTests"))
        val lines = java.util.concurrent.CopyOnWriteArrayList<String>()
        val log = RecordingLogger()
        val provider = VulkanRayBackendFactory(log = log)
        (provider.probe() as RayCapability.Available).backend.use { backend ->
            backend.openSession("logged", RayLimits()).use { }
        }
        lines += log.entries.map { "${it.level.name.lowercase()} ${it.message}" }
        assertTrue(lines.toString(), lines.any { it.startsWith("info Probing Vulkan") })
        assertTrue(lines.toString(), lines.any { it.startsWith("info Vulkan device '") && "qualifies" in it })
        assertTrue(lines.toString(), lines.any { it.startsWith("info Vulkan backend ready on '") })
        assertTrue(lines.toString(), lines.any { it.startsWith("debug Vulkan session 'logged' opened") })
        assertTrue(lines.toString(), lines.any { it.startsWith("info Disposing the Vulkan backend") })
    }
}
