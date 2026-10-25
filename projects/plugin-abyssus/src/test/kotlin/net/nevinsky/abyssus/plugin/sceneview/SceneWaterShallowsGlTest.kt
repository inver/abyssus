/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Depth-dependent absorption and refraction over a bowl-shaped terrain: the floor shows near the shore and fades into
 * the tint toward the deep middle, clarity changes the fade but not the shoreline, and empty depth is deep water.
 * Opt-in GL checks.
 */
class SceneWaterShallowsGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private val orbit = OrbitCamera(WaterGlScenes.BOWL_CENTRE, 520f, 0.0f, 0.5f)

    private fun shoot(params: SceneRenderParams, orbit: OrbitCamera = this.orbit) = WaterGlRig.shoot(params, orbit, size = 800 to 500)

    /** Points on the water level at distances [radii] from the bowl's centre, sideways across the view so all are about as far from the eye. */
    private fun lateral(radii: List<Float>): List<Vector3> {
        val eye = orbit.position()
        val centre = WaterGlScenes.BOWL_CENTRE
        val dx = centre.x - eye.x
        val dz = centre.z - eye.z
        val length = Math.hypot(dx.toDouble(), dz.toDouble()).toFloat()
        val px = -dz / length
        val pz = dx / length
        return radii.map { Vector3(centre.x + px * it, 0f, centre.z + pz * it) }
    }

    private fun colorAt(shot: WaterShot, world: Vector3): Triple<Double, Double, Double> {
        val screen = Vector3(world)
        shot.renderer.frameCamera.project(screen, 0f, 0f, shot.image.width.toFloat(), shot.image.height.toFloat())
        val x = screen.x.toInt()
        val y = (shot.image.height - screen.y).toInt()
        var r = 0.0; var g = 0.0; var b = 0.0; var n = 0
        for (dy in -2..2) for (dx in -2..2) {
            val (pr, pg, pb) = WaterGlRig.rgb(shot.image, (x + dx).coerceIn(0, shot.image.width - 1), (y + dy).coerceIn(0, shot.image.height - 1))
            r += pr; g += pg; b += pb; n++
        }
        return Triple(r / n, g / n, b / n)
    }

    @Test fun theFloorShowsNearTheShoreAndFadesIntoTheTintTowardTheDeepMiddle() {
        val shot = shoot(WaterGlScenes.bowlScene(WaterGlScenes.bowlLake()))
        val points = lateral(listOf(0f, 250f, 450f, 540f))
        val red = points.map { colorAt(shot, it).first }
        // the floor is the brightest thing under the water and red is absorbed first: red rises toward the shore
        assertTrue("red by distance from the middle: $red", red[0] < red[1] && red[1] < red[2] && red[2] < red[3])
        assertTrue("the shallows show clearly more floor than the deep middle ($red)", red[3] - red[0] > 40)
    }

    @Test fun greaterClarityShowsTheFloorDeeperButLeavesTheShorelineAlone() {
        val murky = shoot(WaterGlScenes.bowlScene(WaterGlScenes.bowlLake { it.copy(clarity = 4.0) }))
        val clear = shoot(WaterGlScenes.bowlScene(WaterGlScenes.bowlLake { it.copy(clarity = 60.0) }))
        val land = shoot(WaterGlScenes.bowlScene())
        val middle = lateral(listOf(0f))[0]
        val murkyRed = colorAt(murky, middle).first
        val clearRed = colorAt(clear, middle).first
        assertTrue("clearer water shows more of the deep floor ($murkyRed vs $clearRed)", clearRed > murkyRed + 25)
        fun waterPixels(shot: WaterShot): Int {
            var count = 0
            for (y in 0 until shot.image.height) for (x in 0 until shot.image.width) if (shot.image.getRGB(x, y) != land.image.getRGB(x, y)) count++
            return count
        }
        val a = waterPixels(murky)
        val b = waterPixels(clear)
        assertTrue("the shoreline does not move with clarity ($a vs $b water pixels)", kotlin.math.abs(a - b) < a / 100)
    }

    @Test fun waterOverEmptyDepthIsDeepWaterWhateverTheClarity() {
        val far = Vector3(8000f, 0f, 8000f)
        fun over(clarity: Double): WaterShot {
            val lake = WaterGlScenes.bowlLake { it.copy(x = far.x.toDouble(), z = far.z.toDouble(), clarity = clarity, waveAmplitude = 0.0, foamAmount = 0.0) }
            return WaterGlRig.shoot(WaterGlScenes.bowlScene(lake), OrbitCamera(far, 120f, 0f, 0.8f), size = 400 to 300)
        }
        val short = over(2.0)
        val long = over(200.0)
        val a = WaterGlRig.rgb(short.image, 200, 150)
        val b = WaterGlRig.rgb(long.image, 200, 150)
        assertTrue("nothing is behind the water: its colour does not depend on clarity ($a vs $b)",
            kotlin.math.abs(a.first - b.first) + kotlin.math.abs(a.second - b.second) + kotlin.math.abs(a.third - b.third) <= 6)
        assertTrue("and it is the tint, not the floor", a.first < 80)
    }

    @Test fun theActiveCamerasFarPlaneIsUsedForTheDepths() {
        // points about 520 to 600 units from the eye, so the floor behind them is inside both far planes
        val points = lateral(listOf(0f, 150f, 300f))
        val wide = shoot(WaterGlScenes.bowlScene(WaterGlScenes.bowlLake(), far = 1000f))
        val narrow = shoot(WaterGlScenes.bowlScene(WaterGlScenes.bowlLake(), far = 820f))
        for (p in points) {
            val a = colorAt(wide, p)
            val b = colorAt(narrow, p)
            assertEquals("red at $p", a.first, b.first, 6.0)
            assertEquals("green at $p", a.second, b.second, 6.0)
            assertEquals("blue at $p", a.third, b.third, 6.0)
        }
    }

    @Test fun refractionNearDryLandDoesNotPullTheShoreIntoTheWater() {
        val land = shoot(WaterGlScenes.bowlScene())
        fun whiteWater(amplitude: Double): Int {
            val shot = shoot(WaterGlScenes.bowlScene(WaterGlScenes.bowlLake { it.copy(waveAmplitude = amplitude, foamAmount = 0.0, tintR = 0.02, tintG = 0.15, tintB = 0.2) }))
            var count = 0
            for (y in 0 until shot.image.height) for (x in 0 until shot.image.width) {
                if (shot.image.getRGB(x, y) == land.image.getRGB(x, y)) continue
                val (r, g, b) = WaterGlRig.rgb(shot.image, x, y)
                if (r >= 245 && g >= 245 && b >= 245) count++
            }
            return count
        }
        val calm = whiteWater(0.0)
        val rough = whiteWater(0.6)
        // dry land is overexposed white; refraction that smeared it into the water would add white pixels
        assertTrue("choppy water gains no white from smeared land ($calm vs $rough)", rough <= calm + 150)
    }
}
