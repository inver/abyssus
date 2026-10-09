package net.nevinsky.abyssus.lib.gdx.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.nevinsky.abyssus.lib.gdx.dto.CameraDto
import net.nevinsky.abyssus.lib.gdx.dto.CameraWrapper
import net.nevinsky.abyssus.lib.gdx.util.EcsUtils.Companion.CAMERA_FAR
import net.nevinsky.abyssus.lib.gdx.util.EcsUtils.Companion.CAMERA_FOV
import net.nevinsky.abyssus.lib.gdx.util.EcsUtils.Companion.CAMERA_NEAR

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

/** Reads `{"camera": {...}}` into a [CameraComponent]; the camera's own values stay where the file names none. */
class CameraComponentDeserializer : StdDeserializer<CameraComponent>(CameraComponent::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CameraComponent {
        val component = CameraComponent()
        val data = p.readValueAs(CameraWrapper::class.java)?.camera ?: return component

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
        gen.writeObject(
            CameraWrapper(
                CameraDto(
                    c.direction,
                    c.position,
                    c.far,
                    c.near,
                    c.fieldOfView
                )
            )
        )
    }
}
