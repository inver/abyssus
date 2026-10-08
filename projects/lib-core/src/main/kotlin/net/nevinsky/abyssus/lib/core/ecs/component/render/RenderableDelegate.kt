package net.nevinsky.abyssus.lib.core.ecs.component.render

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ModelInstance
import net.nevinsky.abyssus.lib.core.assets.AssetMeta

interface RenderableDelegate {
    val modelInstance: ModelInstance?
    val meta: AssetMeta<Any>?
    val shaderKey: String?
    val name: String

    fun setPosition(position: Matrix4) = Unit
    fun set2PointPosition(localPosition: Vector3, localPosition2: Vector3) {
        //do nothing
    }
}