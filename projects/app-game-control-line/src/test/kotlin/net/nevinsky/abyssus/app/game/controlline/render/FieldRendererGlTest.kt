/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import net.nevinsky.abyssus.app.game.controlline.ControlLineGame
import net.nevinsky.abyssus.app.game.controlline.FieldFoliageProject
import net.nevinsky.abyssus.lib.core.BaseCtx
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.scene.RuntimeSceneLoader
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * [FieldRenderer]'s foliage on a real GL context (opt-in, `-Dabyssus.glTests=true`): each terrain placement owns its own
 * drawable, a light pass leaves the viewer's buffers alone, and replacing the scene leaves no placement behind.
 */
class FieldRendererGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val tree = listOf(FoliageCopy(0, 100f, 100f, 0.3f, 3f), FoliageCopy(0, 60f, 140f, 1f, 2f))

    /** A camera over x = 250 looking at both terrains (0..200 and 300..500) from the south. */
    private fun camera() = PerspectiveCamera(60f, 640f, 480f).apply {
        position.set(250f, 60f, -250f)
        lookAt(250f, 0f, 100f)
        near = 1f
        far = 3000f
        update()
    }

    @Test
    fun twoTransformedPlacementsOfOneAssetDrawIndependentlyAndASceneReplacementLeavesNothing() = TestGl.run {
        Gdx.files = Lwjgl3Files()
        ShaderProgram.pedantic = false
        FieldFoliageProject().use { project ->
            project.foliage(tree)
            val messages = mutableListOf<String>()
            val log = warningsTo(messages)
            val ctx = BaseCtx(project.dir.path, "/shader", ControlLineGame::class.java, log)
            val loader = RuntimeSceneLoader(ctx.sceneLoader, ctx.ecsLoader, log)
            val renderer = FieldRenderer(ctx)
            try {
                val camera = camera()
                fun field() = FieldScene(requireNotNull(loader.loadFromText(project.scene(xs = listOf(0f, 300f)))))
                var field = field()
                var settled = false
                repeat(900) {
                    if (settled) return@repeat
                    renderer.draw(field, camera, seconds = 0.016f)
                    val placements = renderer.placementDrawables()
                    settled = renderer.loaded(field) && placements.size == 2 && placements.values.all { it.drawnCopies > 0 }
                    if (!settled) Thread.sleep(10)
                }
                assertTrue("the placements never settled: $messages", settled)
                var drawn = renderer.placementDrawables().values.toList()
                assertNotSame(drawn[0], drawn[1])
                // both owners show all their copies, independent of each other's culling and of draw order
                assertEquals(tree.size, drawn[0].drawnCopies)
                assertEquals(tree.size, drawn[1].drawnCopies)
                val rebuilds = drawn.map { it.rebuilds }
                renderer.draw(field, camera, seconds = 0.016f)
                assertEquals("a light pass changed the viewer's buffers", rebuilds, drawn.map { it.rebuilds })
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())

                // look away from one terrain: only the other still shows its copies
                val away = camera().apply { lookAt(250f, 0f, -900f); update() }
                renderer.draw(field, away, seconds = 0.016f)
                assertEquals(listOf(0, 0), drawn.map { it.drawnCopies })
                renderer.draw(field, camera, seconds = 0.016f)
                assertEquals(listOf(tree.size, tree.size), drawn.map { it.drawnCopies })

                // replacing the scene repeatedly leaves exactly the new scene's two placements and no GL errors
                repeat(6) {
                    val old = renderer.placementDrawables().values.toList()
                    field = field()
                    repeat(900) { frame ->
                        renderer.draw(field, camera, seconds = 0.016f)
                        val now = renderer.placementDrawables().values
                        if (renderer.loaded(field) && now.size == 2 && now.all { it.drawnCopies > 0 }) return@repeat
                        Thread.sleep(10)
                    }
                    val now = renderer.placementDrawables().values.toList()
                    assertEquals(2, now.size)
                    assertTrue(now.none { n -> old.any { it === n } })
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
                    drawn = now
                }
            } finally {
                renderer.dispose()
                ctx.executor.shutdownNow()
            }
        }
    }
}
