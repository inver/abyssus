/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.raytracing.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class RayBackendSelectorTest {
    @Test fun offDoesNotConstructAnyProvider() {
        val selector = RayBackendSelector("off", "Mac OS X", mapOf("metal" to { error("Native provider loaded") }))
        assertEquals(RayBackendSelection.Off, selector.select())
    }

    @Test fun forcedBackendProbesAloneAndRetainsGpuDiagnostics() {
        val backend = backend("Vulkan", "Test GPU")
        val selector = RayBackendSelector("vulkan", "Mac OS X", mapOf(
            "metal" to { error("Forced Vulkan must not construct Metal") },
            "vulkan" to { provider(RayCapability.Available(backend)) },
        ))
        val selection = selector.select() as RayBackendSelection.Selected
        assertSame(backend, selection.backend)
        assertEquals(RayBackendInfo("Vulkan", "Test GPU"), selection.backend.info)
    }

    @Test fun unavailableForcedBackendReportsItsReasonWithoutFallback() {
        val selector = RayBackendSelector("metal", "Linux", mapOf(
            "metal" to { provider(RayCapability.Unavailable(RayUnavailableReason.RAY_QUERIES, "Missing GPU feature")) },
            "vulkan" to { error("Forced Metal must not fall back") },
        ))
        val selection = selector.select() as RayBackendSelection.Unavailable
        assertEquals(listOf(RayBackendAttempt("metal", RayUnavailableReason.RAY_QUERIES, "Missing GPU feature")), selection.attempts)
    }

    @Test fun macAutoTriesMetalBeforeVulkanAndCachesBothProbes() {
        val calls = mutableListOf<String>()
        val selected = backend("Vulkan")
        val selector = RayBackendSelector(null, "Mac OS X", mapOf(
            "metal" to { recording(calls, "metal", RayCapability.Unavailable(RayUnavailableReason.ACCELERATION_STRUCTURES)) },
            "vulkan" to { recording(calls, "vulkan", RayCapability.Available(selected)) },
        ))
        repeat(3) { assertSame(selected, (selector.select() as RayBackendSelection.Selected).backend) }
        assertEquals(listOf("metal", "vulkan"), calls)
    }

    @Test fun macAutoStopsAtUsableMetal() {
        val selected = backend("Metal")
        val selector = RayBackendSelector("auto", "Mac OS X", mapOf(
            "metal" to { provider(RayCapability.Available(selected)) },
            "vulkan" to { error("Usable Metal must stop auto discovery") },
        ))
        assertSame(selected, (selector.select() as RayBackendSelection.Selected).backend)
    }

    @Test fun windowsAndLinuxAutoOnlyTryVulkan() {
        for (os in listOf("Windows 11", "Linux")) {
            val selected = backend("Vulkan")
            val selector = RayBackendSelector("auto", os, mapOf(
                "metal" to { error("$os must not construct Metal") },
                "vulkan" to { provider(RayCapability.Available(selected)) },
            ))
            assertSame(selected, (selector.select() as RayBackendSelection.Selected).backend)
        }
    }

    @Test fun startupPreferenceIsCapturedOnceAndInvalidPreferenceDoesNotProbe() {
        var preference = "off"
        var reads = 0
        val selector = RayBackendSelector.fromStartup({ reads++; preference }, "Mac OS X",
            mapOf("metal" to { error("off must not construct Metal") }))
        preference = "metal"
        repeat(2) { assertEquals(RayBackendSelection.Off, selector.select()) }
        assertEquals(1, reads)
        val invalid = RayBackendSelector("metla", "Mac OS X", mapOf("metal" to { error("Invalid value must not probe") }))
        assertEquals("metla", (invalid.select() as RayBackendSelection.Unavailable).invalidPreference)
    }

    @Test fun deviceLossBlocksAutomaticReprobeAndExplicitRetrySelectsFreshBackend() {
        val first = backend("Metal", "First GPU")
        val second = backend("Metal", "Replacement GPU")
        var probes = 0
        val selector = RayBackendSelector("metal", "Mac OS X", mapOf("metal" to {
            object : RayBackendProvider {
                override fun probe() = RayCapability.Available(if (++probes == 1) first else second)
            }
        }))
        assertSame(first, (selector.select() as RayBackendSelection.Selected).backend)
        selector.markDeviceLost(first, "GPU removed")
        repeat(5) {
            val unavailable = selector.select() as RayBackendSelection.Unavailable
            assertEquals("GPU removed", unavailable.attempts.single().detail)
        }
        assertEquals(1, probes)
        assertSame(second, (selector.select(retry = true) as RayBackendSelection.Selected).backend)
        assertEquals(2, probes)
    }

    @Test fun cancellationEscapesAndIsNeverCachedAsUnavailable() {
        var probes = 0
        val selected = backend("Metal")
        val selector = RayBackendSelector("metal", "Mac OS X", mapOf("metal" to {
            object : RayBackendProvider {
                override fun probe(): RayCapability {
                    if (++probes == 1) throw CancellationException("Cancelled")
                    return RayCapability.Available(selected)
                }
            }
        }))
        assertThrows(CancellationException::class.java) { selector.select() }
        assertSame(selected, (selector.select() as RayBackendSelection.Selected).backend)
    }

    @Test fun missingRegisteredBackendHasAnExplicitUnavailableReason() {
        val selection = RayBackendSelector("vulkan", "Linux", emptyMap()).select() as RayBackendSelection.Unavailable
        assertEquals(listOf(RayBackendAttempt("vulkan", RayUnavailableReason.RUNTIME_NOT_FOUND)), selection.attempts)
    }

    private fun provider(capability: RayCapability) = object : RayBackendProvider {
        override fun probe() = capability
    }
    private fun recording(calls: MutableList<String>, name: String, capability: RayCapability) = object : RayBackendProvider {
        override fun probe(): RayCapability { calls += name; return capability }
    }
    private fun backend(name: String, gpu: String = "GPU") = object : RayBackend {
        override val info = RayBackendInfo(name, gpu)
        override val capabilities = RayCapabilities(true, true, true, 4096, 128L * 1024 * 1024)
        override fun openSession(viewId: String, limits: RayLimits): RaySession = error("Selection must not open view sessions")
        override fun dispose() = Unit
    }
}
