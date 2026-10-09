package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.lib.core.assets.MetaType

class RenderComponent(
    var shaderKey: String? = null,
    var type: MetaType? = null,
    var assetName: String = "",
) : Component

/** The asset folder an entity renders, when its render component has the requested type. */
fun assetName(entity: Entity, type: MetaType): String? {
    val renderable = entity.getComponent(RenderComponent::class.java) ?: return null
    return renderable.assetName.takeIf { renderable.type == type }
}
