/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ray

import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.ray.RayBackendSelector
import net.nevinsky.abyssus.lib.core.editor.ray.RayBackendService
import net.nevinsky.abyssus.lib.core.editor.ray.rayTestModel
import net.nevinsky.abyssus.lib.raytracing.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** The ray view's failure reporting on a fake backend: each way ray mode can stop gives the game a reason to show. */
class RayFieldRuntimeTest {
    private val now = AtomicLong(1_000_000_000L)

    private class Device : RayBackendProvider {
        val submitted = AtomicInteger()
        val hold = AtomicBoolean(false)
        val loss = AtomicReference<String?>()
        val failRender = AtomicBoolean(false)
        override fun probe(): RayCapability {
            val health = RayDeviceHealth()
            return RayCapability.Available(object : RayBackend {
                override val info = RayBackendInfo("Fake", "Fake GPU")
                override val capabilities = RayCapabilities(true, true, true, 4096, 128L * 1024 * 1024)
                override fun openSession(viewId: String, limits: RayLimits): RaySession = RayQueuedSession(object : RaySession {
                    private var pending: RaySceneRequest? = null
                    override fun submit(request: RayRequest) = error("scenes only")
                    override fun submit(request: RaySceneRequest) { pending = request; submitted.incrementAndGet() }
                    override fun poll(): RayFrame? {
                        loss.getAndSet(null)?.let(health::reportLost)
                        health.checkUsable()
                        check(!failRender.get()) { "Render failed" }
                        if (hold.get()) return null
                        val request = pending ?: return null
                        pending = null
                        return RayFrame(request.key, request.width, request.height,
                            FloatArray(request.width * request.height * 4), FloatArray(request.width * request.height) { 1f })
                    }
                    override fun dispose() { pending = null }
                }, health)
                override fun dispose() {}
            })
        }
    }

    private fun frame(x: Float): RayFieldFrame = RayFieldSceneBuilder().build(
        RayFieldInputs(
            listOf(RayPlacedModel("m0", rayTestModel(), Matrix4().translate(x, 0f, 0f))), emptyList(),
            listOf(RayLight("sun", RayLightKind.DIRECTIONAL, RayColor(1f, 1f, 1f), RayVec3(0f, -1f, 0f))),
            RayColor(.3f, .3f, .3f), RayColor(.6f, .7f, .85f), null, null,
            RayFieldCamera(listOf(0f, 2f, 5f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f),
        ),
    )

    private fun withRuntime(device: Device, body: (RayFieldRuntime) -> Unit) {
        val service = RayBackendService(
            RayBackendSelector("vulkan", "Linux", mapOf("vulkan" to { device })),
            ResourceEditorMessages(), publish = { it() }, clock = now::get,
        )
        val runtime = RayFieldRuntime(service.newView<Unit>("field"))
        try { body(runtime) } finally { runtime.close(); service.close() }
    }

    @Test fun aHealthyBackendDeliversFramesWithoutAFailure() = withRuntime(Device()) { runtime ->
        runtime.offer(frame(0f), 8, 8)
        await { runtime.latest() != null }
        assertNull(runtime.failure)
    }

    @Test fun aStalledFrameIsReportedAsAFailure() {
        val device = Device().apply { hold.set(true) }
        withRuntime(device) { runtime ->
            runtime.offer(frame(0f), 8, 8)
            await { device.submitted.get() == 1 }
            now.addAndGet(TimeUnit.SECONDS.toNanos(11))
            await { runtime.check() }
            assertNotNull(runtime.failure)
            assertNull(runtime.latest())
        }
    }

    @Test fun aLostDeviceIsReportedAsAFailure() {
        val device = Device()
        withRuntime(device) { runtime ->
            runtime.offer(frame(0f), 8, 8)
            await { runtime.latest() != null }
            device.loss.set("Injected GPU loss")
            runtime.offer(frame(1f), 8, 8)
            await { runtime.check() }
            assertTrue(runtime.failure!!.contains("Injected GPU loss"))
        }
    }

    @Test fun aRenderFailureIsReportedAndStaysReported() {
        val device = Device().apply { failRender.set(true) }
        withRuntime(device) { runtime ->
            runtime.offer(frame(0f), 8, 8)
            await { runtime.check() }
            val reason = runtime.failure
            assertNotNull(reason)
            device.failRender.set(false)
            runtime.offer(frame(1f), 8, 8)
            assertEquals(reason, runtime.failure)
        }
    }

    private fun await(timeoutMillis: Long = 3000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(2)
        assertTrue("Timed out waiting for the ray view", condition())
    }
}
