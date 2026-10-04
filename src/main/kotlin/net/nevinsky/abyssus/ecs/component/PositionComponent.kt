package net.nevinsky.abyssus.ecs.component

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.databind.JsonNode
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.ecs.NO_ENTITY

/**
 * [lookAtId] is the numeric entity id Ashley code uses, `-1` for no target (also for an id that is not a number).
 * [lookAtRef] is the reference as the file names it, integer or text such as `"h"`, null for none or `-1`;
 * [lookAtSource] is the file's own `lookAtId` node, which the codec writes back while the reference is unchanged.
 */
class PositionComponent(var lookAtId: Int = NO_ENTITY) : Component {
    var lookAtRef: String? = null
    internal var lookAtSource: JsonNode? = null

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