package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.assets.MetaType

class RenderComponent(
    val shaderKey: String? = null,
    val type: MetaType? = null,
    val assetName: String = "",
) : Component