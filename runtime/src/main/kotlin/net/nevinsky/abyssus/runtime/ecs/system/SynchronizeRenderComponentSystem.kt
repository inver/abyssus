package net.nevinsky.abyssus.runtime.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderComponent

class SynchronizeRenderComponentSystem(priority: Int = 0) :
    IteratingSystem(Family.all(PositionComponent::class.java, RenderComponent::class.java).get(), priority) {
    private val renderMapper = ComponentMapper.getFor(RenderComponent::class.java)
    private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)

    override fun processEntity(entity: Entity, deltaTime: Float) {
        renderMapper[entity].renderable?.setPosition(positionMapper[entity].getTransform())
    }
}