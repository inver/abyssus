package net.nevinsky.abyssus.lib.core.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import kotlin.math.acos
import kotlin.math.atan
import net.nevinsky.abyssus.lib.core.ecs.SceneEntityIds
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.WorldTransformComponent
import net.nevinsky.abyssus.lib.core.ecs.component.isActive
import net.nevinsky.abyssus.lib.core.ecs.component.worldPosition

/**
 * Turns an entity to face its look-at target, from its world position to the target's world position; the angles use
 * (yaw from x/z, pitch from y). The orientation is the entity's own drawing only: with a resolved
 * [WorldTransformComponent] it replaces the rotation of the world matrix and the stored `localRotation` is left alone,
 * so children (which follow the stored pose) do not turn with it. In an engine without a hierarchy system the
 * `localRotation` is written, as before.
 */
class LookAtSystem(private val ids: SceneEntityIds, priority: Int = 0) :
    IteratingSystem(Family.all(PositionComponent::class.java).get(), priority) {
    private val mapper = ComponentMapper.getFor(PositionComponent::class.java)
    private val tmp1 = Vector3()
    private val tmp2 = Vector3()
    private val scale = Vector3()
    private val quat = Quaternion()

    override fun processEntity(entity: Entity, deltaTime: Float) {
        if (!entity.isActive(PositionComponent::class.java)) {
            return
        }
        val component = mapper[entity]
        if (component.lookAtId < 0) {
            return
        }
        val target = ids[component.lookAtId] ?: return
        target.worldPosition(tmp2) ?: return
        entity.worldPosition(tmp1) ?: return

        tmp2.sub(tmp1).nor()
        var yaw = Math.toDegrees(atan((tmp2.x / tmp2.z).toDouble()))
        if (tmp2.z > 0) {
            yaw += 180
        }
        if (yaw.isNaN()) {
            yaw = 90.0
        }
        val pitch = 90 - Math.toDegrees(acos(tmp2.y.coerceIn(-1f, 1f).toDouble()))
        quat.setEulerAngles(yaw.toFloat(), pitch.toFloat(), 0f).nor()
        val world = entity.getComponent(WorldTransformComponent::class.java)?.takeIf { it.resolved }
        if (world != null) {
            world.transform.getScale(scale)
            world.transform.set(tmp1, quat, scale)
        } else {
            component.localRotation.set(quat.x, quat.y, quat.z, quat.w)
        }
    }
}
