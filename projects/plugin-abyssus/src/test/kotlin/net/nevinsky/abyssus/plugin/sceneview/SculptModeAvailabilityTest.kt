/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.math.Vector3
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.pick.FoliagePaint
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.pick.SceneInteraction
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainSculpt
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.terrain.FastNoiseSamplerFactory
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.FlattenTool
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.RaiseLowerTool
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SetHeightTool
import net.nevinsky.abyssus.plugin.foliage.FoliagePaintSession
import net.nevinsky.abyssus.plugin.terrain.SculptSession
import net.nevinsky.abyssus.plugin.terrain.SculptToolFactory
import net.nevinsky.abyssus.plugin.terrain.SculptToolKind
import net.nevinsky.abyssus.plugin.terrain.StampImages
import java.awt.Component
import java.awt.Container
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JToggleButton

class SculptModeAvailabilityTest : BasePlatformTestCase() {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load() // the gizmo query projects through the native Matrix4.prj
    }

    private fun named(c: Component, name: String): Component? = if (c.name == name) c else
        (c as? Container)?.components?.firstNotNullOfOrNull { named(it, name) }

    private val stroke = object : TerrainSculpt {
        override fun pressed(terrain: TerrainTarget, at: Vector3) = true
        override fun dragged(to: Vector3, inverted: Boolean) {}
        override fun released() {}
        override fun cancelled() {}
    }

    private fun session(reason: String? = null, onClose: () -> Unit = {}) =
        SculptSession(reason, if (reason == null) stroke else null, null, StampImages(net.nevinsky.abyssus.plugin.testCore.json), onClose)

    fun testEnteringTheModeShowsTheStripAndEndsWithTheSelection() {
        val renderer = testRenderer()
        var closed = false
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer,
            sculptSession = { id, _, _, ready -> ready(if (id == "1") session { closed = true } else null) })
        try {
            panel.selectEntity("1")
            val button = named(panel, "sculpt-terrain") as JToggleButton
            assertTrue(button.isEnabled)
            button.doClick()
            val mode = renderer.state.sculpt!!
            assertEquals("1", mode.entityId)
            assertEquals(10f, mode.radius, 0f)
            assertEquals(1f, mode.strength, 0f)
            assertTrue(named(panel, "sculpt-strip")!!.isVisible)
            assertNull("the gizmos are hidden while sculpting", net.nevinsky.abyssus.lib.core.editor.pick.gizmoHandlesFor(
                renderer.params.content, com.badlogic.gdx.graphics.PerspectiveCamera(67f, 10f, 10f), renderer.state, 10))
            panel.selectEntity("2")
            assertNull(renderer.state.sculpt)
            assertTrue(closed)
            assertFalse(button.isSelected)
        } finally { panel.dispose() }
    }

    fun testItIsDisabledForANonTerrainWithAReason() {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer(), sculptSession = { _, _, _, ready -> ready(null) })
        try {
            panel.selectEntity("1")
            val button = named(panel, "sculpt-terrain") as JToggleButton
            assertFalse(button.isEnabled)
            assertTrue(button.toolTipText.contains("Select a terrain"))
        } finally { panel.dispose() }
    }

    fun testItIsDisabledWithTheSessionsReasonForAnUnreadableOrUnsafeTerrain() {
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, testRenderer(), sculptSession = { _, _, _, ready -> ready(session("cannot be trusted")) })
        try {
            panel.selectEntity("1")
            val button = named(panel, "sculpt-terrain") as JToggleButton
            assertFalse(button.isEnabled)
            assertEquals("cannot be trusted", button.toolTipText)
        } finally { panel.dispose() }
    }

    fun testSculptAndPaintFoliageNeverHoldAtOnce() {
        val renderer = testRenderer()
        val paintStroke = object : FoliagePaint {
            override fun pressed(terrain: TerrainTarget, at: Vector3) = true
            override fun dragged(to: Vector3, erase: Boolean) {}
            override fun released() {}
            override fun cancelled() {}
        }
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer,
            sculptSession = { _, _, _, ready -> ready(session()) },
            paintSession = { _, _, ready -> ready(FoliagePaintSession(listOf(3), paintStroke) {}) })
        try {
            panel.selectEntity("1")
            (named(panel, "sculpt-terrain") as JToggleButton).doClick()
            assertNotNull(renderer.state.sculpt)
            (named(panel, "paint-foliage") as JToggleButton).doClick()
            assertNotNull(renderer.state.paint)
            assertNull(renderer.state.sculpt)
            (named(panel, "sculpt-terrain") as JToggleButton).doClick()
            assertNotNull(renderer.state.sculpt)
            assertNull(renderer.state.paint)
        } finally { panel.dispose() }
    }

    fun testShiftInvertsAndPlayEndsTheBrush() {
        val renderer = testRenderer()
        val play = PlayState(null)
        val interaction = SceneInteraction(renderer.state, renderer.queries, OrbitCamera(Vector3(), 10f, 0f, 0.5f))
        val controls = SculptTerrainControls(renderer, interaction, play, { _, _, _, ready -> ready(session()) },
            SculptToolFactory(FastNoiseSamplerFactory()), {}, {})
        renderer.state.selectedId = "1"
        controls.refreshSession(force = true)
        (named(JPanel().also { controls.addTo(it) }, "sculpt-terrain") as JToggleButton).doClick()
        assertFalse(renderer.state.sculpt!!.inverted)
        controls.update(shift = true)
        assertTrue(renderer.state.sculpt!!.inverted)
        controls.update(shift = false)
        assertFalse(renderer.state.sculpt!!.inverted)
        controls.end()
        assertNull(renderer.state.sculpt)
    }

    fun testTheStripBuildsTheChosenTool() {
        val renderer = testRenderer()
        val interaction = SceneInteraction(renderer.state, renderer.queries, OrbitCamera(Vector3(), 10f, 0f, 0.5f))
        val controls = SculptTerrainControls(renderer, interaction, PlayState(null), { _, _, _, ready -> ready(session()) },
            SculptToolFactory(FastNoiseSamplerFactory()), {}, {})
        val bar = JPanel().also { controls.addTo(it) }
        @Suppress("UNCHECKED_CAST")
        val box = named(bar, "sculpt-tool") as JComboBox<SculptToolKind>
        assertTrue(controls.tool() is RaiseLowerTool)
        box.selectedItem = SculptToolKind.FLATTEN
        assertTrue(controls.tool() is FlattenTool)
        box.selectedItem = SculptToolKind.SET_HEIGHT
        assertTrue(controls.tool() is SetHeightTool)
        box.selectedItem = SculptToolKind.STAMP
        assertNull("a Stamp without a readable image has no tool", controls.tool())
    }
}
