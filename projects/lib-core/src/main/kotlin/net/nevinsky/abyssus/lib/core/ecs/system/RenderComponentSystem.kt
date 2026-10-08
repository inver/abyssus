package net.nevinsky.abyssus.lib.core.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import net.nevinsky.abyssus.lib.core.ModelBatch
import net.nevinsky.abyssus.lib.core.ecs.component.render.RenderComponent

/** Draws every renderable once per update, with the context set by [setRenderData]; does nothing without one. */
class RenderComponentSystem(priority: Int = 0) : IteratingSystem(
    Family.all(RenderComponent::class.java).get(), priority
) {
    private val mapper = ComponentMapper.getFor(RenderComponent::class.java)
    private var modelBatch: ModelBatch? = null
    private var context: RenderContext? = null

    fun setRenderData(modelBatch: ModelBatch, context: RenderContext?) {
        this.context = context
        this.modelBatch = modelBatch
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        if (context == null || modelBatch == null) {
            return
        }
        val renderable = mapper[entity].renderable
        if (renderable.modelInstance == null) {
            return
        }
        modelBatch!!.render(renderable.modelInstance!!, renderable.shaderKey)
    }
}
