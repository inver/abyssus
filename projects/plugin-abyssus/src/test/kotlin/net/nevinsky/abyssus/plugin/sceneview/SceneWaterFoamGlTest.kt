/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.water.WaterSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage

/**
 * Shoreline foam: where terrain meets shallow water it follows the level and the width and amount settings; it never
 * appears over deep water, at models or at the surface's own edge. Opt-in GL checks.
 */
class SceneWaterFoamGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private val orbit = OrbitCamera(WaterGlScenes.BOWL_CENTRE, 520f, 0.0f, 0.5f)

    private fun shoot(params: SceneRenderParams, orbit: OrbitCamera = this.orbit) = WaterGlRig.shoot(params, orbit, size = 800 to 500)

    /** A dark, murky lake: the floor hardly shows, so foam is the only bright thing in the water. */
    private fun dark(level: Double = 0.0, edit: (WaterSettings) -> WaterSettings = { it }) = WaterGlScenes.bowlLake(level) {
        edit(it.copy(tintR = 0.02, tintG = 0.08, tintB = 0.12, clarity = 2.0, waveAmplitude = 0.02))
    }

    /** A wider, higher view for the checks that look out to the level-dependent shoreline. */
    private val wide = OrbitCamera(WaterGlScenes.BOWL_CENTRE, 900f, 0.0f, 0.6f)

    private fun lateral(radii: List<Float>, from: OrbitCamera = orbit): List<Vector3> {
        val eye = from.position()
        val centre = WaterGlScenes.BOWL_CENTRE
        val dx = centre.x - eye.x
        val dz = centre.z - eye.z
        val length = Math.hypot(dx.toDouble(), dz.toDouble()).toFloat()
        return radii.map { Vector3(centre.x - dz / length * it, 0f, centre.z + dx / length * it) }
    }

    private fun pixel(shot: WaterShot, world: Vector3): Pair<Int, Int> {
        val screen = Vector3(world)
        shot.renderer.frameCamera.project(screen, 0f, 0f, shot.image.width.toFloat(), shot.image.height.toFloat())
        return screen.x.toInt() to (shot.image.height - screen.y).toInt()
    }

    private fun luminance(shot: WaterShot, world: Vector3): Double {
        val (x, y) = pixel(shot, world)
        return WaterGlRig.luminance(WaterGlRig.rgb(shot.image, x.coerceIn(0, shot.image.width - 1), y.coerceIn(0, shot.image.height - 1)))
    }

    private fun waterMask(shot: WaterShot, land: WaterShot): Array<BooleanArray> =
        Array(shot.image.height) { y -> BooleanArray(shot.image.width) { x -> shot.image.getRGB(x, y) != land.image.getRGB(x, y) } }

    private fun identical(a: BufferedImage, b: BufferedImage): Boolean {
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        return true
    }

    @Test fun foamBrightensTheShallowBandAndLeavesDeepWaterAlone() {
        val with = shoot(WaterGlScenes.bowlScene(dark { it.copy(foamAmount = 1.0, foamWidth = 1.5) }))
        val without = shoot(WaterGlScenes.bowlScene(dark { it.copy(foamAmount = 0.0) }))
        val shore = lateral(listOf(525f, 535f, 545f, 552f))
        val deep = lateral(listOf(0f, 150f, 300f))
        val shoreGain = shore.map { luminance(with, it) - luminance(without, it) }.average()
        val deepGain = deep.map { luminance(with, it) - luminance(without, it) }.map { kotlin.math.abs(it) }.average()
        assertTrue("foam at the shore ($shoreGain)", shoreGain > 0.15)
        assertTrue("no foam in the deep middle ($deepGain)", deepGain < 0.01)
    }

    @Test fun aZeroAmountDisablesFoamWhateverItsWidth() {
        val narrow = shoot(WaterGlScenes.bowlScene(dark { it.copy(foamAmount = 0.0, foamWidth = 0.2) }))
        val wide = shoot(WaterGlScenes.bowlScene(dark { it.copy(foamAmount = 0.0, foamWidth = 5.0) }))
        assertTrue("with no amount the width changes nothing", identical(narrow.image, wide.image))
    }

    @Test fun aWiderBandAndALargerAmountCoverMoreOfTheShallows() {
        val land = shoot(WaterGlScenes.bowlScene())
        fun brightWater(edit: (WaterSettings) -> WaterSettings): Int {
            val shot = shoot(WaterGlScenes.bowlScene(dark(edit = edit)))
            val mask = waterMask(shot, land)
            var count = 0
            for (y in 0 until shot.image.height) for (x in 0 until shot.image.width) {
                if (mask[y][x] && WaterGlRig.luminance(WaterGlRig.rgb(shot.image, x, y)) > 0.5) count++
            }
            return count
        }
        val thin = brightWater { it.copy(foamAmount = 1.0, foamWidth = 0.3) }
        val thick = brightWater { it.copy(foamAmount = 1.0, foamWidth = 3.0) }
        val faint = brightWater { it.copy(foamAmount = 0.15, foamWidth = 3.0) }
        assertTrue("a wider band covers more ($thin vs $thick)", thick > thin * 1.5)
        assertTrue("a larger amount covers more ($faint vs $thick)", thick > faint * 1.5)
    }

    @Test fun theFoamBandFollowsTheLevelUpTheShore() {
        val land = shoot(WaterGlScenes.bowlScene(far = 3000f), wide)
        fun ringRadius(level: Double): Float {
            val shot = shoot(WaterGlScenes.bowlScene(dark(level) { it.copy(foamAmount = 1.0, foamWidth = 1.2) }, far = 3000f), wide)
            val mask = waterMask(shot, land)
            val radii = (400..760 step 5).map { it.toFloat() }
            var best = radii.first()
            var brightest = -1.0
            for ((i, point) in lateral(radii, wide).withIndex()) {
                val (x, y) = pixel(shot, point)
                if (x !in 0 until shot.image.width || y !in 0 until shot.image.height || !mask[y][x]) continue
                val l = luminance(shot, point)
                if (l > brightest) { brightest = l; best = radii[i] }
            }
            return best
        }
        val low = ringRadius(0.0)
        val high = ringRadius(1.0)
        // the shore of a level L is at 800 * sqrt((L + 3) / 6): 565 at 0 and 653 at 1
        assertTrue("the foam moved outward with the level ($low -> $high)", high > low + 50)
        assertEquals("the foam sits at the shore of its level", 565f, low, 30f)
        assertEquals("and of the raised level", 653f, high, 30f)
    }

    @Test fun deepWaterModelsAndTheSurfaceEdgeMakeNoFoam() {
        // a flat floor two units under the lake: models stand in it, and its edges end over open floor
        fun scene(foam: Double) = WaterGlScenes.flatLake(listOf(WaterGlScenes.lake().copy(foamAmount = foam, foamWidth = 1.5)))
        val with = WaterGlRig.shoot(scene(1.0), OrbitCamera(Vector3(0f, 0f, 0f), 25f, 0.6f, 0.35f))
        val without = WaterGlRig.shoot(scene(0.0), OrbitCamera(Vector3(0f, 0f, 0f), 25f, 0.6f, 0.35f))
        assertTrue("no foam anywhere over a deep floor, round models or at the edge", identical(with.image, without.image))
        // the edge itself is on screen: a lake that fits inside the view
        val small = WaterGlRig.shoot(
            WaterGlScenes.flatLake(listOf(WaterGlScenes.lake().copy(width = 20.0, length = 20.0, foamAmount = 1.0, foamWidth = 1.5))),
            OrbitCamera(Vector3(0f, 0f, 0f), 40f, 0.6f, 0.6f),
        )
        val smallOff = WaterGlRig.shoot(
            WaterGlScenes.flatLake(listOf(WaterGlScenes.lake().copy(width = 20.0, length = 20.0, foamAmount = 0.0, foamWidth = 1.5))),
            OrbitCamera(Vector3(0f, 0f, 0f), 40f, 0.6f, 0.6f),
        )
        assertTrue("an extent edge over empty floor shows no foam", identical(small.image, smallOff.image))
    }

    @Test fun dryLandAboveTheLevelNeverShowsFoam() {
        val land = shoot(WaterGlScenes.bowlScene())
        val with = shoot(WaterGlScenes.bowlScene(dark { it.copy(foamAmount = 1.0, foamWidth = 3.0) }))
        val mask = waterMask(with, land)
        // beyond the shoreline (radius above 600) the picture is the terrain's own
        var changedLand = 0
        for ((_, point) in lateral(listOf(620f, 640f, 660f, 700f)).withIndex()) {
            val (x, y) = pixel(with, point)
            if (x in 0 until with.image.width && y in 0 until with.image.height && with.image.getRGB(x, y) != land.image.getRGB(x, y)) changedLand++
        }
        assertEquals("land above the water is untouched", 0, changedLand)
        assertTrue("while some water pixels do differ from the land", mask.sumOf { row -> row.count { it } } > 5000)
    }
}
