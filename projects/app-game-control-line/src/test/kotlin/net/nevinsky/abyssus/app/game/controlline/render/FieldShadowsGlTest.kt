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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The sun's shadow with foliage (opt-in GL): an OBJECT tree outside the viewer's frustum still casts into the map, a
 * DETAIL layer never does, and the viewer's own buffers are the same after the light pass as before it.
 */
class FieldShadowsGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val trees = listOf(FoliageCopy(0, 30f, 30f, 0f, 4f), FoliageCopy(0, 20f, 40f, 1f, 4f))
    private val grass = List(40) { FoliageCopy(0, 10f + it, 130f + it % 7, 0f, 1f) }

    /** Behind the trees, looking away from them. */
    private fun away() = PerspectiveCamera(60f, 640f, 480f).apply {
        position.set(40f, 20f, 120f)
        lookAt(40f, 20f, 800f)
        near = 1f
        far = 2000f
        update()
    }

    /** Runs [project]'s field until its foliage settled, then returns the renderer's numbers. */
    private fun settle(project: FieldFoliageProject, block: (FieldRenderer, FieldScene) -> Unit) {
        val messages = mutableListOf<String>()
        val log = warningsTo(messages)
        val ctx = BaseCtx(project.dir.path, "/shader", ControlLineGame::class.java, log)
        val loader = RuntimeSceneLoader(ctx.sceneLoader, ctx.ecsLoader, log)
        val renderer = FieldRenderer(ctx)
        try {
            val field = FieldScene(requireNotNull(loader.loadFromText(project.scene())))
            var settled = false
            repeat(900) {
                if (settled) return@repeat
                renderer.draw(field, away(), seconds = 0.016f)
                settled = renderer.loaded(field) && renderer.placementDrawables().size == 1
                if (!settled) Thread.sleep(10)
            }
            assertTrue("never settled: $messages", settled)
            renderer.draw(field, away(), seconds = 0.016f)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            block(renderer, field)
        } finally {
            renderer.dispose()
            ctx.executor.shutdownNow()
        }
    }

    @Test
    fun anOffScreenTreeShadowsTheMapAndGrassNeverContributesDepth() = TestGl.run {
        Gdx.files = Lwjgl3Files()
        ShaderProgram.pedantic = false
        val bare = FieldFoliageProject().use { project ->
            project.foliage(emptyList())
            var texels = 0
            settle(project) { renderer, _ -> texels = renderer.sunShadows.castTexels() }
            texels
        }
        val withGrass = FieldFoliageProject().use { project ->
            project.foliage(emptyList(), grass)
            var texels = 0
            var casters = -1
            settle(project) { renderer, _ ->
                texels = renderer.sunShadows.castTexels()
                val drawable = renderer.placementDrawables().values.single()
                casters = drawable.casterCopies
                assertTrue("grass was not drawn for the viewer", drawable.drawnCopies >= 0)
            }
            assertEquals("a DETAIL layer produced casters", 0, casters)
            texels
        }
        assertEquals("grass added depth to the sun's map", bare, withGrass)

        FieldFoliageProject().use { project ->
            project.foliage(trees, grass)
            settle(project) { renderer, _ ->
                val drawable = renderer.placementDrawables().values.single()
                // the trees are behind the camera: the viewer does not draw them, the light still sees them
                assertTrue("the trees were drawn for a viewer looking away: ${drawable.drawnCopies}", drawable.drawnCopies <= grass.size)
                assertEquals(trees.size, drawable.casterCopies)
                assertTrue("the off-screen trees did not reach the shadow map", renderer.sunShadows.castTexels() > bare)
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            }
        }
    }

    @Test
    fun theViewersBuffersAreTheSameBeforeAndAfterTheLightPass() = TestGl.run {
        Gdx.files = Lwjgl3Files()
        ShaderProgram.pedantic = false
        FieldFoliageProject().use { project ->
            project.foliage(trees, grass)
            settle(project) { renderer, field ->
                val drawable = renderer.placementDrawables().values.single()
                val drawn = drawable.drawnCopies
                val rebuilds = drawable.rebuilds
                repeat(5) { renderer.draw(field, away(), seconds = 0.016f) }
                assertEquals(drawn, drawable.drawnCopies)
                assertEquals("the unchanged view refilled its buffers", rebuilds, drawable.rebuilds)
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            }
        }
    }
}
