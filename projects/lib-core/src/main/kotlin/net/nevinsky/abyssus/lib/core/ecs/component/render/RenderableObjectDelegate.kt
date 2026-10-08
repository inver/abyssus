package net.nevinsky.abyssus.lib.core.ecs.component.render

import net.nevinsky.abyssus.lib.core.ModelInstance
import net.nevinsky.abyssus.lib.core.assets.AssetMeta

class RenderableObjectDelegate(
    var asset: RenderableSceneObject?,
    override val meta: AssetMeta<Any>,
    override val shaderKey: String?
) : RenderableDelegate {
    override val modelInstance: ModelInstance? get() = asset?.modelInstance
}