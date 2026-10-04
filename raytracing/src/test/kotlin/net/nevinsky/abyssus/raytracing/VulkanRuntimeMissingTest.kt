/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.lwjgl.system.Configuration

/**
 * A machine without a Vulkan loader must get a reason code, not an exception. LWJGL's library choice is process-global
 * and sticky, so only `verifyVulkanPackaging` (one JVM per class) enables this.
 */
class VulkanRuntimeMissingTest {
    @Test fun missingLoaderIsReportedAsRuntimeNotFound() {
        assumeTrue(java.lang.Boolean.getBoolean("abyssus.vulkanMissingLoaderTest"))
        Configuration.VULKAN_LIBRARY_NAME.set("abyssus-no-such-vulkan-loader")
        val result = VulkanRayBackendFactory().probe()
        assertTrue("Expected unavailable, got $result", result is RayCapability.Unavailable)
        assertEquals(RayUnavailableReason.RUNTIME_NOT_FOUND, (result as RayCapability.Unavailable).reason)
        // A second probe in the same session still answers cleanly.
        assertTrue(VulkanRayBackendFactory().probe() is RayCapability.Unavailable)
    }
}
