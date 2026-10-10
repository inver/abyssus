/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.pick.*
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.plugin.foliage.FoliagePaintSession
import java.awt.Component
import java.awt.Container
import javax.swing.JToggleButton

class SceneFoliageControlsTest : BasePlatformTestCase() {
    private fun named(c: Component, name: String): Component? = if (c.name == name) c else
        (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }

    fun testPaintModeShowsBrushAndSelectionChangeDisposesIt() {
        val renderer = testRenderer()
        var closed = false
        val session = FoliagePaintSession(listOf(7, 9), object : FoliagePaint {
            override fun pressed(terrain: TerrainTarget, at: com.badlogic.gdx.math.Vector3) = true
            override fun dragged(to: com.badlogic.gdx.math.Vector3, erase: Boolean) {}
            override fun released() {}
            override fun cancelled() {}
        }) { closed = true }
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer,
            paintSession = { id, _, ready -> ready(if (id == "1") session else null) })
        try {
            panel.selectEntity("1")
            val button = named(panel, "paint-foliage") as JToggleButton
            button.doClick()
            assertEquals(7, renderer.state.paint!!.layerId)
            assertTrue(named(panel, "foliage-brush-strip")!!.isVisible)
            panel.selectEntity("2")
            assertNull(renderer.state.paint)
            assertTrue(closed)
            assertFalse(button.isSelected)
        } finally { panel.dispose() }
    }

    fun testAttachmentChangesReloadSessionWithoutChangingSelection() {
        val renderer = testRenderer()
        var opens = 0
        var closes = 0
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer, paintSession = { _, _, ready ->
            opens++
            ready(if (renderer.params.content.foliages.isEmpty()) null else FoliagePaintSession(listOf(1),
                object : FoliagePaint {
                    override fun pressed(terrain: TerrainTarget, at: com.badlogic.gdx.math.Vector3) = true
                    override fun dragged(to: com.badlogic.gdx.math.Vector3, erase: Boolean) {}
                    override fun released() {}
                    override fun cancelled() {}
                }) { closes++ })
        })
        fun params(name: String?) = SceneRenderParams.DEFAULT.copy(content =
            net.nevinsky.abyssus.lib.core.editor.scene.SceneContent(terrains = listOf(
                net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement("1", "terrain",
                    net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform.IDENTITY)), foliages = name?.let {
                listOf(net.nevinsky.abyssus.lib.core.editor.scene.FoliagePlacement("1", it, "terrain",
                    net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform.IDENTITY))
            }.orEmpty()))
        try {
            panel.selectEntity("1")
            val button = named(panel, "paint-foliage") as JToggleButton
            assertFalse(button.isEnabled)
            panel.setParams(params("first"))
            assertTrue(button.isEnabled)
            panel.setParams(params("second"))
            assertEquals(1, closes)
            panel.setParams(params(null))
            assertFalse(button.isEnabled)
            assertEquals(2, closes)
            assertEquals(4, opens)
        } finally { panel.dispose() }
    }

    fun testStartingPlayCancelsPainting() {
        val renderer = testRenderer()
        var cancelled = 0
        val play = PlayState(object : SceneSimulationProvider {
            override fun start(request: SimulationRequest, listener: SimulationListener) = object : SceneSimulation {
                override fun pause() {}
                override fun resume() {}
                override fun step() {}
                override fun stop() {}
                override fun input(event: SimulationInput) {}
                override fun poses(): Map<String, net.nevinsky.abyssus.lib.core.editor.content.Pose>? = null
            }
        })
        val session = FoliagePaintSession(listOf(1), object : FoliagePaint {
            override fun pressed(terrain: TerrainTarget, at: com.badlogic.gdx.math.Vector3) = true
            override fun dragged(to: com.badlogic.gdx.math.Vector3, erase: Boolean) {}
            override fun released() {}
            override fun cancelled() { cancelled++ }
        }) {}
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer, play = play,
            paintSession = { _, _, ready -> ready(session) })
        try {
            panel.selectEntity("1")
            val button = named(panel, "paint-foliage") as JToggleButton
            button.doClick()
            assertNotNull(renderer.state.paint)
            val previous = cancelled
            play.play { SimulationRequest(project, com.intellij.testFramework.LightVirtualFile("Main.scene"),
                "{}", java.io.File("."), "1") }
            assertTrue(cancelled > previous)
            assertNull(renderer.state.paint)
            assertFalse(button.isEnabled)
            assertFalse(button.isSelected)
        } finally { panel.dispose() }
    }
}
