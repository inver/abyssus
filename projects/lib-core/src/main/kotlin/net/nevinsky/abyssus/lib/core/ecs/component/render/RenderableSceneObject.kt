package net.nevinsky.abyssus.lib.core.ecs.component.render

import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool
import net.nevinsky.abyssus.lib.core.ModelInstance
import net.nevinsky.abyssus.lib.core.Renderable
import net.nevinsky.abyssus.lib.core.RenderableProvider

interface RenderableSceneObject : RenderableProvider {
    /** Null for a reference that carries no geometry. */
    val modelInstance: ModelInstance

    override fun getRenderables(renderables: Array<Renderable>, pool: Pool<Renderable>) {
        modelInstance.getRenderables(renderables, pool)
    }
}