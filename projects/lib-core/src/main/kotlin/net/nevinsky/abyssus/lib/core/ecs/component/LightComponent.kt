package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.nevinsky.abyssus.lib.core.dto.LightDto
import net.nevinsky.abyssus.lib.core.dto.LightWrapper
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.LIGHT_CONE_ANGLE
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.LIGHT_EDGE_SOFTNESS
import net.nevinsky.abyssus.lib.core.util.EcsUtils.Companion.LIGHT_RANGE
import net.nevinsky.abyssus.lib.runtime.ecs.colorNode
import net.nevinsky.abyssus.lib.runtime.json.number

/**
 * Bound by [LightComponentDeserializer]: the values are in a nested `light` object or directly in the component.
 * [nested] remembers whether the file held the values in a `light` object (the shape the plugin creates) or directly. */
@JsonDeserialize(using = LightComponentDeserializer::class)
@JsonSerialize(using = LightComponentSerializer::class)
class LightComponent(val light: LightDto = LightDto(), var nested: Boolean = true) : Component

class LightComponentDeserializer : StdDeserializer<LightComponent>(LightComponent::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LightComponent {
        val node: JsonNode = p.readValueAsTree()
        val data = p.readValueAs(LightWrapper::class.java)?.light ?: return component


        val nestedNode = node.get("light")?.takeIf { it.isObject }
        val light = ctxt.readTreeAsValue(nestedNode ?: node, LightData::class.java)
        val nested = nestedNode != null || LIGHT_FIELDS.none(node::has)
        return LightComponent(light, nested).also {
            it.source = node.deepCopy()
            it.loaded = light.copy(color = light.color.copy())
        }
    }
}

private val LIGHT_FIELDS = listOf("color", "intensity", "range", "coneAngle", "edgeSoftness")

/**
 * Writes a light into the file's own node (so unknown members and number spelling survive), changing only the values
 * that differ from what was loaded; a value equal to its default is left out, the others are written.
 */
class LightComponentSerializer : StdSerializer<LightComponent>(LightComponent::class.java) {
    override fun serialize(component: LightComponent, gen: JsonGenerator, provider: SerializerProvider) {
        val nodes = JsonNodeFactory.instance
        val root = (component.source as? ObjectNode)?.deepCopy() ?: nodes.objectNode()
        val values = if (component.nested) root.get("light")?.takeIf { it.isObject }?.deepCopy<ObjectNode>()
            ?: nodes.objectNode() else root
        val previous = component.loaded
        val light = component.light
        if (previous == null || previous.color != light.color) values.set<JsonNode>("color", colorNode(light.color))
        if (previous == null || previous.intensity != light.intensity) values.set<JsonNode>(
            "intensity",
            number(light.intensity)
        )
        fun optional(key: String, value: Float, default: Float, old: Float?) {
            if (value == default) values.remove(key)
            else if (old != value) values.set<JsonNode>(key, number(value))
        }
        optional("range", light.range, LIGHT_RANGE, previous?.range)
        optional("coneAngle", light.coneAngle, LIGHT_CONE_ANGLE, previous?.coneAngle)
        optional("edgeSoftness", light.edgeSoftness, LIGHT_EDGE_SOFTNESS, previous?.edgeSoftness)
        gen.writeTree(if (component.nested) root.set<JsonNode>("light", values) else values)
    }
}
