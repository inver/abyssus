package net.nevinsky.abyssus.lib.core.ecs.component.render

import net.nevinsky.abyssus.lib.core.ModelInstance
import net.nevinsky.abyssus.lib.core.assets.AssetMeta

interface RenderableDelegate {
    val modelInstance: ModelInstance?
    val shaderKey: String?
    val meta: AssetMeta<Any>
}