package net.nevinsky.abyssus.runtime.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.nevinsky.abyssus.runtime.ecs.EcsUtils.Companion.CAMERA_FAR
import net.nevinsky.abyssus.runtime.ecs.EcsUtils.Companion.CAMERA_FOV
import net.nevinsky.abyssus.runtime.ecs.EcsUtils.Companion.CAMERA_NEAR
import net.nevinsky.abyssus.runtime.json.number
import net.nevinsky.abyssus.runtime.ecs.putIf
import net.nevinsky.abyssus.runtime.ecs.vectorDiff

/** [camera]'s `direction` is the file's `viewPointPosition`. Bound by [CameraComponentDeserializer]. */
@JsonDeserialize(using = CameraComponentDeserializer::class)
@JsonSerialize(using = CameraComponentSerializer::class)
class CameraComponent : Component {
    val camera = PerspectiveCamera().also {
        it.near = CAMERA_NEAR
        it.far = CAMERA_FAR
        it.fieldOfView = CAMERA_FOV
    }
}

/** The `camera` object of a camera component; every field is optional and a missing one keeps the camera's default. */
private data class CameraData(
    val viewPointPosition: Vector3? = null,
    val position: Vector3? = null,
    val far: Float? = null,
    val near: Float? = null,
    val fieldOfView: Float? = null,
)

/** Reads `{"camera": {...}}` into a [CameraComponent]; the camera's own values stay where the file names none. */
class CameraComponentDeserializer : StdDeserializer<CameraComponent>(CameraComponent::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CameraComponent {
        val component = CameraComponent()
        val node: JsonNode = p.readValueAsTree()
        val data = node.get("camera")?.takeIf { it.isObject }?.let { ctxt.readTreeAsValue(it, CameraData::class.java) }
            ?: return component
        val camera = component.camera
        data.viewPointPosition?.let { camera.direction.set(it) }
        data.position?.let { camera.position.set(it) }
        data.far?.let { camera.far = it }
        data.near?.let { camera.near = it }
        data.fieldOfView?.let { camera.fieldOfView = it }
        return component
    }
}

/** Writes `{"camera": {...}}` with the vectors that differ from zero and every scalar. */
class CameraComponentSerializer : StdSerializer<CameraComponent>(CameraComponent::class.java) {
    override fun serialize(component: CameraComponent, gen: JsonGenerator, provider: SerializerProvider) {
        val c = component.camera
        val camera = JsonNodeFactory.instance.objectNode()
            .putIf("viewPointPosition", vectorDiff(c.direction))
            .putIf("position", vectorDiff(c.position))
        camera.set<JsonNode>("far", number(c.far))
        camera.set<JsonNode>("near", number(c.near))
        camera.set<JsonNode>("fieldOfView", number(c.fieldOfView))
        gen.writeTree(JsonNodeFactory.instance.objectNode().set<JsonNode>("camera", camera))
    }
}
