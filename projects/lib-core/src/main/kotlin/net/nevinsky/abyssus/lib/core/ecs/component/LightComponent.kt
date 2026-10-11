package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.nevinsky.abyssus.lib.core.dto.LightDto
import net.nevinsky.abyssus.lib.core.dto.LightWrapper

@JsonDeserialize(using = LightComponentDeserializer::class)
@JsonSerialize(using = LightComponentSerializer::class)
class LightComponent(val light: LightDto) : Component

class LightComponentDeserializer : StdDeserializer<LightComponent>(LightComponent::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LightComponent {
        val dto = p.readValueAs(LightWrapper::class.java)?.light ?: return LightComponent(LightDto())

        return LightComponent(dto)
    }
}

/**
 * Writes a light into the file's own node (so unknown members and number spelling survive), changing only the values
 * that differ from what was loaded; a value equal to its default is left out, the others are written.
 */
class LightComponentSerializer : StdSerializer<LightComponent>(LightComponent::class.java) {
    override fun serialize(component: LightComponent, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeObject(LightWrapper(component.light))
    }
}
