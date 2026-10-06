/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.properties

import net.nevinsky.abyssus.editor.document.SceneJson

import net.nevinsky.abyssus.editor.ray.RayModePhase
import net.nevinsky.abyssus.editor.ray.RayModeSnapshot
import net.nevinsky.abyssus.editor.ray.RayBackendAttempt
import net.nevinsky.abyssus.editor.ray.RayBackendSelection
import net.nevinsky.abyssus.SceneRayControls

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import net.nevinsky.abyssus.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.projectView.AbyssusRootNode
import net.nevinsky.abyssus.projectView.DtoEntryNode
import net.nevinsky.abyssus.raytracing.RayBackendInfo
import net.nevinsky.abyssus.raytracing.RayUnavailableReason
import net.nevinsky.abyssus.sceneview.*
import java.awt.Component
import java.awt.Container
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import net.nevinsky.abyssus.testPanelServices

class SceneRaySwitchTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    /** A stand-in for a Scene view's runtime switch: records what the Properties panel asks of it. */
    private class FakeControl(override var mode: RayModeSnapshot? = RayModeSnapshot()) : RayControl {
        val requests = mutableListOf<Boolean>()
        var retries = 0
        private val listeners = mutableListOf<() -> Unit>()
        override fun setRequested(enabled: Boolean) {
            requests += enabled
            change(if (enabled) RayModeSnapshot(RayModePhase.Checking, 1) else RayModeSnapshot(revision = 2))
        }
        override fun retry() { retries++ }
        override fun addListener(parent: Disposable, listener: () -> Unit) {
            listeners += listener
            Disposer.register(parent) { listeners -= listener }
        }
        fun change(next: RayModeSnapshot) { mode = next; listeners.toList().forEach { it() } }
    }

    private val opened = mutableListOf<VirtualFile>()
    private lateinit var controls: SceneRayControls
    private lateinit var file: VirtualFile
    private val info = RayBackendInfo("Metal", "Apple Test GPU")

    override fun setUp() {
        super.setUp()
        file = myFixture.addFileToProject("Ray.scene", """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{}}}""").virtualFile
        controls = SceneRayControls(project) { _, f -> opened += f }
    }

    private fun named(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }

    private fun view(): SceneDetailsView = SceneDetailsView(controls, PanelState.UISceneState(file, "Ray scene"), testRootDisposable)
    private fun switch(v: Component) = named(v, "ray-tracing-switch") as JBCheckBox
    private fun status(v: Component) = (named(v, "ray-tracing-status") as JBLabel).text
    private fun detail(v: Component) = named(v, "ray-tracing-detail") as JBLabel
    private fun retry(v: Component) = named(v, "ray-tracing-retry") as JButton

    private fun register(control: FakeControl): Disposable = Disposer.newDisposable(testRootDisposable).also { controls.register(file, control, it) }

    fun testTheSwitchStartsOffAndEnabledWithoutAnOpenView() {
        val v = view()
        assertFalse(switch(v).isSelected)
        assertTrue(switch(v).isEnabled)
        assertEquals("", status(v))
        assertFalse(retry(v).isVisible)
        assertEquals("Ray scene", (named(v, "scene-name") as JBLabel).text)
        assertTrue((named(v, "ray-tracing-hint") as JBLabel).text.contains("The switch is not saved to the scene"))
    }

    fun testSwitchingOnWithoutAViewOpensTheSceneViewAndAppliesTheRequestWhenItAppears() {
        val v = view()
        switch(v).doClick()
        assertEquals(listOf(file), opened)
        assertTrue(controls.isPending(file))
        assertTrue(switch(v).isSelected)
        assertEquals("Opening the Scene View…", status(v))
        val control = FakeControl()
        register(control)
        assertEquals("the pending request reaches the view that opened", listOf(true), control.requests)
        assertFalse(controls.isPending(file))
        assertEquals("Checking ray tracing support…", status(v))
    }

    fun testSwitchingOffWithoutAViewCancelsThePendingRequest() {
        val v = view()
        switch(v).doClick(); switch(v).doClick()
        assertFalse(controls.isPending(file))
        val control = FakeControl()
        register(control)
        assertEquals(emptyList<Boolean>(), control.requests)
    }

    fun testTheSwitchFlipsAnOpenViewAndFollowsItsMode() {
        val control = FakeControl()
        register(control)
        val v = view()
        switch(v).doClick()
        assertEquals(listOf(true), control.requests)
        control.change(RayModeSnapshot(RayModePhase.Preparing, 1, info))
        assertEquals("Preparing ray tracing on Metal…", status(v))
        control.change(RayModeSnapshot(RayModePhase.Active, 1, info))
        assertEquals("Ray tracing: Metal", status(v))
        assertTrue(switch(v).isSelected)
        assertTrue(switch(v).toolTipText, switch(v).toolTipText.contains("Apple Test GPU"))
        switch(v).doClick()
        assertEquals(listOf(true, false), control.requests)
        assertFalse(switch(v).isSelected)
        assertEquals("", status(v))
    }

    fun testAChangeMadeInTheViewsToolbarIsReflectedInTheSwitch() {
        val control = FakeControl()
        register(control)
        val v = view()
        control.change(RayModeSnapshot(RayModePhase.Active, 1, info)) // the toolbar toggle was switched on
        assertTrue(switch(v).isSelected)
        assertEquals("Ray tracing: Metal", status(v))
        control.change(RayModeSnapshot(revision = 2)) // and off again
        assertFalse(switch(v).isSelected)
    }

    fun testUnavailableHardwareDisablesTheSwitchAndExplainsWhy() {
        val control = FakeControl()
        register(control)
        val v = view()
        val unavailable = RayBackendSelection.Unavailable(listOf(RayBackendAttempt("metal", RayUnavailableReason.ACCELERATION_STRUCTURES, "Apple M1: test")))
        control.change(RayModeSnapshot(RayModePhase.Unavailable, 1, unavailable = unavailable))
        assertFalse(switch(v).isEnabled)
        assertFalse(switch(v).isSelected)
        assertTrue(detail(v).isVisible)
        assertTrue(detail(v).text, detail(v).text.contains("no hardware ray tracing"))
        assertTrue(switch(v).toolTipText.contains("Metal"))
        // the saved limits stay editable and still save while the hardware cannot ray trace
        val samples = named(v, "ray-setting-targetSamplesPerPixel") as com.intellij.ui.components.JBTextField
        assertTrue(samples.isEnabled)
        samples.text = "64"; samples.postActionEvent()
        val saved = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)!!.text
        assertEquals(64, net.nevinsky.abyssus.editor.document.SceneJson.parse(saved)["rayTracing"]["targetSamplesPerPixel"].intValue())
    }

    fun testFailureShowsItsReasonAndARetryThatReachesTheView() {
        val control = FakeControl()
        register(control)
        val v = view()
        control.change(RayModeSnapshot(RayModePhase.Failed, 1, info, failure = "Device lost"))
        assertTrue(status(v), status(v).contains("Device lost")) // the reason is in the status line, not repeated below it
        assertFalse(detail(v).isVisible)
        assertTrue(retry(v).isVisible)
        assertFalse(switch(v).isSelected)
        retry(v).doClick()
        assertEquals(1, control.retries)
    }

    fun testClosingTheViewReturnsTheSwitchToItsIdleState() {
        val control = FakeControl()
        val parent = register(control)
        val v = view()
        control.change(RayModeSnapshot(RayModePhase.Active, 1, info))
        assertTrue(switch(v).isSelected)
        Disposer.dispose(parent)
        assertFalse(switch(v).isSelected)
        assertEquals("", status(v))
        assertNull(controls.mode(file))
    }

    fun testEveryOpenViewOfTheSceneIsFlippedTogether() {
        val first = FakeControl(); val second = FakeControl()
        register(first); register(second)
        val v = view()
        switch(v).doClick()
        assertEquals(listOf(true), first.requests)
        assertEquals(listOf(true), second.requests)
    }

    fun testAViewDisposedWithThePanelStopsListening() {
        val control = FakeControl()
        register(control)
        val parent = Disposer.newDisposable(testRootDisposable)
        val v = SceneDetailsView(controls, PanelState.UISceneState(file, "Ray scene"), parent)
        Disposer.dispose(parent)
        control.change(RayModeSnapshot(RayModePhase.Active, 1, info))
        assertFalse("a view the panel dropped is no longer updated", switch(v).isSelected)
    }

    // ---- end to end: a real Scene view, its editor, the service and the Properties panel -----------------------------------

    private fun copyProject() {
        val dir = "Untitled"
        myFixture.copyFileToProject("$dir/Untitled.abss", "$dir/Untitled.abss")
        myFixture.copyFileToProject("$dir/scenes/Main Scene.scene", "$dir/scenes/Main Scene.scene")
    }
    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }
    private fun label(node: AbstractTreeNode<*>): String = (node as? DtoEntryNode)?.value?.name ?: (node as AbyssusAssetNode).virtualFile.name
    private fun abss() = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { label(it).endsWith(".abss") }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < deadline) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); Thread.sleep(5) }
        assertTrue("Timed out waiting for $what", condition())
    }

    fun testTheSwitchInThePropertiesPanelDrivesARealSceneViewAndNeverWritesTheScene() {
        copyProject()
        val sceneFile = myFixture.findFileInTempDir("Untitled/scenes/Main Scene.scene")!!
        val before = sceneFile.contentsToByteArray().toList()
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val device = RayFakeDevice()
        val service = RayFakeDevice.service("metal" to device)
        val integration = RayIntegration(service, { RaySceneAssets({ _, _ -> error("none") }, { _, _ -> error("none") }) }, java.util.concurrent.Executor(Runnable::run))
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer())
        panel.installRay(integration)
        // the editor registers its view's switch with the project service, as it does for every Scene View
        val real = project.getService(SceneRayControls::class.java)
        val editor = net.nevinsky.abyssus.sceneview.newSceneEditor(project, sceneFile, viewFactory = { panel })
        Disposer.register(testRootDisposable, editor)
        Disposer.register(testRootDisposable) { service.close() }
        val properties = AssetPropertiesPanel(project, testRootDisposable, testPanelServices(project), { it.run() }, { it.run() })
        val scene = children(children(abss()).single { label(it) == "scenes" }).single()
        properties.show(scene)
        assertTrue(properties.state is PanelState.UISceneState)
        assertNotNull("the open Scene View registered its switch", real.mode(sceneFile))
        assertNull("the Scene View has no ray tracing button of its own", named(panel, "ray-tracing"))
        val propertySwitch = named(properties, "ray-tracing-switch") as JBCheckBox
        assertFalse(propertySwitch.isSelected); assertFalse(panel.rayMode!!.requested)
        propertySwitch.doClick()
        waitFor("the view to leave Off") { panel.rayMode!!.requested }
        waitFor("a checked backend") { panel.rayMode!!.phase == RayModePhase.Preparing || panel.rayMode!!.phase == RayModePhase.Active }
        waitFor("the status in the Properties panel") { (named(properties, "ray-tracing-status") as JBLabel).text.contains("Metal") }
        panel.rayControl!!.setRequested(false) // a change made in the view itself flips the properties switch back
        waitFor("off") { panel.rayMode!!.phase == RayModePhase.Off }
        waitFor("the switch to follow") { !propertySwitch.isSelected }
        assertEquals("flipping the switch never writes the scene", before, sceneFile.contentsToByteArray().toList())
    }
}
