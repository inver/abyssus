package net.nevinsky.abyssus.lib.gdx.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import net.nevinsky.abyssus.lib.gdx.ecs.component.CameraComponent
import net.nevinsky.abyssus.lib.gdx.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.gdx.scene.SceneEntityIds

class SynchronizeCameraComponentSystem(private val ids: SceneEntityIds, priority: Int = 0) :
    IteratingSystem(Family.all(PositionComponent::class.java, CameraComponent::class.java).get(), priority) {
    private val cameraMapper = ComponentMapper.getFor(CameraComponent::class.java)
    private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val camera = cameraMapper[entity].camera
        val position = positionMapper[entity]
        camera.position.set(position.localPosition)
        if (position.lookAtId >= 0) {
            ids.positionOf(position.lookAtId, PositionComponent::class.java)
                ?.let { camera.lookAt(it.localPosition) }
        }
    }
}
