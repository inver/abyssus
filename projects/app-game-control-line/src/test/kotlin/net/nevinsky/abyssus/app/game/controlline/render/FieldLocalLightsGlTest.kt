/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import java.io.File
import java.nio.file.Files
import net.nevinsky.abyssus.app.game.controlline.ControlLineGame
import net.nevinsky.abyssus.app.game.controlline.components.CONTROL_LINE_COMPONENTS
import net.nevinsky.abyssus.lib.core.BaseCtx
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.LightDto
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.scene.RuntimeSceneLoader
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The hangar's point and spot lights in the raster game (opt-in GL; OpenSpec `control-line-pbr-rendering`, "Scene
 * lights light the models" and "No directional light means no sun"). Each case draws a copy of the bundled hangar from
 * its own camera with its lights changed in memory, and compares windows around projected world points with the same
 * frame drawn without the scene's local lights.
 */
class FieldLocalLightsGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val width = 1280
    private val height = 720

    /** One drawn frame: its pixels (RGBA8888, bottom-up) and where world points land on it. */
    private class Frame(val pixmap: Pixmap, val project: (Vector3) -> Vector3) {
        /** Mean of R, G and B (0..255) over the [half]-pixel window around [world]. */
        fun meanAround(world: Vector3, half: Int = 6): Double {
            val at = project(world)
            var sum = 0.0
            var n = 0
            for (y in at.y.toInt() - half..at.y.toInt() + half) for (x in at.x.toInt() - half..at.x.toInt() + half) {
                val p = pixmap.getPixel(x, pixmap.height - 1 - y)
                sum += ((p ushr 24) and 0xff) + ((p ushr 16) and 0xff) + ((p ushr 8) and 0xff)
                n++
            }
            return sum / (3.0 * n)
        }

        /** The largest channel difference in the window around [world] between this frame and [other]. */
        fun maxDiffAround(other: Frame, world: Vector3, half: Int = 6): Int {
            val at = project(world)
            var worst = 0
            for (y in at.y.toInt() - half..at.y.toInt() + half) for (x in at.x.toInt() - half..at.x.toInt() + half) {
                val a = pixmap.getPixel(x, pixmap.height - 1 - y)
                val b = other.pixmap.getPixel(x, pixmap.height - 1 - y)
                for (shift in intArrayOf(24, 16, 8)) {
                    worst = maxOf(worst, kotlin.math.abs(((a ushr shift) and 0xff) - ((b ushr shift) and 0xff)))
                }
            }
            return worst
        }
    }

    /** The light entities of [kind] of a loaded scene. */
    private fun lightsOf(entities: Iterable<Entity>, kind: EntityType) =
        entities.filter { it.getComponent(HeaderComponent::class.java)?.type == kind }

    /**
     * Draws the bundled hangar after [change] edits its loaded entities, until everything is built, and hands the frame
     * and the renderer to [check]. Every case starts from a fresh copy of the project and a fresh renderer.
     */
    private fun hangar(
        change: (Iterable<Entity>) -> Unit,
        settings: FieldRenderSettings = FieldRenderSettings(),
        before: (FieldRenderer) -> Unit = {},
        check: (Frame, FieldRenderer, FieldScene) -> Unit,
    ) {
        Gdx.files = Lwjgl3Files()
        ShaderProgram.pedantic = false
        val project = Files.createTempDirectory("lights-control-line").toFile()
        File(checkNotNull(System.getProperty("controlLine.project"))).copyRecursively(project, overwrite = true)
        val messages = mutableListOf<String>()
        val log = warningsTo(messages)
        val ctx =
            BaseCtx(project.path, "/shader", ControlLineGame::class.java, log).also {
                it.componentRegistry.registerAll(CONTROL_LINE_COMPONENTS)
            }
        val loader = RuntimeSceneLoader(ctx.sceneLoader, ctx.ecsLoader, log)
        val renderer = FieldRenderer(ctx, requested = settings).also(before)
        try {
            val loaded = requireNotNull(loader.load("Hangar.scene")) { messages.joinToString("\n") }
            change(loaded.engine.entities)
            val field = FieldScene(loaded)
            val cameras = Cameras(width.toFloat(), height.toFloat())
            val (at, camera) = checkNotNull(field.camera)
            var settled = 0
            var frames = 0
            while (settled < 10) {
                cameras.fixed(at.localPosition, camera.direction, camera.fieldOfView)
                Gdx.gl.glViewport(0, 0, width, height)
                renderer.draw(field, cameras.camera, seconds = 0f)
                settled =
                    if (renderer.loaded(field) && !ctx.assetStorage.isLoading()) {
                        settled + 1
                    } else {
                        0
                    }
                if (settled == 0) {
                    Thread.sleep(5)
                }
                check(++frames < 20_000) { "the hangar never loaded: $messages" }
            }
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            val pixmap = Pixmap.createFromFrameBuffer(0, 0, width, height)
            val view = cameras.camera
            try {
                check(
                    Frame(pixmap) { world ->
                        view.project(Vector3(world), 0f, 0f, width.toFloat(), height.toFloat()).also {
                            // project gives y up from the bottom; the windows read top-down
                            it.y = height - 1 - it.y
                        }
                    },
                    renderer,
                    field,
                )
            } finally {
                pixmap.dispose()
            }
        } finally {
            renderer.dispose()
            ctx.close()
            project.deleteRecursively()
        }
    }

    /**
     * The hangar's emissive strips glow with bloom on (OpenSpec `control-line-post-processing`, "Bright emissive
     * blooms"): the wall just below a strip is brighter, and the floor far from any strip is as it was.
     */
    @Test
    fun theEmissiveStripsBloom() = TestGl.run(600, 1280, 720) {
        var without: Frame? = null
        var pixels: Pixmap? = null
        hangar({}, FieldRenderSettings(post = PostSettings.OFF)) { frame, _, _ ->
            val copy = Pixmap(frame.pixmap.width, frame.pixmap.height, frame.pixmap.format)
            copy.blending = Pixmap.Blending.None
            copy.drawPixmap(frame.pixmap, 0, 0)
            pixels = copy
            without = Frame(copy, frame.project)
        }
        try {
            hangar({}, FieldRenderSettings(post = PostSettings.OFF.copy(bloom = true))) { frame, _, _ ->
                val base = checkNotNull(without)
                // the screen point below the middle strip (top-down y): projected from no world point, read directly
                val below = Vector3(640f, 92f, 0f)
                val floor = Vector3(640f, 700f, 0f)
                val screen = Frame(frame.pixmap) { it }
                val plain = Frame(base.pixmap) { it }
                val glow = screen.meanAround(below, half = 3)
                val dark = plain.meanAround(below, half = 3)
                assertTrue("no glow below the strip: $glow vs $dark", glow > dark + 2.0)
                val diff = screen.maxDiffAround(plain, floor, half = 4)
                assertTrue("bloom changed the floor far from the strips by $diff", diff <= 2)
            }
        } finally {
            pixels?.dispose()
        }
    }

    /**
     * The hangar has no sky: it is lit by a probe of itself (OpenSpec `control-line-pbr-rendering`, "Interiors reflect
     * themselves"), corrected to the box around the room, and its floor shows the room instead of the flat ambient.
     */
    @Test
    fun theHangarIsLitByAProbeOfItself() = TestGl.run(600, 1280, 720) {
        var flat: Frame? = null
        var pixels: Pixmap? = null
        hangar({}, FieldRenderSettings(reflectionProbe = false)) { frame, renderer, _ ->
            assertTrue(!renderer.litByProbe)
            val copy = Pixmap(frame.pixmap.width, frame.pixmap.height, frame.pixmap.format)
            copy.blending = Pixmap.Blending.None
            copy.drawPixmap(frame.pixmap, 0, 0)
            pixels = copy
            flat = Frame(copy, frame.project)
        }
        try {
            hangar({}) { frame, renderer, field ->
                assertTrue("the hangar is not lit by its probe", renderer.litByProbe)
                val box = checkNotNull(renderer.probeBounds) { "no box for the probe's reflection" }
                val eye = checkNotNull(field.camera).first.localPosition
                assertTrue("the probe is not at the camera: ${box.probe} vs $eye", box.probe.epsilonEquals(eye, 1e-4f))
                assertTrue("the room's box does not reach the floor: ${box.min}", box.min.y <= 0.1f)
                assertTrue("the room's box is not around the table: $box", box.max.y > 3f && box.min.z < -2f)
                val floor = Vector3(0f, 0.08f, -0.6f)
                val lit = frame.meanAround(floor, half = 10)
                val before = checkNotNull(flat).meanAround(floor, half = 10)
                println("hangar floor: probe $lit, flat ambient $before")
                assertTrue("the probe did not change the floor: $lit vs $before", kotlin.math.abs(lit - before) > 1.0)
            }
        } finally {
            pixels?.dispose()
        }
    }

    /**
     * The display settings' low quality (OpenSpec `control-line-post-processing`, "Low quality"): the frame is the one
     * drawn without bloom and ambient occlusion, the vignette and grade kept.
     */
    @Test
    fun lowQualityDrawsWithoutBloomAndOcclusion() = TestGl.run(600, 1280, 720) {
        var cheap: Frame? = null
        var pixels: Pixmap? = null
        hangar({}, FieldRenderSettings(post = PostSettings(bloom = false, ambientOcclusion = false))) { frame, _, _ ->
            val copy = Pixmap(frame.pixmap.width, frame.pixmap.height, frame.pixmap.format)
            copy.blending = Pixmap.Blending.None
            copy.drawPixmap(frame.pixmap, 0, 0)
            pixels = copy
            cheap = Frame(copy, frame.project)
        }
        try {
            hangar({}, FieldRenderSettings(), before = { it.highQuality = false }) { frame, _, _ ->
                val base = Frame(checkNotNull(cheap).pixmap) { it }
                val shown = Frame(frame.pixmap) { it }
                for (point in listOf(Vector3(640f, 92f, 0f), Vector3(640f, 400f, 0f), Vector3(200f, 650f, 0f))) {
                    val diff = shown.maxDiffAround(base, point, half = 4)
                    assertTrue("low quality differs from no bloom and no occlusion by $diff at $point", diff <= 1)
                }
            }
        } finally {
            pixels?.dispose()
        }
    }

    /**
     * The hangar has no sun, so its lights cast the shadows (OpenSpec `control-line-pbr-rendering`, "Lights cast
     * shadows in a scene without a sun"): the floor under the table, hidden from the spots above it, and the
     * warehouse's walls, shaded by its pillars and beams from the lamps, are darker than without the lights' shadows.
     */
    @Test
    fun theHangarsLightsCastShadows() = TestGl.run(600, 1280, 720) {
        var unshadowed: Frame? = null
        var pixels: Pixmap? = null
        hangar({}, FieldRenderSettings(localShadows = false)) { frame, renderer, _ ->
            assertEquals(0, renderer.sunShadows.spotTiles)
            val copy = Pixmap(frame.pixmap.width, frame.pixmap.height, frame.pixmap.format)
            copy.blending = Pixmap.Blending.None
            copy.drawPixmap(frame.pixmap, 0, 0)
            pixels = copy
            unshadowed = Frame(copy, frame.project)
        }
        try {
            hangar({}) { frame, renderer, _ ->
                assertEquals("the four spots each have a tile", 4, renderer.sunShadows.spotTiles)
                // the lamps nearest the camera shadow the warehouse itself, their cubes drawn once
                assertEquals(POINT_SHADOW_LIGHTS, renderer.sunShadows.pointShadows)
                assertEquals("the lamps' cubes were drawn more than once", 1, renderer.sunShadows.pointBakes)
                val wall = Vector3(640f, 200f, 0f)
                val wallShadowed = Frame(frame.pixmap) { it }.meanAround(wall, half = 60)
                val wallOpen = Frame(checkNotNull(unshadowed).pixmap) { it }.meanAround(wall, half = 60)
                println("hangar wall: shadowed $wallShadowed, without light shadows $wallOpen")
                assertTrue("the warehouse casts no shadow: $wallShadowed vs $wallOpen", wallShadowed < wallOpen - 2.0)
                val under = Vector3(640f, 632f, 0f)
                val shadowed = Frame(frame.pixmap) { it }.meanAround(under, half = 10)
                val open = Frame(checkNotNull(unshadowed).pixmap) { it }.meanAround(under, half = 10)
                println("hangar floor under the table: shadowed $shadowed, without spot shadows $open")
                assertTrue("the table casts no shadow: $shadowed vs $open", shadowed < open - 3.0)
            }
        } finally {
            pixels?.dispose()
        }
    }

    /** Leaves out every point and spot light of the scene. */
    private val noLocalLights: (Iterable<Entity>) -> Unit = { entities ->
        (lightsOf(entities, EntityType.LIGHT_POINT) + lightsOf(entities, EntityType.LIGHT_SPOT)).forEach {
            it.remove(LightComponent::class.java)
        }
    }

    /** The middle of the table-top (the table's origin plus the default top height of 0.958 m). */
    private val tableTop = Vector3(0f, 0.0766f + 0.958f, -1.2824f)

    @Test
    fun theLampsLightTheTableTopAndNoSunIsDrawn() = TestGl.run(600, 1280, 720) {
        var without = 0.0
        hangar(noLocalLights) { frame, renderer, field ->
            without = frame.meanAround(tableTop)
            assertTrue(!field.hasSun)
            assertEquals("a scene without a sun drew a sun shadow map", 0, renderer.sunShadows.renders)
        }
        hangar({ entities ->
            // lamps strong enough to show through the tone map: the bundled ones are tuned in task 7.3
            for (lamp in lightsOf(entities, EntityType.LIGHT_POINT)) {
                lamp.getComponent(LightComponent::class.java).light.intensity = 60f
            }
        }) { frame, renderer, _ ->
            val with = frame.meanAround(tableTop)
            assertTrue("the table top under the lamps is not brighter: $with vs $without", with > without + 2.0)
            assertEquals(0, renderer.sunShadows.renders)
        }
    }

    @Test
    fun aLightOutOfRangeLeavesTheTableAsItIs() = TestGl.run(600, 1280, 720) {
        var without: Frame? = null
        var pixels: Pixmap? = null
        hangar(noLocalLights) { frame, _, _ ->
            // keep a copy: the frame's pixmap is released after the block
            val copy = Pixmap(frame.pixmap.width, frame.pixmap.height, frame.pixmap.format)
            copy.blending = Pixmap.Blending.None
            copy.drawPixmap(frame.pixmap, 0, 0)
            pixels = copy
            without = Frame(copy, frame.project)
        }
        try {
            hangar({ entities ->
                noLocalLights(entities)
                // one strong lamp of range 2 m, 5 m above the table-top
                val lamp = lightsOf(entities, EntityType.LIGHT_POINT).first()
                lamp.add(
                    LightComponent(
                        LightDto(intensity = 60f, range = 2f)
                    )
                )
                lamp.getComponent(PositionComponent::class.java).localPosition.set(tableTop).add(0f, 5f, 0f)
            }) { frame, _, field ->
                assertEquals(1, field.localLights.size)
                val diff = frame.maxDiffAround(checkNotNull(without), tableTop, half = 20)
                assertTrue("a light out of range changed the table top by $diff", diff <= 1)
            }
        } finally {
            pixels?.dispose()
        }
    }

    @Test
    fun aSpotLightsItsConeOnly() = TestGl.run(600, 1280, 720) {
        val floor = 0.08f
        val inside = Vector3(-2.2f, floor, -0.9f)
        val outside = Vector3(2.2f, floor, -0.9f)
        var without: Frame? = null
        var pixels: Pixmap? = null
        hangar(noLocalLights) { frame, _, _ ->
            val copy = Pixmap(frame.pixmap.width, frame.pixmap.height, frame.pixmap.format)
            copy.blending = Pixmap.Blending.None
            copy.drawPixmap(frame.pixmap, 0, 0)
            pixels = copy
            without = Frame(copy, frame.project)
        }
        try {
            hangar({ entities ->
                noLocalLights(entities)
                // a 45 degree spot 3 m above the floor point [inside], pointing straight down
                val spot = lightsOf(entities, EntityType.LIGHT_SPOT).first()
                spot.add(
                    LightComponent(
                        LightDto(
                            intensity = 60f,
                            range = 10f,
                            coneAngle = 45f,
                            edgeSoftness = 0.2f,
                        )
                    )
                )
                val at = spot.getComponent(PositionComponent::class.java)
                at.localPosition.set(inside).add(0f, 3f, 0f)
                at.localRotation.set(Quaternion().setFromAxis(1f, 0f, 0f, -90f))
            }) { frame, _, field ->
                assertEquals(1, field.localLights.size)
                val base = checkNotNull(without)
                val lit = frame.meanAround(inside)
                val dark = base.meanAround(inside)
                assertTrue("the floor in the cone is not brighter: $lit vs $dark", lit > dark + 2.0)
                val diff = frame.maxDiffAround(base, outside)
                assertTrue("the floor well outside the cone changed by $diff", diff <= 1)
            }
        } finally {
            pixels?.dispose()
        }
    }
}
