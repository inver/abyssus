/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import org.junit.Assume.assumeTrue

class MetalRayBackendTest : RayBackendConformanceKit() {
    override val sceneMaterials = true
    override fun provider(devicePresent: Boolean, health: RayDeviceHealth): RayBackendProvider {
        assumeTrue(java.lang.Boolean.getBoolean("abyssus.metalTests"))
        return MetalRayBackendFactory({ devicePresent },health)
    }
}
