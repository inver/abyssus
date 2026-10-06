/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.scene.SceneContent
import net.nevinsky.abyssus.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.editor.ray.RayModePhase
import net.nevinsky.abyssus.ui.RayModeText

import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.content.Quat
import net.nevinsky.abyssus.editor.content.PlacementTransform

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.raytracing.RayUnavailableReason
import java.awt.Component
import java.awt.Container
import java.util.concurrent.TimeUnit
import javax.swing.AbstractButton
import javax.swing.JLabel

/**
 * The Scene View itself has no Ray Tracing control; the Abyssus Properties panel flips its [RayControl]. These tests drive
 * that control directly, which is exactly what the Properties switch calls.
 */
class SceneViewPanelRayTest : BasePlatformTestCase() {
    private val panels = mutableListOf<SceneViewPanel>()
    private val services = mutableListOf<RayBackendService>()

    override fun tearDown() {
        try {
            panels.forEach(SceneViewPanel::dispose)
            services.forEach(RayBackendService::close)
        } finally { super.tearDown() }
    }

    private fun all(c: Component): List<Component> = listOf(c) + ((c as? Container)?.components?.flatMap(::all) ?: emptyList())

    private fun service(vararg devices: Pair<String, RayFakeDevice>, backend: String = "auto") =
        RayFakeDevice.service(*devices, backend = backend).also { services += it }

