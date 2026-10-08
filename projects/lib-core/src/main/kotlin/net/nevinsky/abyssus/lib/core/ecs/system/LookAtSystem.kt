package net.nevinsky.abyssus.lib.core.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.scene.SceneEntityIds
import kotlin.math.acos
import kotlin.math.atan

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
        var yaw = Math.toDegrees(atan((tmp2.x / tmp2.z).toDouble()))
        if (tmp2.z > 0) {
            yaw += 180
        }
        if (yaw.isNaN()) {
            yaw = 90.0
        }
        val pitch = 90 - Math.toDegrees(acos(tmp2.y.coerceIn(-1f, 1f).toDouble()))
        quat.setEulerAngles(yaw.toFloat(), pitch.toFloat(), 0f).nor()
        component.localRotation.set(quat.x, quat.y, quat.z, quat.w)
    }

    fun positionOf(ids: SceneEntityIds, id: Int): PositionComponent? =
        ids[id]?.getComponent(PositionComponent::class.java)
}