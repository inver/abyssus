package net.nevinsky.abyssus.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.ecs.NO_ENTITY

class PositionComponent(var lookAtId: Int = NO_ENTITY) : Component {
    val localPosition = Vector3()
    val localRotation = Quaternion()
    val localScale = Vector3(1f, 1f, 1f)

    private val combined = Matrix4()

    constructor(x: Float, y: Float, z: Float) : this() {
        localPosition.set(x, y, z)
    }

    constructor(position: Vector3, lookAtId: Int = NO_ENTITY) : this(lookAtId) {
        localPosition.set(position)
    }

    /** The transform built from position, rotation and scale; the same matrix instance on every call. */
    fun getTransform(): Matrix4 = combined.set(localPosition, localRotation, localScale)

    fun getLocalPosition(out: Vector3): Vector3 = out.set(localPosition)

    fun getPosition(out: Vector3): Vector3 = getTransform().getTranslation(out)

    fun translate(v: Vector3) {
        localPosition.add(v)
    }

    fun translate(x: Float, y: Float, z: Float) {
        localPosition.add(x, y, z)
    }
}