/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.GdxNativesLoader
import java.io.File
import java.nio.file.Files
import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.editor.modelmesh.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class PartPickerTest {
    private val dirs = mutableListOf<File>()

    @After fun cleanUp() = dirs.forEach(File::deleteRecursively)

    /** A part "p" with one unit triangle in the XY plane at z = 0 (corners (0,0), (1,0), (0,1)). */
    private fun quad(name: String = "p", rest: Matrix4 = Matrix4()): ModelPartsInfo {
        val triangles = PartTriangles(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f), intArrayOf(0, 1, 2))
        val part = ModelPart(name, emptyList(), BoundingBox(Vector3(0f, 0f, -0.01f), Vector3(1f, 1f, 0.01f)), NO_PIVOT)
        return ModelPartsInfo(listOf(part), mapOf(name to rest), mapOf(name to rest), mapOf(name to triangles))
    }

    private fun along(z: Float, x: Float = 0.2f, y: Float = 0.2f) = Ray(Vector3(x, y, z), Vector3(0f, 0f, -1f))

    @Test
    fun aRayThroughATriangleHitsItsPart() {
        val hit = checkNotNull(PartPicker().pick(along(5f), listOf(PartTarget(1, Matrix4(), quad()))))
        assertEquals(1L, hit.entityId)
        assertEquals("p", hit.node)
        assertEquals(5f, hit.distance, 1e-5f)
        assertEquals(0f, hit.point.z, 1e-5f)
    }

    @Test
    fun aRayThroughTheGapOfTheBoxMisses() {
        // inside the box (x,y in 0..1) but outside the triangle (x + y > 1)
        assertNull(PartPicker().pick(along(5f, 0.9f, 0.9f), listOf(PartTarget(1, Matrix4(), quad()))))
    }

    @Test
    fun theEntityWorldMatrixMovesTheHit() {
        val world = Matrix4().setTranslation(10f, 0f, 0f)
        assertNull(PartPicker().pick(along(5f), listOf(PartTarget(1, world, quad()))))
        assertNotNull(PartPicker().pick(along(5f, 10.2f, 0.2f), listOf(PartTarget(1, world, quad()))))
    }

    @Test
    fun aDisabledEntityIsSkipped() {
        assertNull(PartPicker().pick(along(5f), listOf(PartTarget(1, Matrix4(), quad(), enabled = false))))
    }

    @Test
    fun theNearestOfTwoHitsWins() {
        val near = PartTarget(1, Matrix4().setTranslation(0f, 0f, 2f), quad("near"))
        val far = PartTarget(2, Matrix4(), quad("far"))
        assertEquals("near", PartPicker().pick(along(5f), listOf(far, near))!!.node)
    }

    @Test
    fun aRayThroughTheTrainersLeftFlapHitsTheFlap() {
        GdxNativesLoader.load()
        val root = Files.createTempDirectory("part-picker").toFile().also(dirs::add)
        assertTrue(File("../app-game-control-line/project/ControlLine/assets/model_trainer").copyRecursively(File(root, "assets/trainer")))
        val files = testFileLoader(root)
        val info = checkNotNull(ModelMeshLister(files, testMetaLoader(root, fileLoader = files)).parts("trainer"))
        val flap = checkNotNull(info.find("LeftFlap")?.bounds)
        val centre = flap.getCenter(Vector3()).mul(info.rest.getValue("LeftFlap"))
        // Along the line through the middle of the flap's geometry, from several sides; at least one lands on the flap.
        val hits = listOf(Vector3(0f, 1f, 0f), Vector3(0f, -1f, 0f)).mapNotNull { direction ->
            val ray = Ray(Vector3(centre).mulAdd(direction, -5f), Vector3(direction))
            PartPicker().pick(ray, listOf(PartTarget(7, Matrix4(), info)))
        }
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { it.node == "LeftFlap" })
    }
}
