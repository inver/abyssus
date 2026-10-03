/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.ecs

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.core.ModelInstance
import net.nevinsky.abyssus.ecs.component.CameraComponent
import net.nevinsky.abyssus.ecs.component.IdComponent
import net.nevinsky.abyssus.ecs.component.Point2PointPositionComponent
import net.nevinsky.abyssus.ecs.component.PositionComponent
import net.nevinsky.abyssus.ecs.render.RenderComponent
import net.nevinsky.abyssus.ecs.render.RenderContext
import net.nevinsky.abyssus.ecs.render.RenderableDelegate
import net.nevinsky.abyssus.ecs.scene.SceneEngine
import net.nevinsky.abyssus.ecs.system.RenderComponentSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class Recorder(private val rotationOf: (() -> Float)? = null) :
    RenderableDelegate {
    var transform: Matrix4? = null
    var points: Pair<Vector3, Vector3>? = null
    val drawn = mutableListOf<RenderContext>()
    var rotationAtDraw: Float? = null
    override val modelInstance: ModelInstance? = null
    override fun setPosition(position: Matrix4) {
        transform = Matrix4(position)
    }

    override fun set2PointPosition(point1: Vector3, point2: Vector3) {
        points = Vector3(point1) to Vector3(point2)
    }

    override fun render(context: RenderContext, delta: Float) {
        drawn += context
        rotationAtDraw = rotationOf?.invoke()
    }
}

class SystemsTest {
    private val engine = EcsConfigurator().createEngine()

    private fun entity(id: Int, vararg components: com.badlogic.ashley.core.Component): Entity =
        Entity().also { e ->
            e.add(IdComponent(id))
            components.forEach { e.add(it) }
            engine.addEntity(e)
            engine.ids.register(id, e)
        }

    private fun position(e: Entity) = e.getComponent(PositionComponent::class.java)

    @Test
    fun lookAtMatchesMundusAngles() {
        // Mundus: dir (0,0,-1): yaw = atan(-0/-1) = 0 (z<0, no +180), pitch = 90 - acos(0) = 0 -> identity
        val a = entity(0, PositionComponent(0f, 0f, 0f).also { it.lookAtId = 1 })
        entity(1, PositionComponent(0f, 0f, -10f))
        engine.update(0f)
        val q = position(a).localRotation
        assertEquals(0f, q.x, 1e-6f); assertEquals(0f, q.y, 1e-6f); assertEquals(0f, q.z, 1e-6f); assertEquals(1f, q.w, 1e-6f)

        // dir (1,0,0): atan(1/0)=90, z=0 so no +180; yaw 90, pitch 0 -> quaternion about y by 90 degrees
        val b = entity(2, PositionComponent(0f, 0f, 0f).also { it.lookAtId = 3 })
        entity(3, PositionComponent(5f, 0f, 0f))
        engine.update(0f)
        val r = position(b).localRotation
        val s = Math.sqrt(0.5).toFloat()
        assertEquals(0f, r.x, 1e-5f); assertEquals(s, r.y, 1e-5f); assertEquals(0f, r.z, 1e-5f); assertEquals(s, r.w, 1e-5f)

        // dir (0,0,1): yaw = 0 + 180 -> rotation about y by 180
        val c = entity(4, PositionComponent(0f, 0f, 0f).also { it.lookAtId = 5 })
        entity(5, PositionComponent(0f, 0f, 3f))
        engine.update(0f)
        val t = position(c).localRotation
        assertEquals(1f, t.y, 1e-5f); assertEquals(0f, t.w, 1e-5f)
    }

    @Test
    fun lookAtDirectlyAboveHasYaw90AndNoNaN() {
        val a = entity(0, PositionComponent(0f, 0f, 0f).also { it.lookAtId = 1 })
        entity(1, PositionComponent(0f, 10f, 0f))
        engine.update(0f)
        val q = position(a).localRotation
        assertFalse(q.x.isNaN() || q.y.isNaN() || q.z.isNaN() || q.w.isNaN())
        // yaw 90, pitch 90 - acos(1) = 90
        val expected = com.badlogic.gdx.math.Quaternion().setEulerAngles(90f, 90f, 0f).nor()
        assertEquals(expected.x, q.x, 1e-5f); assertEquals(expected.y, q.y, 1e-5f)
        assertEquals(expected.z, q.z, 1e-5f); assertEquals(expected.w, q.w, 1e-5f)
    }

