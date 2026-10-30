/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.sin

/**
 * Native-only timing evidence for the Vulkan backend (opt in with `-Dabyssus.vulkanTests=true -Dabyssus.vulkanTimingTests=true`);
 * this does not replace the runIde presentation/EDT gate. It moves one instance and the camera at 1280x720 for 30 seconds
 * through the feasibility slice and then through the full scene shader, and prints the frame rate and p95 completion latency.
 */
class VulkanRayBackendTimingTest {
    private val identity = listOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    private fun measure(label: String, backend: RayBackend, submit: (RaySession, Long, Double) -> Unit) {
        backend.openSession("timing-$label", RayLimits()).use { session ->
            val timings = mutableListOf<Double>()
            val start = System.nanoTime()
            var version = 0L
            do {
                val frameStart = System.nanoTime()
                submit(session, version, (frameStart - start) / 1_000_000_000.0)
                val deadline = System.nanoTime() + 5_000_000_000L
                var complete = false
                while (!complete && System.nanoTime() < deadline) {
                    complete = session.poll() != null
                    if (!complete) Thread.sleep(0, 200_000)
                }
                assertTrue("GPU frame completion timed out", complete)
                timings += (System.nanoTime() - frameStart) / 1_000_000.0
                version++
            } while (System.nanoTime() - start < 30_000_000_000L)
            val seconds = (System.nanoTime() - start) / 1_000_000_000.0
            val sorted = timings.sorted()
            val p95 = sorted[((sorted.size - 1) * 0.95).toInt()]
            println("${backend.info} $label 1280x720: frames=$version seconds=$seconds fps=${version / seconds} p95Ms=$p95")
        }
    }

    @Test fun movingInstanceAndCameraReadbackForThirtySeconds() {
        assumeTrue(java.lang.Boolean.getBoolean("abyssus.vulkanTests") && java.lang.Boolean.getBoolean("abyssus.vulkanTimingTests"))
        val capability = VulkanRayBackendFactory().probe() as RayCapability.Available
        capability.backend.use { backend ->
            val sliceMeshes = listOf(RaySliceMesh(floatArrayOf(-10f, 0f, -10f, 10f, 0f, -10f, 10f, 0f, 10f, -10f, 0f, 10f), intArrayOf(0, 2, 1, 0, 3, 2)),
                RaySliceMesh(floatArrayOf(-1f, 0f, 0f, 1f, 0f, 0f, 0f, 2f, 0f), intArrayOf(0, 1, 2)))
            measure("slice", backend) { session, version, phase ->
                val camera = RaySliceCamera(listOf(sin(phase).toFloat(), 2f, 4f), listOf(0f, -2f, -4f), listOf(0f, 1f, 0f), 60f, 0.1f, 100f)
                val translated = identity.toMutableList().also { it[12] = sin(phase).toFloat(); it[14] = -2f }
                session.submit(RayRequest(RayFrameKey(1, 1, version, version), 1280, 720, camera, sliceMeshes, listOf(
                    RaySliceInstance(0, identity, listOf(1f, 1f, 1f), true), RaySliceInstance(1, translated, listOf(1f, 0f, 0f)))))
            }
            val floor = RayMesh("floor", floatArrayOf(-10f, 0f, -10f, 10f, 0f, -10f, 10f, 0f, 10f, -10f, 0f, 10f), intArrayOf(0, 2, 1, 0, 3, 2),
                normals = floatArrayOf(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f))
            val prism = RayMesh("prism", floatArrayOf(-1f, 0f, 0f, 1f, 0f, 0f, 0f, 2f, 0f), intArrayOf(0, 1, 2), normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f))
            val materials = listOf(RayMaterial(kind = RayMaterialKind.PBR, baseColor = RayColor(.8f, .8f, .8f), metallic = 1f, roughness = .2f),
                RayMaterial(baseColor = RayColor(1f, .2f, .1f)))
            val lights = listOf(RayLight("sun", RayLightKind.DIRECTIONAL, RayColor(1f, 1f, 1f), direction = RayVec3(-.4f, -1f, -.3f)))
            measure("scene", backend) { session, version, phase ->
                val camera = RaySliceCamera(listOf(sin(phase).toFloat(), 2f, 4f), listOf(0f, -2f, -4f), listOf(0f, 1f, 0f), 60f, 0.1f, 100f)
                val translated = identity.toMutableList().also { it[12] = sin(phase).toFloat(); it[14] = -2f }
                session.submit(RaySceneRequest(RayFrameKey(1, 1, version, version), 1280, 720, camera,
                    RaySceneSnapshot(listOf(floor, prism), listOf(RayInstance("floor", 0, 0, identity.toFloatArray()), RayInstance("prism", 1, 1, translated.toFloatArray())),
                        materials, emptyList(), lights)))
            }
        }
    }
}
