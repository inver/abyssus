/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScenePickerTest {
    private fun box(id: String, x: Float, y: Float, z: Float, half: Float = 1f) =
        BoxTarget(id, BoundingBox(Vector3(x - half, y - half, z - half), Vector3(x + half, y + half, z + half)))

    private fun ray(from: Vector3, to: Vector3) = Ray(from, Vector3(to).sub(from).nor())

    private val flat = TerrainData(3, FloatArray(9), 100, 1f)

    private fun oriented(x: Float = 0f, bottom: Float = 5f, z: Float = 0f, half: Float = 1f,
                         world: Matrix4 = Matrix4()) = OrientedBox(
        BoundingBox(Vector3(x - half, bottom, z - half), Vector3(x + half, bottom + 2f, z + half)), world)

    @Test fun aRegeneratedTerrainIsPickedAtItsNewHeightsAndNotTheOldOnes() {
        val down = ray(Vector3(50f, 20f, 50f), Vector3(50f, 12f, 50f))
        val old = TerrainTarget("t", flat, Matrix4())
        val regenerated = TerrainTarget("t", TerrainData(3, FloatArray(9) { 10f }, 100, 1f), Matrix4())
        assertEquals(20f, ScenePicker().terrainDistance(down, old, 30f)!!, 1e-3f)
        assertEquals(10f, ScenePicker().terrainDistance(down, regenerated, 30f)!!, 1e-3f)
        assertEquals("t", ScenePicker().pick(down, emptyList(), listOf(regenerated), 30f))
    }

    @Test fun anIdentityMatrixLeavesTheCornersUnmoved() {
        val b = oriented()
        assertEquals(8, b.corners.size)
        assertEquals(setOf(-1f, 1f), b.corners.map { it.x }.toSet())
        assertEquals(setOf(5f, 7f), b.corners.map { it.y }.toSet())
    }

    @Test fun rotatingTheBoxRotatesItsCorners() {
        val b = oriented(world = Matrix4().rotate(Vector3.Y, 90f))
        assertEquals(-1f, b.corners[0].x, 1e-5f)
        assertEquals(1f, b.corners[0].z, 1e-5f)
    }

    @Test fun bottomAndTopUseAllEightCorners() {
        val b = oriented(bottom = 0f, world = Matrix4().rotate(Vector3.X, 45f))
        assertEquals(-0.7071068f, b.bottom, 1e-5f)
        assertEquals(2.1213203f, b.top, 1e-5f)
    }

    @Test fun overlappingHullsOverlap() {
        org.junit.Assert.assertTrue(oriented().overlaps(oriented(x = 1f)))
        org.junit.Assert.assertFalse(oriented().overlaps(oriented(x = 5f)))
    }

    @Test fun aRotatedBarDoesNotOverlapABoxOnlyItsAxisAlignedBoundsReach() {
        val bar = OrientedBox(BoundingBox(Vector3(-5f, 0f, -0.1f), Vector3(5f, 1f, 0.1f)), Matrix4().rotate(Vector3.Y, 45f))
        org.junit.Assert.assertFalse(bar.overlaps(oriented(x = 3f, z = 3f, half = 0.2f)))
        org.junit.Assert.assertTrue(bar.overlaps(oriented(x = 3f, z = -3f, half = 0.2f)))
    }

    private fun rest(footprint: OrientedBox = oriented(), vararg boxes: OrientedBox) =
        ScenePicker().restHeight(footprint, boxes.toList(), emptyList())

    @Test fun restsOnTheHighestBoxBelow() { assertEquals(4f, rest(oriented(), oriented(bottom = 0f), oriented(bottom = 2f))!!, 0f) }
    @Test fun aBoxWhollyAboveIsIgnored() { assertNull(rest(oriented(), oriented(bottom = 8f))) }
    @Test fun aSunkObjectRisesOntoTheBox() { assertEquals(6f, rest(oriented(), oriented(bottom = 4f))!!, 0f) }
    @Test fun aWideFootprintOverANarrowBoxRestsOnIt() { assertEquals(2f, rest(oriented(half = 5f), oriented(bottom = 0f, half = 0.2f))!!, 0f) }
    @Test fun noSurfaceBelowReturnsNull() { assertNull(rest(oriented(), oriented(x = 10f, bottom = 0f))) }

    private fun terrainRest(footprint: OrientedBox = oriented(x = 2f, z = 2f),
                            data: TerrainData = TerrainData(3, FloatArray(9) { 3f }, 4, 1f),
                            world: Matrix4 = Matrix4(), boxes: List<OrientedBox> = emptyList()) =
        ScenePicker().restHeight(footprint, boxes, listOf(TerrainTarget("t", data, world)))

    @Test fun restsOnTheTerrainHeightUnderTheFootprint() { assertEquals(3f, terrainRest()!!, 1e-5f) }
    @Test fun restsOnTheHigherOfTerrainAndBox() { assertEquals(4f, terrainRest(boxes = listOf(oriented(x = 2f, bottom = 2f, z = 2f)))!!, 1e-5f) }
    @Test fun findsABumpBetweenTheCorners() {
        val heights = FloatArray(25).also { it[12] = 10f }
        assertEquals(10f, terrainRest(oriented(x = 2.1f, z = 2.1f, half = 1.4f), TerrainData(5, heights, 4, 1f))!!, 1e-5f)
    }
    @Test fun staysExactUnderANonUniformTerrainScale() {
        val world = Matrix4().setToTranslation(10f, 7f, 20f).scale(2f, 3f, 4f)
        assertEquals(16f, terrainRest(oriented(x = 14f, z = 28f), world = world)!!, 1e-5f)
    }
    @Test fun aFootprintOffTheTerrainGetsNoTerrainHeight() { assertNull(terrainRest(oriented(x = 20f, z = 20f))) }
    @Test fun anObjectUnderTheTerrainRisesToItsSurface() { assertEquals(3f, terrainRest(oriented(x = 2f, bottom = -10f, z = 2f))!!, 1e-5f) }
    @Test fun aTiltedTerrainUsesTheActualWorldColumn() {
        // Rotation shifts positive local heights toward negative world Z. The plane is y = z + 2*sqrt(2).
        val data = TerrainData(2, FloatArray(4) { 2f }, 10, 1f)
        val world = Matrix4().rotate(Vector3.X, -45f)
        assertEquals(2f + 2f * kotlin.math.sqrt(2f), terrainRest(oriented(x = 3f, z = 1f), data, world)!!, 1e-5f)
    }
    @Test fun aClippedBilinearCellIncludesItsBoundaryMaximum() {
        // h = x*z; the diagonal footprint edge x+z=1 has an interior maximum of 0.25.
        val footprint = OrientedBox(BoundingBox(Vector3(-1f, 5f, -1f), Vector3(1f, 7f, 1f)),
            Matrix4().rotate(Vector3.Y, 45f).scale(1f / kotlin.math.sqrt(2f), 1f, 1f / kotlin.math.sqrt(2f)))
        assertEquals(0.25f, terrainRest(footprint, TerrainData(2, floatArrayOf(0f, 0f, 0f, 1f), 1, 1f))!!, 1e-5f)
    }
    @Test fun aSingularTerrainOffersNoSurface() { assertNull(terrainRest(world = Matrix4().scale(1f, 0f, 1f))) }

    @Test fun fixtureTerrainAndCameraAreValidForDrop() {
        val dir = java.io.File("src/test/testData/project/Untitled")
        val content = sceneContentOf(net.nevinsky.abyssus.lib.core.editor.parseScene(java.io.File(dir, "scenes/Main Scene.scene").readText()))
        val placement = content.terrains.single()
        val data = net.nevinsky.abyssus.lib.core.editor.terrainData(dir, placement.assetName)
        org.junit.Assert.assertTrue(data.heights.all { it == 0f })
        val camera = content.cameras.single()
        val ground = ScenePicker().restHeight(OrientedBox(SceneMarkers().cameraBounds(camera.position), Matrix4()), emptyList(), listOf(TerrainTarget("1", data, placement.transform.toMatrix())))!!
        println("Camera 4: terrain=$ground lowest=${camera.position.y - 0.5f}")
        org.junit.Assert.assertTrue(kotlin.math.abs(camera.position.y - 0.5f - ground) > REST_EPS)
        val root = net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parse(java.io.File(dir, "scenes/Main Scene.scene").readText())
        val components = root["ecs"]["4"]["components"]
        assertEquals(components["PositionComponent"]["localPosition"], components["CameraComponent"]["camera"]["position"])
    }

    @Test fun aRestHeightWithinEpsAboveIsResting() {
        org.junit.Assert.assertTrue(ScenePicker().isResting(5f, rest(oriented(), oriented(bottom = 3.00005f))!!))
    }
    @Test fun aRestHeightWithinEpsBelowIsResting() {
        org.junit.Assert.assertTrue(ScenePicker().isResting(5f, rest(oriented(), oriented(bottom = 2.99995f))!!))
    }
    @Test fun aSecondDropAfterARotatedFirstDropIsANoOp() {
        val local = BoundingBox(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))
        val world = Matrix4().setToTranslation(0f, 10f, 0f).rotate(Vector3.X, 37f).rotate(Vector3.Y, 29f)
        val first = OrientedBox(local, world)
        val surface = oriented(bottom = -2f)
        val height = rest(first, surface)!!
        world.`val`[Matrix4.M13] += height - first.bottom
        val settled = OrientedBox(local, world)
        org.junit.Assert.assertTrue(ScenePicker().isResting(settled.bottom, rest(settled, surface)!!))
    }

    @Test
    fun hitsTheBoxUnderTheRay() {
        val r = ray(Vector3(0f, 0f, 10f), Vector3(0f, 0f, 0f))
        assertEquals("a", ScenePicker().pick(r, listOf(box("a", 0f, 0f, 0f), box("b", 5f, 0f, 0f)), emptyList(), 100f))
    }

    @Test
    fun missesWhenNothingIsUnderTheRay() {
        val r = ray(Vector3(0f, 0f, 10f), Vector3(20f, 0f, 0f))
        assertNull(ScenePicker().pick(r, listOf(box("a", 0f, 0f, 0f)), emptyList(), 100f))
    }

    @Test
    fun theNearestBoxWins() {
        val r = ray(Vector3(0f, 0f, 20f), Vector3(0f, 0f, 0f))
        val boxes = listOf(box("far", 0f, 0f, -5f), box("near", 0f, 0f, 5f))
        assertEquals("near", ScenePicker().pick(r, boxes, emptyList(), 100f))
    }

    @Test
    fun terrainIsHitWhereNoModelIsInFront() {
        val t = TerrainTarget("ground", flat, Matrix4())
        val r = ray(Vector3(50f, 10f, 50f), Vector3(50f, 0f, 60f))
        assertEquals("ground", ScenePicker().pick(r, listOf(box("m", 80f, 1f, 80f)), listOf(t), 1000f))
    }

    @Test
    fun aModelInFrontOfTheTerrainWins() {
        val t = TerrainTarget("ground", flat, Matrix4())
        val r = ray(Vector3(50f, 10f, 50f), Vector3(50f, 0f, 50f))
        assertEquals("m", ScenePicker().pick(r, listOf(box("m", 50f, 5f, 50f)), listOf(t), 1000f))
    }

    @Test
    fun terrainBehindTheModelLosesAndTerrainBeyondTheRangeIsIgnored() {
        val t = TerrainTarget("ground", flat, Matrix4())
        val r = ray(Vector3(50f, 10f, 50f), Vector3(50f, 0f, 50f))
        assertNull(ScenePicker().pick(r, emptyList(), listOf(t), 5f))
        assertNotNull(ScenePicker().pick(r, emptyList(), listOf(t), 50f))
    }

    @Test
    fun rayAboveOrOutsideTheTerrainMisses() {
        val t = TerrainTarget("ground", flat, Matrix4())
        assertNull(ScenePicker().pick(ray(Vector3(50f, 10f, 50f), Vector3(50f, 20f, 60f)), emptyList(), listOf(t), 1000f))
        assertNull(ScenePicker().pick(ray(Vector3(500f, 10f, 500f), Vector3(500f, 0f, 510f)), emptyList(), listOf(t), 1000f))
    }

    @Test
    fun terrainTransformIsHonoured() {
        val moved = TerrainTarget("ground", flat, Matrix4().setToTranslation(1000f, 0f, 0f))
        assertNull(ScenePicker().pick(ray(Vector3(50f, 10f, 50f), Vector3(50f, 0f, 60f)), emptyList(), listOf(moved), 5000f))
        assertEquals("ground", ScenePicker().pick(ray(Vector3(1050f, 10f, 50f), Vector3(1050f, 0f, 60f)), emptyList(), listOf(moved), 5000f))
    }

    @Test
    fun hillsAreHitAtTheirSurface() {
        // a ridge of height 10 along the middle: a ray coming in low hits the ridge side, not the floor behind it
        val heights = floatArrayOf(0f, 10f, 0f, 0f, 10f, 0f, 0f, 10f, 0f)
        val t = TerrainTarget("hill", TerrainData(3, heights, 100, 1f), Matrix4())
        val r = ray(Vector3(-20f, 5f, 50f), Vector3(40f, 5f, 50f))
        assertEquals("hill", ScenePicker().pick(r, emptyList(), listOf(t), 1000f))
        val d = ScenePicker().terrainDistance(r, t, 1000f)!!
        // the surface at height 5 is x = 25 (first slope reaches 10 at x = 50), so the hit is 45 away
        assertEquals(45f, d, 1.5f)
    }
}

class PickRayTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private fun camera() = com.badlogic.gdx.graphics.PerspectiveCamera(67f, 800f, 600f).apply {
        position.set(0f, 0f, 10f)
        lookAt(0f, 0f, 0f)
        near = 0.1f
        far = 100f
        update()
    }

    @Test
    fun worksWithoutAnyGdxGlobals() {
        // clicks arrive outside GdxRuntime.withContext, where Gdx.graphics is null
        org.junit.Assert.assertNull(com.badlogic.gdx.Gdx.graphics)
        val ray = ScenePicker().pickRay(camera(), 400, 300, 800, 600)
        assertEquals(0f, ray.direction.x, 1e-4f)
        assertEquals(0f, ray.direction.y, 1e-4f)
        assertEquals(-1f, ray.direction.z, 1e-4f)
    }

    @Test
    fun topLeftPixelLooksUpAndLeft() {
        val ray = ScenePicker().pickRay(camera(), 0, 0, 800, 600)
        org.junit.Assert.assertTrue(ray.direction.x < 0f)
        org.junit.Assert.assertTrue(ray.direction.y > 0f)
    }

    // a regenerated terrain: the same entity with new heights is what picking and resting use

    @Test fun pickingUsesTheNewHeightsOfAReplacedTerrain() {
        val low = TerrainData(3, FloatArray(9) { 1f }, 100, 1f)
        val high = TerrainData(3, FloatArray(9) { 40f }, 100, 1f)
        // a ray from above at (50, 50) towards (50, 20, 50): it passes the low surface (y 1) but is blocked by the high one (y 40)
        val ray = Ray(Vector3(50f, 100f, 50f), Vector3(0f, -1f, 0f))
        val beforeHit = ScenePicker().terrainDistance(ray, TerrainTarget("t", low, Matrix4()), 1000f)!!
        val afterHit = ScenePicker().terrainDistance(ray, TerrainTarget("t", high, Matrix4()), 1000f)!!
        assertEquals(99f, beforeHit, 0.05f)
        assertEquals(60f, afterHit, 0.05f)
        assertEquals("t", ScenePicker().pick(ray, emptyList(), listOf(TerrainTarget("t", high, Matrix4())), 1000f))
        // a ray that only reaches y 20 hits the new surface and missed the old one
        val short = Ray(Vector3(50f, 60f, 50f), Vector3(0f, -1f, 0f))
        assertEquals("t", ScenePicker().pick(short, emptyList(), listOf(TerrainTarget("t", high, Matrix4())), 30f))
        assertNull(ScenePicker().pick(short, emptyList(), listOf(TerrainTarget("t", low, Matrix4())), 30f))
    }

    @Test fun restingFollowsTheNewHeightsOfAReplacedTerrain() {
        val footprint = OrientedBox(BoundingBox(Vector3(1f, 5f, 1f), Vector3(3f, 7f, 3f)), Matrix4())
        val before = ScenePicker().restHeight(footprint, emptyList(), listOf(TerrainTarget("t", TerrainData(3, FloatArray(9) { 3f }, 4, 1f), Matrix4())))!!
        val after = ScenePicker().restHeight(footprint, emptyList(), listOf(TerrainTarget("t", TerrainData(3, FloatArray(9) { 8f }, 4, 1f), Matrix4())))!!
        assertEquals(3f, before, 1e-5f)
        assertEquals(8f, after, 1e-5f)
    }
}
