package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.annotation.JsonIgnore
import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY

/**
 * Bound from the scene JSON by Jackson: `localPosition`, `localRotation` and `localScale` merge into the component's
 * own vectors (a field the file leaves out keeps its default), and `lookAtId` takes an integer or text.
 */
class PositionComponent(
    var lookAtId: Int = NO_ENTITY,
    val localPosition: Vector3 = Vector3(),
    var localScale: Vector3 = Vector3(1f, 1f, 1f),
    val localRotation: Quaternion = Quaternion()
) : Component {

    @JsonIgnore
    private val combined = Matrix4()

    constructor(x: Float, y: Float, z: Float) : this() {
        localPosition.set(x, y, z)
    }

    constructor(position: Vector3, lookAtId: Int = NO_ENTITY) : this(lookAtId) {
        localPosition.set(position)
    }

    /** The transform built from position, rotation and scale; the same matrix instance on every call. */
    @JsonIgnore
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
