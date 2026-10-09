/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.modelimport

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.assimp.UpAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImportTransformTest {
    private val fixtures = importFixtures()
    private val transform = ImportTransform()

    private fun open(name: String) = ModelSourceOpener().open(File(fixtures, name))

    @Test
    fun aCentimetreCrateIsOneMetreGroundedAndCentred() {
        open("crate.obj").use { source ->
            val result = transform.apply(source.data, ImportSettings("c", LengthUnit.CM, UpAxis.Y))
            val box = transform.restBounds(result.data)
            assertEquals(Vector3(1f, 1f, 1f), result.size)
            assertEquals(0f, box.min.y, 1e-5f)
            assertEquals(1f, box.max.y, 1e-5f)
            assertEquals(0f, box.getCenterX(), 1e-5f)
            assertEquals(0f, box.getCenterZ(), 1e-5f)
            assertEquals(IMPORT_ROOT, result.data.nodes.single().id)
            assertSame("the source roots are wrapped, not copied", source.data.nodes.first(), result.data.nodes.single().children.single())
            assertEquals("the source is not changed", 1, source.data.nodes.size)
        }
    }

    @Test
    fun zUpTurnsZIntoY() {
        open("box.3ds").use { source ->
            val result = transform.apply(source.data, ImportSettings("b", LengthUnit.CM, UpAxis.Z))
            // the box is 100 x 50 x 200 along the file's X, Y, Z
            assertEquals(1f, result.size.x, 1e-5f)
            assertEquals(2f, result.size.y, 1e-5f)
            assertEquals(0.5f, result.size.z, 1e-5f)
            val box = transform.restBounds(result.data)
            assertEquals(0f, box.min.y, 1e-5f)
        }
    }

    @Test
    fun aFitToHeightGivesThatHeight() {
        open("rig.fbx").use { source ->
            val result = transform.apply(source.data, ImportSettings("r", LengthUnit.CM, UpAxis.Z, FitSize.Height(1.8)))
            val box = transform.restBounds(result.data)
            assertEquals(1.8f, box.height, 1e-5f)
            assertEquals(0f, box.min.y, 1e-5f)
            assertEquals(1.8f, result.size.y, 1e-5f)
        }
        open("crate.obj").use { source ->
            val result = transform.apply(source.data, ImportSettings("c", LengthUnit.M, UpAxis.Y, FitSize.LargestExtent(3.0)))
            assertEquals(Vector3(3f, 3f, 3f).toString(), result.size.toString())
        }
    }

    /** The import root moves the joints with the mesh, so a posed character is the source's pose, transformed. */
    @Test
    fun aPosedRigMatchesTheSourceAfterTheTransform() {
        open("rig.fbx").use { source ->
            val result = transform.apply(source.data, ImportSettings("r", LengthUnit.CM, UpAxis.Z))
            for ((animation, time) in listOf(null to 0f, "Run" to 0f, "Run" to 0.5f, "Idle" to 1.3f)) {
                val before = posedPositions(source.data, animation, time).map { Vector3(it).mul(result.root) }
                val after = posedPositions(result.data, animation, time)
                assertEquals(before.size, after.size)
                for (i in before.indices) {
                    assertTrue("$animation@$time vertex $i: ${before[i]} != ${after[i]}", before[i].epsilonEquals(after[i], 1e-4f))
                }
            }
            // mid-Run the spine bends: the top of the bar is no longer straight above the hip
            val top = posedPositions(result.data, "Run", 0.5f).maxBy { it.y }
            val rest = posedPositions(result.data, null, 0f).maxBy { it.y }
            assertTrue("$top vs $rest", !top.epsilonEquals(rest, 1e-3f))
        }
    }
}
