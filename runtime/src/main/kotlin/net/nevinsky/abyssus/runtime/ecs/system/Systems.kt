/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.runtime.ecs.component.CameraComponent
import net.nevinsky.abyssus.runtime.ecs.component.Point2PointPositionComponent
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderContext
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEntityIds

private fun positionOf(ids: SceneEntityIds, id: Int): PositionComponent? =
    ids[id]?.getComponent(PositionComponent::class.java)

/** Turns an entity to face its look-at target; the angles use (yaw from x/z, pitch from y). */
class LookAtSystem(private val ids: SceneEntityIds, priority: Int = 0) :
    IteratingSystem(Family.all(PositionComponent::class.java).get(), priority) {
    private val mapper = ComponentMapper.getFor(PositionComponent::class.java)
    private val tmp1 = Vector3()
    private val tmp2 = Vector3()
    private val quat = Quaternion()

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val component = mapper[entity]
        if (component.lookAtId < 0) return
        val target = positionOf(ids, component.lookAtId) ?: return

        tmp1.set(component.localPosition)
        tmp2.set(target.localPosition).sub(tmp1).nor()
        var yaw = Math.toDegrees(Math.atan((tmp2.x / tmp2.z).toDouble()))
        if (tmp2.z > 0) yaw += 180
        if (yaw.isNaN()) yaw = 90.0
        val pitch = 90 - Math.toDegrees(Math.acos(tmp2.y.coerceIn(-1f, 1f).toDouble()))
        quat.setEulerAngles(yaw.toFloat(), pitch.toFloat(), 0f).nor()
        component.localRotation.set(quat.x, quat.y, quat.z, quat.w)
    }
}

class SynchronizeRenderComponentSystem(priority: Int = 0) :
    IteratingSystem(Family.all(PositionComponent::class.java, RenderComponent::class.java).get(), priority) {
    private val renderMapper = ComponentMapper.getFor(RenderComponent::class.java)
    private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)

    override fun processEntity(entity: Entity, deltaTime: Float) {
        renderMapper[entity].renderable?.setPosition(positionMapper[entity].getTransform())
    }
}

class SynchronizeCameraComponentSystem(private val ids: SceneEntityIds, priority: Int = 0) :
    IteratingSystem(Family.all(PositionComponent::class.java, CameraComponent::class.java).get(), priority) {
    private val cameraMapper = ComponentMapper.getFor(CameraComponent::class.java)
    private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val camera = cameraMapper[entity].camera
        val position = positionMapper[entity]
        camera.position.set(position.localPosition)
        if (position.lookAtId >= 0) positionOf(ids, position.lookAtId)?.let { camera.lookAt(it.localPosition) }
    }
}

class SynchronizeRenderPoint2PointSystem(private val ids: SceneEntityIds, priority: Int = 0) :
    IteratingSystem(Family.all(Point2PointPositionComponent::class.java).get(), priority) {
    private val p2pMapper = ComponentMapper.getFor(Point2PointPositionComponent::class.java)
    private val renderMapper = ComponentMapper.getFor(RenderComponent::class.java)

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val comp = p2pMapper[entity]
        val e1 = positionOf(ids, comp.entity1Id) ?: return
        val e2 = positionOf(ids, comp.entity2Id) ?: return
        comp.point1.set(e1.localPosition)
        comp.point2.set(e2.localPosition)
        renderMapper[entity]?.renderable?.set2PointPosition(e1.localPosition, e2.localPosition)
    }
}

/** Draws every renderable once per update, with the context set by [setRenderData]; does nothing without one. */
class RenderComponentSystem(priority: Int = 0) :
    IteratingSystem(Family.all(RenderComponent::class.java).get(), priority) {
    private val mapper = ComponentMapper.getFor(RenderComponent::class.java)
    private var context: RenderContext? = null

    fun setRenderData(context: RenderContext?) {
        this.context = context
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val context = context ?: return
        mapper[entity].renderable?.render(context, deltaTime)
    }
}
