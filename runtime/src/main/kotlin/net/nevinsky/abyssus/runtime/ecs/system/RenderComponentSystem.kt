package net.nevinsky.abyssus.runtime.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import net.nevinsky.abyssus.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderContext

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
