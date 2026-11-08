package net.nevinsky.abyssus.lib.core.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.SceneEntityIds
import net.nevinsky.abyssus.lib.core.ecs.component.CameraComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.isActive
import net.nevinsky.abyssus.lib.core.ecs.component.worldPosition

/** Puts each camera at its entity's world position and, when the entity has a look-at target, aims it there. */
class SynchronizeCameraComponentSystem(private val ids: SceneEntityIds, priority: Int = 0) :
    IteratingSystem(Family.all(PositionComponent::class.java, CameraComponent::class.java).get(), priority) {
    private val cameraMapper = ComponentMapper.getFor(CameraComponent::class.java)
    private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)
    private val target = Vector3()

    override fun processEntity(entity: Entity, deltaTime: Float) {
        if (!entity.isActive(CameraComponent::class.java)) {
            return
        }
        val camera = cameraMapper[entity].camera
        val position = positionMapper[entity]
        entity.worldPosition(camera.position)
        if (position.lookAtId >= 0) {
            ids[position.lookAtId]?.worldPosition(target)?.let { camera.lookAt(it) }
        }
    }
}
