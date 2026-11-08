/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.editor.entityOf
import net.nevinsky.abyssus.lib.core.editor.hierarchy.SceneHierarchy
import net.nevinsky.abyssus.lib.core.editor.sceneOf
import net.nevinsky.abyssus.lib.core.format.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HierarchyRebaseTest {
    private fun assertVec(expected: Vector3, actual: Vector3) =
        assertTrue("expected $expected, got $actual", expected.epsilonEquals(actual, 1e-4f))

    private fun position(x: Float, y: Float, z: Float, rotation: Quaternion = Quaternion()) =
        PositionComponent(x, y, z).also { it.localRotation.set(rotation) }

    /** `Lights/scenes/Abyssus Lights.scene` as it was written: two handles at the origin, under lights above it. */
    private fun abyssusLights() = sceneOf(
        entityOf(0, EntityType.HANDLE, PositionComponent(), ParentComponent(1)),
        entityOf(1, EntityType.LIGHT_DIRECTIONAL, PositionComponent(Vector3(0f, 10f, 0f), lookAtId = 0)),
        entityOf(3, EntityType.HANDLE, PositionComponent(), ParentComponent(4)),
        entityOf(4, EntityType.LIGHT_SPOT, PositionComponent(Vector3(0f, 5f, 0f), lookAtId = 3)),
    )

    @Test
    fun theAbyssusLightsHandlesBecomeLocal() {
        val scene = abyssusLights()
        assertTrue(HierarchyRebase().apply(scene))
        assertEquals(Vector3(0f, -10f, 0f), scene.entity(0)!!.component(PositionComponent::class.java)!!.localPosition)
        assertEquals(Vector3(0f, -5f, 0f), scene.entity(3)!!.component(PositionComponent::class.java)!!.localPosition)
        // a handle carries no orientation: only its position is written
        assertEquals(Quaternion(), scene.entity(0)!!.component(PositionComponent::class.java)!!.localRotation)
        // the lights are roots and keep their values
        assertEquals(Vector3(0f, 10f, 0f), scene.entity(1)!!.component(PositionComponent::class.java)!!.localPosition)
    }

    @Test
    fun cameraFourHandleMapsBackToTheOrigin() {
        val rotation = Quaternion(-0.11914976f, -0.6859156f, -0.115346394f, 0.7085326f)
        val scene = sceneOf(
            entityOf(3, EntityType.HANDLE, PositionComponent(), ParentComponent(4)),
            entityOf(4, EntityType.CAMERA, position(-23.56657f, 12.318308f, -0.84287655f, rotation)),
        )
        assertTrue(HierarchyRebase().apply(scene))
        val world = SceneHierarchy().resolve(scene).worldOf(3).getTranslation(Vector3())
        assertVec(Vector3(0f, 0f, 0f), world)
    }

    @Test
    fun rootsAndIdentityParentCopiesAreUnchanged() {
        val scene = sceneOf(
            entityOf(11, EntityType.GROUP),
            entityOf(12, EntityType.OBJECT, position(5f, 1f, 2f, Quaternion(Vector3.Y, 30f)), ParentComponent(11)),
            entityOf(13, EntityType.OBJECT, position(7f, 0f, 0f)),
        )
        assertFalse(HierarchyRebase().apply(scene))
        assertEquals(Vector3(5f, 1f, 2f), scene.entity(12)!!.component(PositionComponent::class.java)!!.localPosition)
    }

    @Test
    fun aNonHandleChildKeepsItsWorldPose() {
        val scene = sceneOf(
            entityOf(1, EntityType.OBJECT, position(1f, 0f, 0f, Quaternion(Vector3.Y, 90f))),
            entityOf(2, EntityType.OBJECT, position(1f, 0f, -1f, Quaternion(Vector3.Y, 90f)), ParentComponent(1)),
        )
        assertTrue(HierarchyRebase().apply(scene))
        val world = SceneHierarchy().resolve(scene).worldOf(2)
        assertVec(Vector3(1f, 0f, -1f), world.getTranslation(Vector3()))
        val local = scene.entity(2)!!.component(PositionComponent::class.java)!!
        assertTrue("${local.localRotation}", local.localRotation.isIdentity(1e-5f))
    }
}
