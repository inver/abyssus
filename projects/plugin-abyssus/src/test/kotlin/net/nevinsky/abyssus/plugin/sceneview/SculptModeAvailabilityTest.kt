/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.math.Vector3
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.pick.SceneInteraction
import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.lib.core.editor.pick.BrushStroke
import net.nevinsky.abyssus.lib.core.editor.pick.FOLIAGE_BRUSH
import net.nevinsky.abyssus.lib.core.editor.pick.SCULPT_BRUSH
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

    private fun sculptMode(r: SceneRenderer): BrushMode? = r.state.brush?.takeIf { it.kind == SCULPT_BRUSH }

    private fun paintMode(r: SceneRenderer): BrushMode? = r.state.brush?.takeIf { it.kind == FOLIAGE_BRUSH }

    private val stroke = object : BrushStroke {
        override fun pressed(terrain: TerrainTarget, at: Vector3) = true
        override fun dragged(to: Vector3, reversed: Boolean) {}
        override fun released() {}
        override fun cancelled() {}
    }

    private fun session(reason: String? = null, onClose: () -> Unit = {}) =
        SculptSession(reason, if (reason == null) stroke else null, null, StampImages(net.nevinsky.abyssus.plugin.testCore.json), onClose)

    private fun controls(renderer: SceneRenderer, play: PlayState = PlayState(null), onEnded: () -> Unit = {}, report: (String) -> Unit = {},
                         sessionOf: () -> SculptSession? = { session() }): SculptTerrainControls {
        val interaction = SceneInteraction(renderer.state, renderer.queries, OrbitCamera(Vector3(), 10f, 0f, 0.5f))
        return SculptTerrainControls(renderer, interaction, play, BrushKinds(), { _, _, _, ready -> ready(sessionOf()) },
            SculptToolFactory(FastNoiseSamplerFactory()), {}, onEnded, report).also {
            renderer.state.selectedId = "1"
        }
    }

    fun testEnteringTheModeShowsTheStripAndEndsWithTheSelection() {
        val renderer = testRenderer()
        var closed = false
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer,
            sculptSession = { id, _, _, ready -> ready(if (id == "1") session { closed = true } else null) })
        var ended = 0
        panel.onSculptEnded = { ended++ }
        try {
            panel.selectEntity("1")
            assertNull("the toggle is in the Properties panel, not the toolbar", named(panel, "sculpt-terrain"))
            panel.setSculpting("1")
            val mode = sculptMode(renderer)!!
            assertEquals("1", mode.entityId)
            assertEquals(10f, mode.radius, 0f)
            assertEquals(1f, mode.strength, 0f)
            assertTrue(named(panel, "sculpt-strip")!!.isVisible)
            assertNull("the gizmos are hidden while sculpting", net.nevinsky.abyssus.lib.core.editor.pick.gizmoHandlesFor(
                renderer.params.content, com.badlogic.gdx.graphics.PerspectiveCamera(67f, 10f, 10f), renderer.state, 10))
            panel.selectEntity("2")
            assertNull(sculptMode(renderer))
            assertTrue(closed)
            assertEquals("the host is told when the view ends it", 1, ended)
        } finally { panel.dispose() }
    }

    fun testItEndsAndSaysWhyForANonTerrain() {
        val renderer = testRenderer()
        var ended = 0
        val reasons = mutableListOf<String>()
        val controls = controls(renderer, onEnded = { ended++ }, report = { reasons += it }, sessionOf = { null })
        controls.refreshSession(force = true)
        controls.begin("1")
        assertNull(sculptMode(renderer))
        assertEquals("a toggle with nothing to sculpt is switched back off", 1, ended)
    }

    fun testItEndsWithTheSessionsReasonForAnUnreadableOrUnsafeTerrain() {
        val renderer = testRenderer()
        var ended = 0
        val reasons = mutableListOf<String>()
        val controls = controls(renderer, onEnded = { ended++ }, report = { reasons += it }, sessionOf = { session("cannot be trusted") })
        controls.refreshSession(force = true)
        controls.begin("1")
        assertNull(sculptMode(renderer))
        assertEquals(listOf("cannot be trusted"), reasons)
        assertEquals(1, ended)
    }

    fun testABrushRequestedWhileTheSessionLoadsIsKept() {
        val renderer = testRenderer()
        var ended = 0
        val pending = mutableListOf<(SculptSession?) -> Unit>()
        val interaction = SceneInteraction(renderer.state, renderer.queries, OrbitCamera(Vector3(), 10f, 0f, 0.5f))
        val controls = SculptTerrainControls(renderer, interaction, PlayState(null), BrushKinds(), { _, _, _, ready -> pending += ready },
            SculptToolFactory(FastNoiseSamplerFactory()), {}, { ended++ }, {})
        renderer.state.selectedId = "1"
        controls.refreshSession(force = true)
        controls.begin("1")
        assertNull("the session is not here yet", sculptMode(renderer))
        pending.single()(session())
        assertNotNull("the brush goes on when the session arrives", sculptMode(renderer))
        assertEquals(0, ended)
    }

    fun testSculptAndPaintFoliageNeverHoldAtOnce() {
        val renderer = testRenderer()
        val paintStroke = object : BrushStroke {
            override fun pressed(terrain: TerrainTarget, at: Vector3) = true
            override fun dragged(to: Vector3, reversed: Boolean) {}
            override fun released() {}
            override fun cancelled() {}
        }
        val panel = SceneViewPanel(SceneRenderParams.DEFAULT, renderer,
            sculptSession = { _, _, _, ready -> ready(session()) },
            paintSession = { _, _, ready -> ready(FoliagePaintSession(listOf(3), paintStroke) {}) })
        try {
            panel.selectEntity("1")
            panel.setSculpting("1")
            assertNotNull(sculptMode(renderer))
            (named(panel, "paint-foliage") as JToggleButton).doClick()
            assertNotNull(paintMode(renderer))
            assertNull(sculptMode(renderer))
            panel.setSculpting("1")
            assertNotNull(sculptMode(renderer))
            assertNull(paintMode(renderer))
            panel.setSculpting(null)
            assertNull(sculptMode(renderer))
        } finally { panel.dispose() }
    }

    fun testShiftInvertsAndPlayEndsTheBrush() {
        val renderer = testRenderer()
        val controls = controls(renderer)
        controls.refreshSession(force = true)
        controls.begin("1")
        assertFalse(sculptMode(renderer)!!.reversed)
        controls.update(shift = true)
        assertTrue(sculptMode(renderer)!!.reversed)
        controls.update(shift = false)
        assertFalse(sculptMode(renderer)!!.reversed)
        controls.end()
        assertNull(sculptMode(renderer))
    }

    fun testTheStripBuildsTheChosenTool() {
        val renderer = testRenderer()
        val interaction = SceneInteraction(renderer.state, renderer.queries, OrbitCamera(Vector3(), 10f, 0f, 0.5f))
        val controls = SculptTerrainControls(renderer, interaction, PlayState(null), BrushKinds(), { _, _, _, ready -> ready(session()) },
            SculptToolFactory(FastNoiseSamplerFactory()), {}, {}, {})
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
