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
    private var factory: VulkanRayBackendFactory? = null

    override fun provider(devicePresent: Boolean, health: RayDeviceHealth): RayBackendProvider {
        assumeTrue(java.lang.Boolean.getBoolean("abyssus.vulkanTests"))
        return VulkanRayBackendFactory({ devicePresent }, health).also { factory = it }
    }

    @After
    fun validationReportsNoErrors() {
        val used = factory?.takeIf { it.probed } ?: return
        if (java.lang.Boolean.getBoolean("abyssus.raytracing.validation")) {
            assertTrue("Validation layer was requested but is not active", used.validationActive)
        }
        assertEquals(emptyList<String>(), used.validationErrors)
    }
}
