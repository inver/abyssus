package net.nevinsky.abyssus.lib.core.ecs.component.render

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.nevinsky.abyssus.lib.core.dto.RenderableDto
import net.nevinsky.abyssus.lib.core.dto.RenderableWrapper

@JsonDeserialize(using = RenderComponentDeserializer::class)
@JsonSerialize(using = RenderComponentSerializer::class)
class RenderComponent(var renderable: RenderableDelegate) : Component

/**
 * Reads `{"renderable": {...}}`. A native `asset` renderable resolves its asset through the [AssetResolver] the reader
 * was given (injected by [net.nevinsky.abyssus.lib.core.ecs.EcsLoader]); any other renderable (editor-only delegates) or
 * an asset the project lacks loads without a renderable and keeps the file's `renderable` object, which is written back.
 */
class RenderComponentDeserializer : StdDeserializer<RenderComponent>(RenderComponent::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): RenderComponent {
        val dto = p.readValueAs(RenderableWrapper::class.java).renderable
        return RenderComponent(RenderableObjectDelegate(name = dto.assetName, shaderKey = dto.shaderKey))
    }
}

class RenderComponentSerializer : StdSerializer<RenderComponent>(RenderComponent::class.java) {
    override fun serialize(component: RenderComponent, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeObject(
            RenderableWrapper(
                RenderableDto(
                    component.renderable.shaderKey,
                    component.renderable.name
                )
            )
        )
    }
}