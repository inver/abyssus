/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import net.nevinsky.abyssus.lib.core.editor.pick.LineSink
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.content.Rgba

import com.badlogic.gdx.graphics.PerspectiveCamera
import net.nevinsky.abyssus.lib.core.editor.parseScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SceneOverlayHostTest {
    private val scene = parseScene(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText())
    private val content = sceneContentOf(scene)

    private fun view(onTop: Boolean = false) =
        OverlayView(content, scene.ecs, null, null, PerspectiveCamera(), 600, playing = false, onTop = onTop)

    /** Draws a small cross at each entity position the view shows. */
    private class MarkerOverlay : SceneOverlay {
        val seen = mutableListOf<Map<String, Vec3>>()
        override fun draw(view: OverlayView, lines: LineSink) {
            seen += view.content.entityPositions
            for (p in view.content.entityPositions.values) lines.line(Vec3(p.x - 0.1f, p.y, p.z), Vec3(p.x + 0.1f, p.y, p.z), Rgba(1f, 1f, 1f, 1f))
        }
    }

    private class Recorder : LineSink {
        val centres = mutableListOf<Vec3>()
        override fun line(from: Vec3, to: Vec3, color: Rgba) {
            centres += Vec3((from.x + to.x) / 2, (from.y + to.y) / 2, (from.z + to.z) / 2)
        }
    }

    @Test
    fun anOverlaySeesTheEntityPositionsOfMainScene() {
        val overlay = MarkerOverlay()
        val sink = Recorder()
        SceneOverlayHost(listOf(NamedOverlay("Test", overlay))) { m, e -> throw AssertionError(m, e) }.draw(view(), sink)
        assertEquals(content.entityPositions, overlay.seen.single())
        for (id in listOf("0", "2", "6")) {
            val model = content.models.single { it.entityId == id }.transform.position
            assertTrue("no marker at entity $id", model in sink.centres)
        }
    }

    @Test
    fun aThrowingOverlayIsSwitchedOffOnceAndTheOthersKeepDrawing() {
        var calls = 0
        val thrower = object : SceneOverlay {
            var disposed = false
            override fun draw(view: OverlayView, lines: LineSink) {
                calls++
                error("broken")
            }
            override fun dispose() { disposed = true }
        }
        val good = MarkerOverlay()
        val errors = mutableListOf<String>()
        val host = SceneOverlayHost(listOf(NamedOverlay("Broken Plugin", thrower), NamedOverlay("Test", good))) { m, _ -> errors += m }
        host.draw(view(), Recorder())
        host.draw(view(onTop = true), Recorder())
        host.draw(view(), Recorder())
        assertEquals(1, calls)
        assertEquals(1, errors.size)
        assertTrue(errors.single(), errors.single().contains("Broken Plugin"))
        assertTrue(thrower.disposed)
        assertEquals(3, good.seen.size)
        assertEquals(listOf("Test"), host.overlays.map { it.source })
    }
}
