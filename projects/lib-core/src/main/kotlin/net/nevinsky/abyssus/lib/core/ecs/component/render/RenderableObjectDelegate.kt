package net.nevinsky.abyssus.lib.core.ecs.component.render

import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.core.ModelInstance
import net.nevinsky.abyssus.lib.core.assets.AssetMeta

class RenderableObjectDelegate(
    var asset: RenderableSceneObject? = null,
    override val shaderKey: String?,
    override val name: String,
) : RenderableDelegate {
    override val modelInstance: ModelInstance? get() = asset?.modelInstance
    override val meta: AssetMeta<Any>? get() = asset?.meta

    override fun setPosition(position: Matrix4) {
        if (modelInstance != null) {
            modelInstance!!.transform?.set(position)
        }
    }
}