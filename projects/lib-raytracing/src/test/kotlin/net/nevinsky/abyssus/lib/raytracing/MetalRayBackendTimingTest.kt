/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.sin

/** Native-only timing evidence; this does not replace the runIde presentation/EDT gate. */
class MetalRayBackendTimingTest {
    @Test fun movingInstanceAndCameraReadbackForThirtySeconds() {
        assumeTrue(java.lang.Boolean.getBoolean("abyssus.metalTests") && java.lang.Boolean.getBoolean("abyssus.metalTimingTests"))
        val factory = MetalRayBackendFactory()
        val capability = factory.probe() as RayCapability.Available
        capability.backend.use { backend ->
        backend.openSession("timing",RayLimits()).use { session ->
            val meshes = listOf(RaySliceMesh(
                floatArrayOf(-10f,0f,-10f, 10f,0f,-10f, 10f,0f,10f, -10f,0f,10f), intArrayOf(0,2,1, 0,3,2)
            ), RaySliceMesh(floatArrayOf(-1f,0f,0f, 1f,0f,0f, 0f,2f,0f),intArrayOf(0,1,2)))
            val timings = mutableListOf<Double>()
            val start = System.nanoTime()
            var version = 0L
            do {
                val frameStart = System.nanoTime()
                val phase = (frameStart-start)/1_000_000_000.0
                val camera = RaySliceCamera(listOf(sin(phase).toFloat(),2f,4f),listOf(0f,-2f,-4f),listOf(0f,1f,0f),60f,0.1f,100f)
                val identity = listOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f)
                val translated = identity.toMutableList().also { it[12] = sin(phase).toFloat(); it[14] = -2f }
                session.submit(RayRequest(RayFrameKey(1,1,version,version),1280,720,camera,meshes,listOf(
                    RaySliceInstance(0,identity,listOf(1f,1f,1f),true), RaySliceInstance(1,translated,listOf(1f,0f,0f))
                )))
                val deadline = System.nanoTime()+5_000_000_000L
                var complete = false
                while (!complete && System.nanoTime() < deadline) {
                    complete = session.poll() != null
                    if (!complete) Thread.sleep(1)
                }
                assertTrue("GPU frame completion timed out",complete)
                timings += (System.nanoTime()-frameStart)/1_000_000.0
                version++
            } while (System.nanoTime()-start < 30_000_000_000L)
            val seconds = (System.nanoTime()-start)/1_000_000_000.0
            val sorted = timings.sorted()
            val p95 = sorted[((sorted.size-1)*0.95).toInt()]
            println("${backend.info} native-only 1280x720: frames=$version seconds=$seconds fps=${version/seconds} p95ReadbackMs=$p95")
        }
        }
    }
}