    private fun panel(renderer: SceneRenderer, service: RayBackendService): SceneViewPanel {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer)
        panel.installRay(RayIntegration(service, { RaySceneAssets({ _, _ -> error("none") }, { _, _ -> error("none") }) }, java.util.concurrent.Executor(Runnable::run)))
        panels += panel
        return panel
    }

    private fun waitFor(what: String, timeoutMillis: Long = 3000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!condition() && System.nanoTime() < deadline) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); Thread.sleep(5) }
        assertTrue("Timed out waiting for $what", condition())
    }

    private fun context(renderer: SceneRenderer): RayFrameContext {
        val camera = PerspectiveCamera(60f, 8f, 8f).apply { position.set(0f, 0f, 5f); lookAt(0f, 0f, 0f); update() }
        return RayFrameContext(renderer.params, SceneContent(), camera, LightSet.NONE, emptyList(), 8, 8, renderer.state.viewCamera)
    }

    fun testTheSceneViewToolbarHasNoRayTracingControl() {
        val panel = panel(testRenderer(), service("metal" to RayFakeDevice()))
        val rayControls = all(panel).filter { c ->
            c.name?.startsWith("ray-") == true || ((c as? AbstractButton)?.text ?: (c as? JLabel)?.text)?.contains("Ray Tracing", ignoreCase = true) == true
        }
        assertEquals("no ray tracing button, status or retry in the Scene View: $rayControls", emptyList<Component>(), rayControls)
        assertNotNull("the view still exposes its runtime switch to the Properties panel", panel.rayControl)
    }

    fun testRayTracingIsOffByDefaultAndSwitchingItOnWritesNothing() {
        val file = myFixture.addFileToProject("Ray.scene", """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{}}}""").virtualFile
        val before = file.contentsToByteArray()
        val device = RayFakeDevice()
        val panel = panel(testRenderer(), service("metal" to device))
        assertEquals(RayModePhase.Off, panel.rayMode!!.phase)
        assertEquals(0, device.probes.get())
        panel.rayControl!!.setRequested(true)
        waitFor("a checked backend") { panel.rayMode!!.phase != RayModePhase.Off && panel.rayMode!!.phase != RayModePhase.Checking }
        assertTrue(panel.rayMode!!.requested)
        panel.rayControl!!.setRequested(false)
        waitFor("off again") { panel.rayMode!!.phase == RayModePhase.Off }
        assertEquals("switching ray tracing never writes the scene", before.toList(), file.contentsToByteArray().toList())
        assertEquals(String(before), FileDocumentManager.getInstance().getDocument(file)!!.text)
    }

    fun testCameraSelectionAndDragStateSurviveEveryTransition() {
        val renderer = testRenderer()
        renderer.state.selectedId = "selected-entity"
        renderer.state.viewCamera = "camera-1"
        renderer.state.preview = mapOf("selected-entity" to net.nevinsky.abyssus.editor.pick.DragResult(
            PlacementTransform(Vec3(1f, 2f, 3f), Quat.IDENTITY, Vec3(1f, 1f, 1f)), null))
        val preview = renderer.state.preview
        val device = RayFakeDevice()
        val panel = panel(renderer, service("metal" to device))
        panel.rayControl!!.setRequested(true)
        waitFor("a checked backend") { panel.rayMode!!.phase == RayModePhase.Preparing || panel.rayMode!!.phase == RayModePhase.Active }
        device.loss.set("Injected GPU loss")
        val feedFrame = renderer.rayFrameProvider!!
        waitFor("device loss to fail the mode") { feedFrame(context(renderer)); panel.rayMode!!.phase == RayModePhase.Failed }
        panel.rayControl!!.setRequested(false)
        assertEquals("selected-entity", renderer.state.selectedId)
        assertEquals("camera-1", renderer.state.viewCamera)
        assertSame("a drag in progress keeps its preview", preview, renderer.state.preview)
    }

    fun testTwoViewsHaveIndependentModes() {
        val device = RayFakeDevice()
        val shared = service("metal" to device)
        val first = panel(testRenderer(), shared)
        val second = panel(testRenderer(), shared)
        first.rayControl!!.setRequested(true)
        waitFor("the first view to check its backend") { first.rayMode!!.phase != RayModePhase.Off && first.rayMode!!.phase != RayModePhase.Checking }
        assertEquals(RayModePhase.Off, second.rayMode!!.phase)
        second.rayControl!!.setRequested(true)
        waitFor("the second view to check its backend") { second.rayMode!!.phase != RayModePhase.Off && second.rayMode!!.phase != RayModePhase.Checking }
        first.rayControl!!.setRequested(false)
        waitFor("the first view off") { first.rayMode!!.phase == RayModePhase.Off }
        assertTrue("turning one view off leaves the other on", second.rayMode!!.requested)
        assertEquals("one device probe serves both views", 1, device.probes.get())
    }

    fun testUnsupportedHardwareIsReportedWithTheReason() {
        val device = RayFakeDevice().apply { unavailable = RayUnavailableReason.ACCELERATION_STRUCTURES }
        val panel = panel(testRenderer(), service("metal" to device, "vulkan" to RayFakeDevice("Vulkan").apply { unavailable = RayUnavailableReason.RUNTIME_NOT_FOUND }))
        panel.rayControl!!.setRequested(true)
        waitFor("the mode to become unavailable") { panel.rayMode!!.phase == RayModePhase.Unavailable }
        assertFalse("the switch is disabled for unsupported hardware", panel.rayMode!!.toggleEnabled)
        val tooltip = RayModeText.tooltip(panel.rayMode!!)
        assertTrue(tooltip, tooltip.contains("Metal") && tooltip.contains("no hardware ray tracing"))
        assertTrue(tooltip, tooltip.contains("Vulkan runtime not found"))
    }

    fun testFailureCanBeRetriedWhichChecksTheGpuAgain() {
        val device = RayFakeDevice().apply { failOpen = true }
        val panel = panel(testRenderer(), service("metal" to device, backend = "metal"))
        panel.rayControl!!.setRequested(true)
        waitFor("the failure") { panel.rayMode!!.phase == RayModePhase.Failed }
        assertTrue(RayModeText.status(panel.rayMode!!)!!, RayModeText.status(panel.rayMode!!)!!.contains("Preparation failed"))
        assertFalse(panel.rayMode!!.requested)
        device.failOpen = false
        panel.rayControl!!.retry()
        waitFor("a recovered mode") { panel.rayMode!!.phase == RayModePhase.Preparing || panel.rayMode!!.phase == RayModePhase.Active }
        assertEquals(2, device.opened.get())
    }

    fun testTheBackendAndGpuAreNamedForAnActiveView() {
        val panel = panel(testRenderer(), service("metal" to RayFakeDevice("Metal", "Apple Test GPU")))
        panel.rayControl!!.setRequested(true)
        waitFor("preparation") { panel.rayMode!!.backendInfo != null }
        val tooltip = RayModeText.tooltip(panel.rayMode!!)
        assertTrue(tooltip, tooltip.contains("Apple Test GPU") && tooltip.contains("Metal"))
    }
}
