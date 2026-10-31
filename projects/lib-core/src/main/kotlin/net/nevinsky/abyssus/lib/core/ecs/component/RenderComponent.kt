package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import net.nevinsky.abyssus.lib.core.assets.MetaType


class RenderComponent(
    var shaderKey: String? = null,
    var type: MetaType? = null,
    var assetName: String = "",
) : Component {
    /**
     * The one mesh of a `MODEL` asset to draw, with its materials; absent draws the whole model.
     * Ignored (but kept) on a `TERRAIN`.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    var meshId: String? = null

    /** Editor-only scene-instance optical overrides per material id; the game keeps them but never reads them. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    var rayTracingMaterials: JsonNode? = null
}

/** The asset folder an entity renders, when its render component has the requested type. */
fun assetName(entity: Entity, type: MetaType): String? {
    val renderable = entity.activeComponent(RenderComponent::class.java) ?: return null
    return renderable.assetName.takeIf { renderable.type == type }
}
