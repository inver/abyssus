/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import org.junit.Assert.*
import org.junit.Test

/** Feasibility/lifecycle stage. Full shading references are added with tasks 3.1–3.5. */
abstract class RayBackendConformanceKit {
    protected abstract fun provider(devicePresent: Boolean, health: RayDeviceHealth): RayBackendProvider

    @Test
    fun probeWithoutDeviceIsUnavailable() {
        assertTrue(provider(false, RayDeviceHealth()).probe() is RayCapability.Unavailable)
    }

    @Test
    fun primaryVisibilityAndDepthMatchTheReference() = withBackend { backend, _ ->
        backend.openSession("primary", RayLimits()).use { session ->
            val frame = render(session, triangleRequest())
            assertReference(frame, floatArrayOf(0.1f, 0.1f, 0.1f, 1f), floatArrayOf(0.950951f), 0.0002f)
        }
    }

    @Test
    fun instanceMotionChangesVisibility() = withBackend { backend, _ ->
        backend.openSession("motion", RayLimits()).use { session ->
            val hit = render(session, triangleRequest())
            val miss = render(session, triangleRequest(x = 10f))
            assertTrue(hit.depthValues()[0] < 1f)
            assertReference(miss, floatArrayOf(0.05f, 0.1f, 0.2f, 1f), floatArrayOf(1f), 0.0002f)
        }
    }

    @Test
    fun directionalShadowDoesNotBlockThePrimaryCameraRay() = withBackend { backend, _ ->
        backend.openSession("shadow", RayLimits()).use { session ->
            val geometry = listOf(floor(), floor(0.5f))
            val exposed = render(session, request(geometry, listOf(instance(0), instance(1, x = 10f, y = 1f))))
            val shadow = render(session, request(geometry, listOf(instance(0), instance(1, y = 1f))))
            assertReference(exposed, floatArrayOf(1f, 1f, 1f, 1f), shadow.depthValues(), 0.0002f)
            assertReference(shadow, floatArrayOf(0.1f, 0.1f, 0.1f, 1f), exposed.depthValues(), 0.0002f)
        }
    }

    @Test
    fun reflectionHitAndMissMatchTheReference() = withBackend { backend, _ ->
        backend.openSession("reflection", RayLimits()).use { session ->
            val geometry =
                listOf(floor(), RaySliceMesh(floatArrayOf(-1f, 0f, -2f, 1f, 0f, -2f, 0f, 2f, -2f), intArrayOf(0, 1, 2)))
            val mirror = instance(0, reflective = true)
            val hit = render(session, request(geometry, listOf(mirror, instance(1, color = listOf(1f, 0f, 0f)))))
            val miss =
                render(session, request(geometry, listOf(mirror, instance(1, x = 10f, color = listOf(1f, 0f, 0f)))))
            assertReference(hit, floatArrayOf(0.1f, 0f, 0f, 1f), miss.depthValues(), 0.0002f)
            assertReference(miss, floatArrayOf(0.05f, 0.1f, 0.2f, 1f), hit.depthValues(), 0.0002f)
        }
    }

    @Test
    fun aPendingRequestIsReplacedWithoutStarvingMotion() = withBackend { backend, _ ->
        backend.openSession("queue", RayLimits()).use { session ->
            session.submit(triangleRequest(revision = 1))
            session.submit(triangleRequest(revision = 2))
            session.submit(triangleRequest(revision = 3))
            assertEquals(1L, await(session).key.cameraRevision)
            assertEquals(3L, await(session).key.cameraRevision)
            assertNull(session.poll())
        }
    }

    @Test
    fun structurallyStaleFramesAreRejected() = withBackend { backend, _ ->
        backend.openSession("stale", RayLimits()).use { session ->
            session.submit(triangleRequest(generation = 1))
            session.submit(triangleRequest(generation = 2))
            assertEquals(2L, await(session).key.sceneGeneration)
        }
    }

