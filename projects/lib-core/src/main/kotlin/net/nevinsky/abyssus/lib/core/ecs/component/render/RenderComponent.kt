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
import net.nevinsky.abyssus.lib.core.dto.ASSET_RENDERABLE_KIND
import net.nevinsky.abyssus.lib.core.dto.RenderableDto
import net.nevinsky.abyssus.lib.core.dto.RenderableWrapper
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.io.EcsReadWarnings

@JsonDeserialize(using = RenderComponentDeserializer::class)
@JsonSerialize(using = RenderComponentSerializer::class)
class RenderComponent(var renderable: RenderableDelegate) : Component

/**
 * Reads `{"renderable": {...}}`. A native `asset` renderable resolves its asset through the [AssetResolver] the reader
 * was given (injected by [net.nevinsky.abyssus.lib.core.ecs.EcsLoader]); any other renderable (editor-only delegates) or
 * an asset the project lacks loads without a renderable and keeps the file's `renderable` object, which is written back.
 */
class RenderComponentDeserializer : StdDeserializer<RenderComponent>(RenderComponent::class.java) {
    private val format = AbyssusDocumentFormat()
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): RenderComponent {
        val dto = p.readValueAs(RenderableWrapper::class.java).renderable

        val resolver = ctxt.findInjectableValue(AssetResolver::class.java.name, null, null) as AssetResolver
        val warnings = ctxt.findInjectableValue(
            EcsReadWarnings::class.java.name, null, null, null, null
        ) as EcsReadWarnings

        if (dto.kind != ASSET_RENDERABLE_KIND) {
            warnings.warn("renderable kind ${dto.kind} is not supported and is kept unchanged")
            TODO("IMPLEMENT IT")
        }
        val resolved = resolver.resolve(dto.assetType, dto.assetName)
        if (resolved == null) {
            warnings.warn("render asset $type $name has no folder in the project assets")
            return RenderComponent(null, raw)
        }
        return RenderComponent(RenderableObjectDelegate(resolved, data.shaderKey), raw)
    }
}

class RenderComponentSerializer : StdSerializer<RenderComponent>(RenderComponent::class.java) {
    override fun serialize(component: RenderComponent, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeObject(
            RenderableWrapper(
                RenderableDto(
                    ASSET_RENDERABLE_KIND,
                    component.renderable.shaderKey,
                    component.renderable.meta.type,
                    component.renderable.meta.name
                )
            )
        )
    }
}