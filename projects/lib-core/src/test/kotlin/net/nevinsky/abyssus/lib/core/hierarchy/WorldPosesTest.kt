/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.hierarchy

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldPosesTest {
    private val poses = WorldPoses()

    private fun at(x: Float, y: Float, z: Float, rotation: Quaternion = Quaternion(), scale: Vector3 = Vector3(1f, 1f,
            1f)) =
        LocalPose(Vector3(x, y, z), rotation, scale)

    private fun ResolvedPoses.pos(id: Long) = worldOf(id).getTranslation(Vector3())

    private fun assertVec(expected: Vector3, actual: Vector3) =
        assertTrue("expected $expected, got $actual", expected.epsilonEquals(actual, 1e-4f))

    @Test
    fun aRootIsItsLocalPose() {
        val r = poses.resolve(listOf(1L), mapOf(1L to at(1f, 2f, 3f)), emptyMap())
        assertVec(Vector3(1f, 2f, 3f), r.pos(1))
        assertEquals(setOf(1L), r.roots)
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun aChildFollowsItsParent() {
        val r = poses.resolve(
            listOf(9L, 20L),
            mapOf(9L to at(0f, 21.632797f, 0f), 20L to at(0f, 1f, 0f)),
            mapOf(20L to ParentLink(9L)),
        )
        assertVec(Vector3(0f, 22.632797f, 0f), r.pos(20))
        assertEquals(listOf(20L), r.childrenOf(9))
    }

    @Test
    fun twoLevels() {
        val r = poses.resolve(
            listOf(1L, 2L, 3L),
            mapOf(1L to at(1f, 0f, 0f), 2L to at(1f, 0f, 0f), 3L to at(1f, 0f, 0f)),
            mapOf(2L to ParentLink(1L), 3L to ParentLink(2L)),
        )
        assertVec(Vector3(3f, 0f, 0f), r.pos(3))
        assertEquals(listOf(2L, 3L), r.descendantsOf(1))
        assertEquals(listOf(2L, 1L), r.ancestorsOf(3))
    }

    @Test
    fun aRotatedParentTurnsTheOffset() {
        val r = poses.resolve(
            listOf(1L, 2L),
            mapOf(1L to at(0f, 0f, 0f, Quaternion(Vector3.Y, 90f)), 2L to at(1f, 0f, 0f)),
            mapOf(2L to ParentLink(1L)),
        )
        assertVec(Vector3(0f, 0f, -1f), r.pos(2))
    }

    @Test
    fun aScaledParentScalesTheChild() {
        val r = poses.resolve(
            listOf(1L, 2L),
            mapOf(1L to at(0f, 0f, 0f, scale = Vector3(2f, 2f, 2f)), 2L to at(1f, 0f, 0f)),
            mapOf(2L to ParentLink(1L)),
        )
        assertVec(Vector3(2f, 0f, 0f), r.pos(2))
        assertVec(Vector3(2f, 2f, 2f), r.worldOf(2).getScale(Vector3()))
    }

    @Test
    fun theStoredPoseIsUsedNotALookAt() {
        // light at (0, 10, 0) with no stored rotation, aimed (by look-at) at its child handle: the handle uses the
        // stored pose only, so it sits at the origin whatever the light's look-at orientation is
        val r = poses.resolve(
            listOf(0L, 1L),
            mapOf(1L to at(0f, 10f, 0f), 0L to at(0f, -10f, 0f)),
            mapOf(0L to ParentLink(1L)),
        )
        assertVec(Vector3(0f, 0f, 0f), r.pos(0))
    }

    @Test
    fun aNodeRestTransformIsComposed() {
        val wing = Matrix4().setToTranslation(2f, 0f, 0f)
        val nodes = NodeTransforms { parent, node ->
            if (parent == 1L && node == "LeftWing") {
                NodeTransform.Found(wing)
            } else {
                NodeTransform.Missing
            }
        }
        val r = poses.resolve(
            listOf(1L, 2L),
            mapOf(1L to at(0f, 5f, 0f), 2L to at(0f, 0f, 1f)),
            mapOf(2L to ParentLink(1L, "LeftWing")),
            nodes,
        )
        assertVec(Vector3(2f, 5f, 1f), r.pos(2))
        assertVec(Vector3(2f, 5f, 0f), r.parentWorldOf(2).getTranslation(Vector3()))
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun anUnknownNodeFallsBackToTheParentWithAWarning() {
        val r = poses.resolve(
            listOf(1L, 2L),
            mapOf(1L to at(0f, 5f, 0f), 2L to at(1f, 0f, 0f)),
            mapOf(2L to ParentLink(1L, "NoSuchNode")),
            { _, _ -> NodeTransform.Missing },
        )
        assertVec(Vector3(1f, 5f, 0f), r.pos(2))
        assertEquals(listOf(HierarchyWarning(2L, HierarchyProblem.UNKNOWN_NODE, 1L, "NoSuchNode")), r.warnings)
        assertTrue(r.warnings.single().message().contains("NoSuchNode"))
    }

    @Test
    fun aNotLoadedNodeIsSilent() {
        val r = poses.resolve(
            listOf(1L, 2L),
            mapOf(1L to at(0f, 5f, 0f)),
            mapOf(2L to ParentLink(1L, "LeftWing")),
        )
        assertVec(Vector3(0f, 5f, 0f), r.pos(2))
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun aMissingParentIsARoot() {
        val r = poses.resolve(listOf(1L), mapOf(1L to at(4f, 0f, 0f)), mapOf(1L to ParentLink(99L)))
        assertVec(Vector3(4f, 0f, 0f), r.pos(1))
        assertEquals(setOf(1L), r.roots)
        assertEquals(listOf(HierarchyWarning(1L, HierarchyProblem.MISSING_PARENT, 99L)), r.warnings)
    }

    @Test
    fun aSelfLinkIsARoot() {
        val r = poses.resolve(listOf(1L), mapOf(1L to at(4f, 0f, 0f)), mapOf(1L to ParentLink(1L)))
        assertVec(Vector3(4f, 0f, 0f), r.pos(1))
        assertEquals(HierarchyProblem.SELF_LINK, r.warnings.single().problem)
    }

    @Test
    fun aCycleMakesEachMemberARoot() {
        val r = poses.resolve(
            listOf(1L, 2L, 3L),
            mapOf(1L to at(1f, 0f, 0f), 2L to at(2f, 0f, 0f), 3L to at(0f, 1f, 0f)),
            mapOf(1L to ParentLink(2L), 2L to ParentLink(1L), 3L to ParentLink(2L)),
        )
        assertVec(Vector3(1f, 0f, 0f), r.pos(1))
        assertVec(Vector3(2f, 0f, 0f), r.pos(2))
        assertVec(Vector3(2f, 1f, 0f), r.pos(3))
        assertEquals(setOf(1L, 2L), r.roots)
        assertEquals(listOf(1L, 2L), r.warnings.filter { it.problem == HierarchyProblem.CYCLE }.map { it.entityId })
    }

    @Test
    fun aDisabledAncestorDisablesTheSubtree() {
        val r = poses.resolve(
            listOf(1L, 2L, 3L, 4L),
            emptyMap(),
            mapOf(2L to ParentLink(1L), 3L to ParentLink(2L)),
            disabledSelf = setOf(1L),
        )
        assertEquals(setOf(1L, 2L, 3L), r.disabled)
    }

    @Test
    fun warningsAreReportedOnceOverRepeatedResolves() {
        val log = HierarchyWarningLog()
        val reported = ArrayList<HierarchyWarning>()
        repeat(100) {
            val r = poses.resolve(
                listOf(1L, 2L),
                emptyMap(),
                mapOf(1L to ParentLink(2L), 2L to ParentLink(1L)),
            )
            reported += log.fresh(r.warnings)
        }
        assertEquals(listOf(1L, 2L), reported.map { it.entityId })
    }

    @Test
    fun localForRoundTrips() {
        val parent = Matrix4().set(Vector3(1f, 2f, 3f), Quaternion(Vector3.Y, 30f), Vector3(2f, 2f, 2f))
        val child = Matrix4().set(Vector3(-4f, 5f, 0.5f), Quaternion(Vector3.X, 45f), Vector3(1f, 1f, 1f))
        val fit = localFor(parent, child)
        assertFalse(fit.sheared)
        val back = Matrix4(parent).mul(fit.pose.matrix())
        for (i in 0 until 16) assertEquals("cell $i", child.`val`[i], back.`val`[i], 1e-4f)
    }

    @Test
    fun localForUnderARootIsTheWorldPose() {
        val child = Matrix4().set(Vector3(-4f, 5f, 0.5f), Quaternion(Vector3.X, 45f), Vector3(1f, 3f, 1f))
        val fit = localFor(Matrix4(), child)
        assertVec(Vector3(-4f, 5f, 0.5f), fit.pose.position)
        assertVec(Vector3(1f, 3f, 1f), fit.pose.scale)
    }

    @Test
    fun nonUniformScaleUnderRotationKeepsThePositionExact() {
        val parent = Matrix4().set(Vector3(), Quaternion(Vector3.X, 90f), Vector3(1f, 2f, 1f))
        assertTrue(shearsChildren(parent))
        assertFalse(shearsChildren(Matrix4().set(Vector3(), Quaternion(Vector3.X, 90f), Vector3(2f, 2f, 2f))))
        assertFalse(shearsChildren(Matrix4().set(Vector3(), Quaternion(), Vector3(1f, 2f, 1f))))
        val child = Matrix4().set(Vector3(0.5f, 1f, 2f), Quaternion(Vector3.Y, 30f), Vector3(1f, 1f, 1f))
        val fit = localFor(parent, child)
        val placed = Matrix4(parent).mul(fit.pose.matrix()).getTranslation(Vector3())
        assertVec(Vector3(0.5f, 1f, 2f), placed)
    }
}
