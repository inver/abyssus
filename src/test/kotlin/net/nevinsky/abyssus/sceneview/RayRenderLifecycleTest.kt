/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.ResourceEditorMessages
import net.nevinsky.abyssus.editor.ray.RayModePhase

import net.nevinsky.abyssus.raytracing.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

class RayRenderLifecycleTest {
    private fun input(revision: Long, generation: Long = 1): RayRenderInput {
        val camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f)
        val scene = RaySceneSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        return RayRenderInput(RaySceneRequest(RayFrameKey(generation, 1, revision, revision), 4, 4, camera, scene), RayDisplayKey(4, 4, "orbit"), revision)
    }
    private fun onEdt(action: () -> Unit) = SwingUtilities.invokeAndWait(action)

    @Test fun aCompletionThatArrivesAfterTheViewClosedIsNeverPublished() {
        val device = RayFakeDevice()
        val service = RayFakeDevice.service("metal" to device)
        val view = service.newView<String>("closing")
        try {
            val gate = CountDownLatch(1)
            device.completionGate = gate
            onEdt { view.setRequested(true); view.offer(input(1), "late frame") }
            RayFakeDevice.await(what = "submission") { device.submitted.get() == 1 }
            onEdt { view.close() }
            gate.countDown() // the native work finishes after close
            RayFakeDevice.await(what = "disposal") { device.disposedSessions.get() == 1 }
            assertNull("a closed view has nothing to present", view.latest())
        } finally { view.close(); service.close() }
    }

    @Test fun aHiddenViewStopsSubmittingAndResumesWithoutStaleImages() {
        val device = RayFakeDevice()
        val service = RayFakeDevice.service("metal" to device)
        val view = service.newView<String>("hidden")
        try {
            onEdt { view.setRequested(true); view.offer(input(1), "visible") }
            RayFakeDevice.await(what = "first frame") { view.latest() != null }
            onEdt { view.setVisible(false) }
            val submitted = device.submitted.get()
            onEdt { repeat(50) { view.offer(input(2 + it.toLong()), "while hidden") } }
            Thread.sleep(50)
            assertEquals("a hidden view submits no native work", submitted, device.submitted.get())
            assertNull(view.latest())
            assertTrue("the toolbar request is kept while hidden", view.mode.requested)
            onEdt { view.setVisible(true); view.offer(input(100), "shown again") }
            RayFakeDevice.await(what = "resumed frame") { view.latest()?.metadata == "shown again" }
        } finally { view.close(); service.close() }
    }

    @Test fun aReplacedContextRejectsFramesRenderedForTheOldOne() {
        val device = RayFakeDevice()
        val service = RayFakeDevice.service("metal" to device)
        val view = service.newView<String>("context")
        try {
            val gate = CountDownLatch(1)
            device.completionGate = gate
            onEdt { view.setRequested(true); view.offer(input(1, generation = 1), "old context") }
            RayFakeDevice.await(what = "submission") { device.submitted.get() == 1 }
            // the scene generation changes (context replaced) before the old frame completes
            onEdt { view.offer(input(2, generation = 2), "new context") }
            gate.countDown()
            RayFakeDevice.await(what = "new frame") { view.latest()?.metadata == "new context" }
            assertNotEquals("old context", view.latest()!!.metadata)
        } finally { view.close(); service.close() }
    }

    @Test fun aRenderFailureClearsPublicationDisposesTheSessionAndRetryRecovers() {
        val device = RayFakeDevice()
        val service = RayFakeDevice.service("metal" to device)
        val view = service.newView<String>("render-failure")
        try {
            onEdt { view.setRequested(true); view.offer(input(1), "frame") }
            RayFakeDevice.await(what = "first frame") { view.latest() != null }
            device.failRender = true
            onEdt { view.offer(input(2), "doomed") }
            RayFakeDevice.await(what = "failure") { view.mode.phase == RayModePhase.Failed }
            assertNull(view.latest())
            RayFakeDevice.await(what = "disposal") { device.disposedSessions.get() == 1 }
            assertFalse("a failure never keeps retrying on its own", device.probes.get() > 1)
            device.failRender = false
            onEdt { view.retry(); view.offer(input(3), "recovered") }
            RayFakeDevice.await(what = "recovered frame") { view.latest()?.metadata == "recovered" }
        } finally { view.close(); service.close() }
    }

    @Test fun aBackendWithoutSceneOpticsFallsBackInsteadOfIgnoringSavedDepths() {
        val device = RayFakeDevice()
        val service = RayFakeDevice.service("metal" to device)
        val view = service.newView<String>("no-optics")
        try {
            val base = input(1)
            val deep = RayRenderInput((base.request as RaySceneRequest).let {
                RaySceneRequest(it.key, it.width, it.height, it.camera, it.scene, maxReflectionBounces = 2)
            }, base.display, base.contentRevision)
            onEdt { view.setRequested(true); view.offer(deep, "deep") }
            RayFakeDevice.await(what = "failure") { view.mode.phase == RayModePhase.Failed }
            assertEquals("nothing reaches a backend that cannot honour the depths", 0, device.submitted.get())
            assertTrue(view.mode.failure.orEmpty(), view.mode.failure.orEmpty().contains("scene optics"))
            assertNull(view.latest())
        } finally { view.close(); service.close() }
        device.sceneOptics = true
        val capable = RayFakeDevice.service("metal" to device)
        val second = capable.newView<String>("optics")
        try {
            val base = input(1)
            val deep = RayRenderInput((base.request as RaySceneRequest).let {
                RaySceneRequest(it.key, it.width, it.height, it.camera, it.scene, maxReflectionBounces = 2)
            }, base.display, base.contentRevision)
            onEdt { second.setRequested(true); second.offer(deep, "deep") }
            RayFakeDevice.await(what = "frame") { second.latest()?.metadata == "deep" }
        } finally { second.close(); capable.close() }
    }

    @Test fun aPreparationFailureLeavesTheViewFailedWithNoLeakedSession() {
        val device = RayFakeDevice().apply { failOpen = true }
        val service = RayFakeDevice.service("metal" to device)
        val view = service.newView<String>("preparation")
        try {
            onEdt { view.setRequested(true) }
            RayFakeDevice.await(what = "failure") { view.mode.phase == RayModePhase.Failed }
            assertEquals(0, device.disposedSessions.get())
            assertNull(view.latest())
        } finally { view.close(); service.close() }
    }

    @Test fun severalViewsKeepIndependentSessionsAndClosingOneLeavesTheOther() {
        val device = RayFakeDevice()
        val service = RayFakeDevice.service("metal" to device)
        val first = service.newView<String>("first")
        val second = service.newView<String>("second")
        try {
            onEdt { first.setRequested(true); second.setRequested(true); first.offer(input(1), "first"); second.offer(input(1), "second") }
            RayFakeDevice.await(what = "both frames") { first.latest() != null && second.latest() != null }
            assertEquals(2, device.opened.get())
            assertEquals(1, device.probes.get())
            onEdt { first.close() }
            RayFakeDevice.await(what = "first disposed") { device.disposedSessions.get() == 1 }
            onEdt { second.offer(input(2), "second again") }
            RayFakeDevice.await(what = "second still renders") { second.latest()?.metadata == "second again" }
        } finally { first.close(); second.close(); service.close() }
    }

    @Test fun ordinaryRasterStartupLoadsNoNativeLibraryAndProbesNothing() {
        val created = AtomicInteger()
        val selector = rayBackendSelectorFromStartup(property = { null }, osName = "Mac OS X",
            providers = mapOf("metal" to { created.incrementAndGet(); RayFakeDevice() }, "vulkan" to { created.incrementAndGet(); RayFakeDevice() }))
        val service = RayBackendService(selector, ResourceEditorMessages(), publish = { SwingUtilities.invokeLater(it) })
        try {
            val view = service.newView<String>("raster")
            repeat(20) { view.latest(); view.offer(input(it.toLong()), "ignored while off") }
            assertEquals("no provider is built until a toggle asks for one", 0, created.get())
            assertEquals(RayModePhase.Off, view.mode.phase)
            view.close()
            assertEquals(0, created.get())
        } finally { service.close() }
    }

    @Test fun offLoadsNoRayTracingNativesEvenWhenToggled() {
        val created = AtomicInteger()
        val service = RayBackendService(RayBackendSelector("off", "Mac OS X", mapOf("metal" to { created.incrementAndGet(); RayFakeDevice() })), ResourceEditorMessages(), publish = { SwingUtilities.invokeLater(it) })
        val view = service.newView<String>("off")
        try {
            onEdt { view.setRequested(true) }
            RayFakeDevice.await(what = "disabled") { view.mode.phase == RayModePhase.Unavailable }
            assertEquals(0, created.get())
            assertEquals(RayUnavailableReason.DISABLED, view.mode.unavailable!!.attempts.single().reason)
        } finally { view.close(); service.close() }
    }

    @Test fun nativeWorkNeverRunsOnTheEdtAndCleanupDoesNotBlockIt() {
        val device = RayFakeDevice()
        val service = RayFakeDevice.service("metal" to device)
        val view = service.newView<String>("edt")
        try {
            onEdt { view.setRequested(true); view.offer(input(1), "frame") }
            RayFakeDevice.await(what = "frame") { view.latest() != null }
            val responsive = CountDownLatch(1)
            onEdt { view.close() }
            SwingUtilities.invokeLater { responsive.countDown() }
            assertTrue(responsive.await(500, TimeUnit.MILLISECONDS))
            assertTrue(device.threads.none { it.startsWith("AWT-EventQueue") })
        } finally { view.close(); service.close() }
    }
}
