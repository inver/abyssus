/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCollider
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageColliderShape
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMatrices
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class FoliageColliderTransformsTest {
    private val transforms = FoliageColliderTransforms()
    private val terrain = TerrainData(3, FloatArray(9) { 4f }, 10, 1f) // flat at height 4
    private val matrices = FoliageMatrices()

    private fun matrix(entity: Matrix4 = Matrix4(), yaw: Float = 0f, scale: Float = 1f): Matrix4 =
        checkNotNull(matrices.copy(terrain, 0f, entity, FoliageCopy(0, 2f, 3f, yaw, scale), Matrix4()))

    private fun placed(collider: FoliageCollider, m: Matrix4) =
        (transforms.place(collider, m) as ColliderPlacementResult.Placed).placement

    private fun reason(collider: FoliageCollider, m: Matrix4) =
        (transforms.place(collider, m) as ColliderPlacementResult.Refused).reason

    @Test
    fun aBoxTakesTheCopyHeightRotationScaleAndTransformedOffset() {
        val box = FoliageCollider(FoliageColliderShape.BOX, 1f, 2f, 3f, offsetY = 1.5f)
        val p = placed(box, matrix(yaw = (PI / 2).toFloat(), scale = 2f))
        assertEquals(2f, p.x, 1e-4f)
        assertEquals(4f + 3f, p.y, 1e-4f) // 1.5 model units up, scaled by 2
        assertEquals(3f, p.z, 1e-4f)
        assertEquals(2f, p.halfX, 1e-4f)
        assertEquals(4f, p.halfY, 1e-4f)
        assertEquals(6f, p.halfZ, 1e-4f)
        val q = Quaternion(p.qx, p.qy, p.qz, p.qw)
        assertEquals(PI.toFloat() / 2, q.getAngleAroundRad(Vector3.Y), 1e-4f)
        assertFalse(p.approximated)
    }

    @Test
    fun anOffsetIsRotatedWithTheCopy() {
        val p = placed(FoliageCollider(offsetX = 1f), matrix(yaw = (PI / 2).toFloat()))
        // a quarter turn about +Y sends local +x to world -z
        assertEquals(2f, p.x, 1e-4f)
        assertEquals(3f - 1f, p.z, 1e-4f)
    }

    @Test
    fun spheresAndCapsulesTakeTheLargestAxisAndNoteTheApproximation() {
        val entity = Matrix4().scale(1f, 3f, 2f)
        val sphere = placed(FoliageCollider(FoliageColliderShape.SPHERE, radius = 0.5f), matrix(entity))
        assertEquals(1.5f, sphere.radius, 1e-4f)
        assertTrue(sphere.approximated)
        val capsule = placed(FoliageCollider(FoliageColliderShape.CAPSULE, radius = 0.4f, halfHeight = 2f), matrix(entity))
        assertEquals(1.2f, capsule.radius, 1e-4f)
        assertEquals(6f, capsule.halfHeight, 1e-4f)
        assertTrue(capsule.approximated)
        assertFalse(placed(FoliageCollider(FoliageColliderShape.SPHERE), matrix(scale = 2f)).approximated)
    }

    @Test
    fun anOrthogonalNonUniformBoxIsRepresentable() {
        // a quarter turn under a non-uniform entity scale still has perpendicular axes
        val entity = Matrix4().scale(2f, 1f, 3f)
        val p = placed(FoliageCollider(FoliageColliderShape.BOX, 1f, 1f, 1f), matrix(entity, yaw = (PI / 2).toFloat()))
        assertEquals(3f, p.halfX, 1e-4f)
        assertEquals(1f, p.halfY, 1e-4f)
        assertEquals(2f, p.halfZ, 1e-4f)
    }

    @Test
    fun shearReflectionZeroScaleAndNonFiniteValuesAreRefused() {
        val box = FoliageCollider()
        assertTrue(reason(box, matrix(Matrix4().scale(2f, 1f, 3f), yaw = (PI / 4).toFloat())).contains("shear"))
        assertTrue(reason(box, Matrix4().scale(-1f, 1f, 1f)).contains("reflected"))
        assertTrue(reason(box, Matrix4().scale(1f, 0f, 1f)).contains("zero scale"))
        assertTrue(reason(box, Matrix4().also { it.`val`[Matrix4.M13] = Float.NaN }).contains("not finite"))
        assertTrue(reason(box, Matrix4().also { it.`val`[Matrix4.M30] = 0.5f }).contains("affine"))
    }
}
