/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.math.Vector3
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.pick.BrushStroke
import net.nevinsky.abyssus.lib.core.editor.pick.FOLIAGE_BRUSH
import net.nevinsky.abyssus.lib.core.editor.pick.SCULPT_BRUSH
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.plugin.foliage.FoliagePaintSession
import net.nevinsky.abyssus.plugin.terrain.SculptSession
import net.nevinsky.abyssus.plugin.terrain.StampImages
import java.awt.Component
import java.awt.Container
import javax.swing.JToggleButton

class BrushKindsTest : BasePlatformTestCase() {
    private val stroke = object : BrushStroke {
        override fun pressed(terrain: TerrainTarget, at: Vector3) = true
        override fun dragged(to: Vector3, reversed: Boolean) {}
        override fun released() {}
        override fun cancelled() {}
    }

    private fun named(c: Component, name: String): Component? = if (c.name == name) c else
        (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }

    private fun namesInOrder(c: Component): List<String> =
        listOfNotNull(c.name) + ((c as? Container)?.components?.flatMap(::namesInOrder) ?: emptyList())

    private fun panel(renderer: SceneRenderer, onSculptEnded: () -> Unit = {}): SceneViewPanel {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer,
            sculptSession = { _, _, _, ready ->
                ready(SculptSession(null, stroke, null, StampImages(net.nevinsky.abyssus.plugin.testCore.json)) {})
            },
            paintSession = { _, _, ready -> ready(FoliagePaintSession(listOf(3), stroke) {}) })
        panel.onSculptEnded = onSculptEnded
        return panel
    }

    fun testToggleOrderIsPaintFoliageThenItsStripThenSculptStrip() {
        val panel = panel(testRenderer())
        try {
            val order = namesInOrder(panel).filter { it in setOf("paint-foliage", "foliage-brush-strip", "sculpt-strip") }
            assertEquals(listOf("paint-foliage", "foliage-brush-strip", "sculpt-strip"), order)
        } finally { panel.dispose() }
    }

    fun testStartingPaintFoliageEndsSculptTerrainAndTellsTheHost() {
        val renderer = testRenderer()
        var ended = 0
        val panel = panel(renderer) { ended++ }
        try {
            panel.selectEntity("1")
            panel.setSculpting("1")
            assertEquals(SCULPT_BRUSH, renderer.state.brush?.kind)
            assertEquals(0, ended)
            (named(panel, "paint-foliage") as JToggleButton).doClick()
            assertEquals(FOLIAGE_BRUSH, renderer.state.brush?.kind)
            assertEquals("the host is told the sculpting is over", 1, ended)
            assertFalse(named(panel, "sculpt-strip")!!.isVisible)
        } finally { panel.dispose() }
    }

    fun testStartingSculptTerrainEndsPaintFoliageAndDeselectsItsToggle() {
        val renderer = testRenderer()
        val panel = panel(renderer)
        try {
            panel.selectEntity("1")
            val button = named(panel, "paint-foliage") as JToggleButton
            button.doClick()
            assertEquals(FOLIAGE_BRUSH, renderer.state.brush?.kind)
            assertTrue(button.isSelected)
            panel.setSculpting("1")
            assertEquals(SCULPT_BRUSH, renderer.state.brush?.kind)
            assertFalse(button.isSelected)
            assertFalse(named(panel, "foliage-brush-strip")!!.isVisible)
        } finally { panel.dispose() }
    }
}
