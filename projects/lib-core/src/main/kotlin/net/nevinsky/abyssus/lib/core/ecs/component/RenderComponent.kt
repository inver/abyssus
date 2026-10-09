package net.nevinsky.abyssus.lib.gdx.ecs.component

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.gdx.assets.MetaType

class RenderComponent(
    var shaderKey: String? = null,
    var type: MetaType? = null,
    var assetName: String = "",
) : Component