    @Test
    fun noTargetKeepsRotation() {
        val a = entity(0, PositionComponent(1f, 2f, 3f))
        position(a).localRotation.set(0.1f, 0.2f, 0.3f, 0.9f)
        engine.update(0f)
        assertEquals(0.2f, position(a).localRotation.y, 0f)
    }

    @Test
    fun renderablesFollowTheirEntity() {
        val rec = Recorder()
        entity(0, PositionComponent(1f, 2f, 3f), rec.asComponent())
        engine.update(0f)
        assertEquals(Vector3(1f, 2f, 3f), rec.transform!!.getTranslation(Vector3()))
    }

    @Test
    fun pointToPoint() {
        val rec = Recorder()
        entity(0, PositionComponent(1f, 0f, 0f))
        entity(1, PositionComponent(0f, 5f, 0f))
        entity(2, Point2PointPositionComponent(0, 1), rec.asComponent())
        engine.update(0f)
        assertEquals(Vector3(1f, 0f, 0f), rec.points!!.first)
        assertEquals(Vector3(0f, 5f, 0f), rec.points!!.second)
    }

    @Test
    fun pointToPointMissingEndpointDoesNothing() {
        val rec = Recorder()
        entity(0, PositionComponent(1f, 0f, 0f))
        entity(1) // no position
        entity(2, Point2PointPositionComponent(0, 1), rec.asComponent())
        engine.update(0f)
        assertNull(rec.points)
    }

    @Test
    fun cameraFollowsEntityAndTarget() {
        val camera = CameraComponent()
        entity(0, PositionComponent(0f, 0f, 10f).also { it.lookAtId = 1 }, camera)
        entity(1, PositionComponent(0f, 0f, 0f))
        engine.update(0f)
        assertEquals(Vector3(0f, 0f, 10f), camera.camera.position)
        assertEquals(0f, camera.camera.direction.x, 1e-6f)
        assertEquals(-1f, camera.camera.direction.z, 1e-6f)

        val free = CameraComponent()
        free.camera.direction.set(1f, 0f, 0f)
        entity(2, PositionComponent(4f, 4f, 4f), free)
        engine.update(0f)
        assertEquals(Vector3(4f, 4f, 4f), free.camera.position)
        assertEquals(Vector3(1f, 0f, 0f), free.camera.direction)
    }

    private val context = object : RenderContext {}

    @Test
    fun renderPassDrawsOnceWithRenderData() {
        val a = Recorder()
        val b = Recorder()
        entity(0, PositionComponent(), a.asComponent())
        entity(1, b.asComponent())
        engine.getSystem(RenderComponentSystem::class.java).setRenderData(context)
        engine.update(0.1f)
        assertEquals(listOf(context), a.drawn)
        assertEquals(listOf(context), b.drawn)
    }

    @Test
    fun noRenderDataDrawsNothing() {
        val a = Recorder()
        entity(0, a.asComponent())
        engine.update(0.1f)
        assertTrue(a.drawn.isEmpty())
    }

    @Test
    fun rotationIsAppliedBeforeDrawing() {
        lateinit var self: Entity
        val rec = Recorder(rotationOf = { position(self).localRotation.y })
        self = entity(0, PositionComponent(0f, 0f, 0f).also { it.lookAtId = 1 }, rec.asComponent())
        entity(1, PositionComponent(0f, 0f, 3f))
        engine.getSystem(RenderComponentSystem::class.java).setRenderData(context)
        engine.update(0f)
        assertEquals(1f, rec.rotationAtDraw!!, 1e-5f)
        assertEquals(1f, rec.transform!!.getRotation(com.badlogic.gdx.math.Quaternion()).y, 1e-5f)
    }
}
