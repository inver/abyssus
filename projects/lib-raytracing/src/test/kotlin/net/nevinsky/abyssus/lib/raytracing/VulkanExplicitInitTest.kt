/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.lwjgl.system.Configuration

/**
 * LWJGL's Vulkan loader choice is process-global and sticky, so this runs in its own forked JVM
 * (one class per JVM) with `org.lwjgl.vulkan.explicitInit=true`. With explicit initialization the
 * provider's getter throws `IllegalStateException` until `VK.create()` is called, so `loadVulkanLibrary`
 * must create the function table itself rather than treating the getter's exception as fatal.
 * A missing loader is reported as `RUNTIME_NOT_FOUND`, never as an unclassified error.
 */
class VulkanExplicitInitTest {
    @Test fun explicitInitLoadsTheRuntimeOrReportsUnavailable() {
        assumeTrue(java.lang.Boolean.getBoolean("abyssus.vulkanMissingLoaderTest"))
        Configuration.VULKAN_EXPLICIT_INIT.set(true)
        val result = VulkanRayBackendFactory().probe()
        assertTrue("Expected unavailable with an explicit-init host and no loader, got $result", result is RayCapability.Unavailable)
        assertEquals(RayUnavailableReason.RUNTIME_NOT_FOUND, (result as RayCapability.Unavailable).reason)
        // A second probe in the same session still answers cleanly.
        assertTrue(VulkanRayBackendFactory().probe() is RayCapability.Unavailable)
    }
}
