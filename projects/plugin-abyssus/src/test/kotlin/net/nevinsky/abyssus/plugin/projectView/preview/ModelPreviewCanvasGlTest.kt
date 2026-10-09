/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView.preview

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.gdx.assimp.UpAxis
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ImportSettings
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ImportTransform
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.LengthUnit
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ModelSource
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ModelSourceOpener
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.plugin.sceneview.GlHarness
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.awt.BorderLayout
import java.io.File
import java.nio.file.Files
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * The Import Model preview in a real GL 3.2 core canvas: it builds and draws the converted model (textures uploaded one
 * per frame), frames it, plays the chosen animation in a loop, rebuilds on a new transform, shows a failed frame as
 * text, and frees its GL while still showing. Opens a window: runs only with `-Dabyssus.glTests=true`.
 */
class ModelPreviewCanvasGlTest {
    private val fixtures = Files.createTempDirectory("abyssus_preview").toFile().also {
        File("../lib-core-editor/src/test/resources/modelimport").copyRecursively(it)
    }
    private val sources = ArrayList<ModelSource>()
    private val windows = ArrayList<JFrame>()

    @Before
    fun glOnly() {
        assumeTrue("GL tests open a window: -Dabyssus.glTests=true", GlHarness.enabled)
        GdxNativesLoader.load()
    }

    @After
    fun close() {
        SwingUtilities.invokeAndWait { windows.forEach(JFrame::dispose) }
        sources.forEach(ModelSource::close)
    }

    /** The model as the dialog hands it to the preview: transformed, its images decoded off the AWT thread. */
    private fun preview(name: String, settings: ImportSettings): PreviewModel {
        val source = ModelSourceOpener().open(File(fixtures, name)).also(sources::add)
        val transformed = ImportTransform().apply(source.data, settings)
        val file = FileHandle(source.file)
        return PreviewModel(transformed.data, AssimpModelLoader().decodeTextures(transformed.data, file), file, transformed.size)
    }

    private fun shown(canvas: ModelPreviewCanvas): ModelPreviewCanvas {
        SwingUtilities.invokeAndWait {
            windows += JFrame("abyssus preview GL test").apply {
                layout = BorderLayout()
                add(canvas, BorderLayout.CENTER)
                setSize(480, 360)
                isVisible = true
            }
        }
        return canvas
    }

    private fun canvas(): ModelPreviewCanvas {
        lateinit var canvas: ModelPreviewCanvas
        SwingUtilities.invokeAndWait { canvas = ModelPreviewCanvas() }
        return canvas
    }

    private fun state(canvas: ModelPreviewCanvas): ModelPreviewCanvas.State {
        lateinit var state: ModelPreviewCanvas.State
        SwingUtilities.invokeAndWait { state = canvas.state() }
        return state
    }

    /** Waits (off the AWT thread, so frames keep coming) until [done] holds; fails after [millis]. */
    private fun await(canvas: ModelPreviewCanvas, what: String, millis: Long = 15_000, done: (ModelPreviewCanvas.State) -> Boolean): ModelPreviewCanvas.State {
        val end = System.currentTimeMillis() + millis
        while (true) {
            val s = state(canvas)
            if (done(s)) return s
            check(System.currentTimeMillis() < end) { "timed out waiting for $what; message: ${s.message}, frames: ${s.frames}" }
            Thread.sleep(30)
        }
    }

    private fun onAwt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)

    @Test
    fun drawsTheTexturedModelFramedAndRebuildsItOnANewTransform() {
        val canvas = shown(canvas())
        onAwt { canvas.sampleCentre = true }
        val empty = await(canvas, "a first frame") { it.frames > 2 }
        assertFalse(empty.built)

        onAwt { canvas.show(preview("crate.glb", ImportSettings("p", LengthUnit.M, UpAxis.Y))) }
        val crate = await(canvas, "the crate, built after its texture upload") { it.built && !it.uploading }
        assertNull(crate.message)
        assertEquals(Vector3(1f, 1f, 1f).toString(), crate.size.toString())
        val drawn = await(canvas, "a few frames of the crate") { it.frames > crate.frames + 3 }
        assertNotEquals("the framed crate covers the centre, not the background", BACKGROUND, drawn.centre)

        // a new unit: the same source, transformed again, replaces the model and the camera follows its size
        onAwt { canvas.show(preview("crate.obj", ImportSettings("p", LengthUnit.M, UpAxis.Y))) }
        val big = await(canvas, "the 100 m crate") { it.built && !it.uploading && it.size.y > 99f }
        val bigDrawn = await(canvas, "frames of the 100 m crate") { it.frames > big.frames + 3 }
        assertNotEquals("the 100 m crate is framed too", BACKGROUND, bigDrawn.centre)

        onAwt { canvas.release() }
        val released = state(canvas)
        assertTrue(released.released)
        Thread.sleep(200)
        assertEquals("no frame after release", released.frames, state(canvas).frames)
    }

    @Test
    fun playsTheChosenAnimationInALoop() {
        val canvas = shown(canvas())
        onAwt {
            canvas.play("Run")
            canvas.show(preview("rig.fbx", ImportSettings("p", LengthUnit.CM, UpAxis.Z)))
        }
        await(canvas, "Run playing") { it.built && it.playing == "Run" }
        Thread.sleep(1_500) // Run is 1 s long: it has looped by now
        val looped = state(canvas)
        assertEquals("Run", looped.playing)
        assertTrue("the time wraps within the clip: ${looped.animationTime}", looped.animationTime <= 1.0001f)

        onAwt { canvas.play("Idle") }
        await(canvas, "Idle playing") { it.playing == "Idle" }
        onAwt { canvas.play(null) }
        await(canvas, "no animation") { it.playing == null }
        onAwt { canvas.release() }
    }

    @Test
    fun aFrameThatFailsIsShownAsTextAndStopsDrawing() {
        val canvas = shown(canvas())
        await(canvas, "a first frame") { it.frames > 0 }
        val broken = ModelData().apply {
            nodes.add(ModelNode().apply {
                id = "broken"
                parts = arrayOf(ModelNodePart().apply { meshPartId = "missing"; materialId = "missing" })
            })
        }
        onAwt { canvas.show(PreviewModel(broken, HashMap(), FileHandle(File(fixtures, "crate.obj")), Vector3(1f, 1f, 1f))) }
        val failed = await(canvas, "the failure text") { it.message != null }
        assertTrue(failed.message, failed.message!!.contains("Create still writes"))
        Thread.sleep(200)
        assertEquals("drawing stopped", failed.frames, state(canvas).frames)
        onAwt { canvas.release() }
    }

    @Test
    fun aCanvasThatNeverShowedReleasesWithoutGl() {
        val canvas = canvas()
        onAwt { canvas.show(preview("crate.obj", ImportSettings("p", LengthUnit.CM, UpAxis.Y))) }
        onAwt { canvas.release() }
        val s = state(canvas)
        assertTrue(s.released)
        assertEquals(0, s.frames)
        onAwt { canvas.release() } // idempotent
    }

    private companion object {
        /** The preview's clear colour (0.16, 0.17, 0.19) as RGB bytes. */
        val BACKGROUND = (41 shl 16) or (43 shl 8) or 48
    }
}
