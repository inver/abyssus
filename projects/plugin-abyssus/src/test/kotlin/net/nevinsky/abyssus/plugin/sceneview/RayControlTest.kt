/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.ray.RayBackendService
import net.nevinsky.abyssus.lib.core.editor.ray.RaySceneAssets
import net.nevinsky.abyssus.lib.core.editor.scene.FoliagePlacement
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.SceneRayControls
import net.nevinsky.abyssus.plugin.properties.PanelState
import net.nevinsky.abyssus.plugin.properties.SceneDetailsView
import java.awt.Component
import java.awt.Container
import java.util.concurrent.Executor
import javax.swing.JLabel

/**
 * The note that says a ray-traced image has no foliage: a Scene view's [RayControl] reports it while the scene it
 * shows has foliage, and the Abyssus Properties panel's Ray Tracing section shows it. The ray-traced image itself is
 * covered by `RaySceneSnapshotTest`, which captures a scene with and without foliage.
 */
class RayControlTest : BasePlatformTestCase() {
    private val panels = mutableListOf<SceneViewPanel>()
    private val services = mutableListOf<RayBackendService>()

    override fun tearDown() {
        try {
            panels.forEach(SceneViewPanel::dispose)
            services.forEach(RayBackendService::close)
        } finally { super.tearDown() }
    }

    private fun named(c: Component, name: String): Component? =
        if (c.name == name) c else (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }

    /** A Scene view with ray tracing installed that shows [content]. */
    private fun panel(content: SceneContent): SceneViewPanel {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val service = RayFakeDevice.service("metal" to RayFakeDevice()).also { services += it }
        val view = SceneViewPanel(SceneRenderParams.DEFAULT.copy(content = content), testRenderer())
        view.installRay(RayIntegration(service,
            { RaySceneAssets({ _, _ -> error("no assets expected") }, { _, _ -> error("no assets expected") }) },
            Executor(Runnable::run)))
        panels += view
        return view
    }

    private fun foliaged() = SceneContent(
        foliages = listOf(FoliagePlacement("0", "foliage_meadow", "terrain", PlacementTransform.IDENTITY))
    )

    fun testTheNoteSaysFoliageIsNotRayTracedFromTheBundle() {
        assertEquals(AbyssusBundle.message("propertiesSceneRayFoliage"), panel(foliaged()).rayControl!!.foliageNote)
        assertNull("a scene that shows no foliage says nothing", panel(SceneContent()).rayControl!!.foliageNote)
    }

    fun testThePropertiesRaySectionShowsTheNoteWhileAViewShowsFoliage() {
        val file = myFixture.addFileToProject("Foliage.scene", """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{}}}""").virtualFile
        val controls = SceneRayControls(project) { _, _ -> }
        controls.register(file, panel(foliaged()).rayControl!!, Disposer.newDisposable(testRootDisposable))
        val note = named(SceneDetailsView(controls, PanelState.UISceneState(file, "Foliage scene"), testRootDisposable),
            "ray-tracing-foliage") as JLabel
        assertTrue("the note is shown while a view shows foliage", note.isVisible)
        assertEquals(AbyssusBundle.message("propertiesSceneRayFoliage"), note.text)
    }

    fun testThePropertiesRaySectionSaysNothingWhileNoViewShowsFoliage() {
        val file = myFixture.addFileToProject("Plain.scene", """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{}}}""").virtualFile
        val controls = SceneRayControls(project) { _, _ -> }
        controls.register(file, panel(SceneContent()).rayControl!!, Disposer.newDisposable(testRootDisposable))
        val note = named(SceneDetailsView(controls, PanelState.UISceneState(file, "Plain scene"), testRootDisposable),
            "ray-tracing-foliage") as JLabel
        assertFalse("no note without foliage", note.isVisible)
        assertEquals("", note.text)
    }
}