    @Test
    fun staleResizeFramesAreRejected() = withBackend { backend, _ ->
        backend.openSession("resize", RayLimits()).use { session ->
            session.submit(triangleRequest(width = 1))
            session.submit(triangleRequest(width = 2))
            assertEquals(2, await(session).width)
        }
    }

    @Test
    fun twoSessionsAreIndependentOnOneDevice() = withBackend { backend, _ ->
        val first = backend.openSession("first", RayLimits())
        val second = backend.openSession("second", RayLimits())
        first.submit(triangleRequest())
        second.submit(triangleRequest(x = 10f))
        first.dispose()
        first.dispose()
        assertEquals(1f, await(second).depthValues()[0], 0f)
        second.dispose()
    }

    @Test
    fun disposeWithWorkInFlightRejectsLaterUse() = withBackend { backend, _ ->
        val session = backend.openSession("dispose", RayLimits())
        session.submit(triangleRequest())
        session.dispose()
        assertThrows(IllegalStateException::class.java) { session.poll() }
        assertThrows(IllegalStateException::class.java) { session.submit(triangleRequest()) }
    }

    @Test
    fun lossReportedAtPollFailsEverySessionOnTheDevice() = withBackend { backend, health ->
        backend.openSession("loss-first", RayLimits()).use { first ->
            backend.openSession("loss-second", RayLimits()).use { second ->
                first.submit(triangleRequest())
                second.submit(triangleRequest())
                health.reportLost("Injected device loss")
                assertThrows(RayDeviceLostException::class.java) { first.poll() }
                assertThrows(RayDeviceLostException::class.java) { second.poll() }
            }
        }
    }

    protected fun withBackend(check: (RayBackend, RayDeviceHealth) -> Unit) {
        val health = RayDeviceHealth()
        val result = provider(true, health).probe()
        assertTrue("Backend probe must succeed: $result", result is RayCapability.Available)
        (result as RayCapability.Available).backend.use { check(it, health) }
    }

    private fun triangleRequest(x: Float = 0f, revision: Long = 1, generation: Long = 1, width: Int = 1) = RayRequest(
        RayFrameKey(generation, 1, revision, 1),
        width,
        1,
        RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, 0.1f, 100f),
        listOf(RaySliceMesh(floatArrayOf(-2f, -2f, 0f, 2f, -2f, 0f, 0f, 2f, 0f), intArrayOf(0, 1, 2))),
        listOf(instance(0, x = x))
    )

    private fun request(meshes: List<RaySliceMesh>, instances: List<RaySliceInstance>) = RayRequest(
        RayFrameKey(1, 1, 1, 1),
        1,
        1,
        RaySliceCamera(listOf(0f, 2f, 4f), listOf(0f, -2f, -4f), listOf(0f, 1f, 0f), 60f, 0.1f, 100f),
        meshes,
        instances
    )

    private fun floor(size: Float = 10f) = RaySliceMesh(
        floatArrayOf(-size, 0f, -size, size, 0f, -size, size, 0f, size, -size, 0f, size), intArrayOf(0, 2, 1, 0, 3, 2)
    )

    private fun instance(
        mesh: Int,
        x: Float = 0f,
        y: Float = 0f,
        color: List<Float> = listOf(1f, 1f, 1f),
        reflective: Boolean = false
    ) =
        RaySliceInstance(mesh, listOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, x, y, 0f, 1f), color, reflective)

    private fun render(session: RaySession, request: RayRequest): RayFrame {
        session.submit(request); return await(session)
    }

    private fun await(session: RaySession): RayFrame {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            session.poll()?.let { return it }
            Thread.sleep(1)
        }
        error("Backend frame completion timed out")
    }

    private fun assertReference(frame: RayFrame, color: FloatArray, depth: FloatArray, tolerance: Float) {
        val actual = frame.colorValues()
        assertArrayEquals(color, actual, tolerance)
        assertArrayEquals(depth, frame.depthValues(), tolerance)
        assertTrue(actual.indices.sumOf {
            kotlin.math.abs(actual[it] - color[it]).toDouble()
        } / actual.size <= tolerance)
    }
}
