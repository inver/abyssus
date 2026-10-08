package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.NO_ENTITY
import net.nevinsky.abyssus.lib.runtime.ecs.putId
import net.nevinsky.abyssus.lib.runtime.ecs.putIf
import net.nevinsky.abyssus.lib.runtime.ecs.quaternionDiff
import net.nevinsky.abyssus.lib.runtime.ecs.vectorDiff

/**
 * Bound from the scene JSON by Jackson: `localPosition`, `localRotation` and `localScale` merge into the component's
 * own vectors (a field the file leaves out keeps its default), and `lookAtId` takes an integer or text.
 *
 * [lookAtId] is the numeric entity id Ashley code uses, `-1` for no target (also for an id that is not a number).
 * [lookAtRef] is the reference as the file names it, integer or text such as `"h"`, null for none or `-1`;
 * [lookAtSource] is the file's own `lookAtId` node, which the codec writes back while the reference is unchanged.
 */
@JsonSerialize(using = PositionComponentSerializer::class)
class PositionComponent(@get:JsonIgnore @set:JsonIgnore var lookAtId: Int = NO_ENTITY) : Component {
    @JsonIgnore
    var lookAtRef: String? = null

    @JsonIgnore
    internal var lookAtSource: JsonNode? = null

    /** The id and reference as the file gave them, so the writer can tell whether an edit has changed them. */
    @JsonIgnore
    internal var loadedLookAt: Pair<Int, String?>? = null

    @JsonMerge
    var localPosition = Vector3()

    @JsonMerge
    var localRotation = Quaternion()

    @JsonMerge
    var localScale = Vector3(1f, 1f, 1f)

    @JsonIgnore
    private val combined = Matrix4()

    /** Reads the file's `lookAtId`: an integer, `-1` for none, or text (any text names a target, an id only if numeric). */
    @JsonSetter("lookAtId")
    internal fun readLookAt(node: JsonNode?) {
        lookAtSource = node?.takeIf { !it.isNull }?.deepCopy()
        when {
            node == null || node.isNull -> {
                lookAtId = NO_ENTITY; lookAtRef = null
            }

            node.isIntegralNumber -> node.asInt(NO_ENTITY).let {
                lookAtId = it
                lookAtRef = if (it == NO_ENTITY || it < 0) null else it.toString()
            }

            node.isTextual -> node.asText().let { t ->
                lookAtId = t.trim().toIntOrNull() ?: NO_ENTITY
                lookAtRef = t.takeIf { it.isNotBlank() && it != "-1" }
            }

            else -> {
                lookAtId = node.asInt(NO_ENTITY); lookAtRef = null
            }
        }
        loadedLookAt = lookAtId to lookAtRef
    }

    @JsonCreator
    constructor() : this(NO_ENTITY)

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

/** Writes only what differs from the defaults; the file's own `lookAtId` node while the reference is as it was loaded. */
class PositionComponentSerializer : StdSerializer<PositionComponent>(PositionComponent::class.java) {
    override fun serialize(component: PositionComponent, gen: JsonGenerator, provider: SerializerProvider) {
        val out = JsonNodeFactory.instance.objectNode().putIf("localRotation", quaternionDiff(component.localRotation))
        // (`"3"`, `"-1"`, `"h"`, `3`) as the file spelled it; an edit writes an integer
        val source = component.lookAtSource
        if (source != null && component.loadedLookAt == (component.lookAtId to component.lookAtRef)) {
            out.set<JsonNode>("lookAtId", source.deepCopy())
        } else out.putId("lookAtId", component.lookAtId)
        out.putIf("localPosition", vectorDiff(component.localPosition))
            .putIf("localScale", vectorDiff(component.localScale, 1f))
        gen.writeTree(out)
    }
}
