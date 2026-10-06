/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs.render

import net.nevinsky.abyssus.runtime.ecs.putIf
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import net.nevinsky.abyssus.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.core.ModelBatch
import net.nevinsky.abyssus.core.ModelInstance
import net.nevinsky.abyssus.core.RenderableProvider
import net.nevinsky.abyssus.core.assets.MetaType
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEcsWarnings

/** What a render pass draws with; the scene view supplies it. Opaque to the systems. */
interface RenderContext

/** A [RenderContext] of the `:gdx-model` [batch] and the lighting [environment]. */
class BatchRenderContext(val batch: ModelBatch, val environment: Environment?) : RenderContext


/** An asset a render component names: a model or a terrain, optionally with geometry. */
interface RenderableSceneObject : RenderableProvider {
    val assetName: String
    val type: MetaType

    /** Null for a reference that carries no geometry. */
    val modelInstance: ModelInstance?
}

/** A [RenderableSceneObject] that only names the asset and draws nothing. */
class AssetReference(override val assetName: String, override val type: MetaType) : RenderableSceneObject {
    override val modelInstance: ModelInstance? = null

    override fun getRenderables(
        renderables: com.badlogic.gdx.utils.Array<net.nevinsky.abyssus.core.Renderable>,
        pool: com.badlogic.gdx.utils.Pool<net.nevinsky.abyssus.core.Renderable>,
    ) = Unit
}

/** Turns an asset name into a [RenderableSceneObject], or null when the project has no such asset. */
fun interface AssetResolver {
    fun resolve(type: MetaType, name: String): RenderableSceneObject?
}

/** Resolves project folder names to references without allocating GL resources. */
class FolderAssetResolver(assetFolders: Collection<String>) : AssetResolver {
    private val names = assetFolders.toSet()
    override fun resolve(type: MetaType, name: String): RenderableSceneObject? =
        if (name in names) AssetReference(name, type) else null
}

interface RenderableDelegate {
    val modelInstance: ModelInstance?

    fun setPosition(position: Matrix4) = Unit

    fun set2PointPosition(point1: Vector3, point2: Vector3) = Unit

    fun render(context: RenderContext, delta: Float)

    fun asComponent() = RenderComponent(this)
}

/**
 * The renderable of an entity. [renderable] is null when the file's renderable is not one the plugin knows (an
 * editor-only delegate, an asset the project lacks); [raw] then holds the file's `renderable` object to write back.
 */
@JsonDeserialize(using = RenderComponentDeserializer::class)
@JsonSerialize(using = RenderComponentSerializer::class)
class RenderComponent(var renderable: RenderableDelegate? = null, var raw: JsonNode? = null) : Component

class RenderableObjectDelegate(var asset: RenderableSceneObject, var shaderKey: String?) : RenderableDelegate {
    override val modelInstance: ModelInstance? get() = asset.modelInstance

    override fun setPosition(position: Matrix4) {
        asset.modelInstance?.transform = (asset.modelInstance?.transform ?: Matrix4()).set(position)
    }

    override fun render(context: RenderContext, delta: Float) {
        if (context is BatchRenderContext) context.batch.render(asset, context.environment, shaderKey)
    }
}

/** The stable native kind for an asset renderable. */
const val ASSET_RENDERABLE_KIND = "asset"

private data class RenderableData(val kind: String? = null, val shaderKey: String? = null, val asset: AssetData? = null)

private data class AssetData(val type: MetaType? = null, val assetName: String? = null)

/**
 * Reads `{"renderable": {...}}`. A native `asset` renderable resolves its asset through the [AssetResolver] the reader
 * was given (injected by [net.nevinsky.abyssus.runtime.ecs.EcsLoader]); any other renderable (editor-only delegates) or
 * an asset the project lacks loads without a renderable and keeps the file's `renderable` object, which is written back.
 */
class RenderComponentDeserializer : StdDeserializer<RenderComponent>(RenderComponent::class.java) {
    private val format = AbyssusDocumentFormat()
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): RenderComponent {
        val node: JsonNode = p.readValueAsTree()
        val raw = node.get("renderable")?.takeIf { it.isObject } ?: return RenderComponent()
        format.requireRenderable(raw)
        val resolver = ctxt.findInjectableValue(AssetResolver::class.java.name, null, null) as AssetResolver
        val warnings = ctxt.findInjectableValue(SceneEcsWarnings::class.java.name, null, null) as SceneEcsWarnings
        val data = ctxt.readTreeAsValue(raw, RenderableData::class.java)
        if (data.kind != ASSET_RENDERABLE_KIND) {
            warnings.warn("renderable kind ${data.kind} is not supported and is kept unchanged")
            return RenderComponent(null, raw)
        }
        val type = data.asset?.type?.takeIf { it != MetaType.UNKNOWN }
        val name = data.asset?.assetName
        if (type == null || name == null) {
            warnings.warn("render asset ${raw.get("asset") ?: "(missing)"} names no MODEL or TERRAIN asset")
            return RenderComponent(null, raw)
        }
        val resolved = resolver.resolve(type, name)
        if (resolved == null) {
            warnings.warn("render asset $type $name has no folder in the project assets")
            return RenderComponent(null, raw)
        }
        return RenderComponent(RenderableObjectDelegate(resolved, data.shaderKey), raw)
    }
}

/**
 * Writes an asset renderable from its asset, into the file's own `renderable` object so unknown members survive; any
 * other renderable (an editor-only delegate, or an asset the project lacked when the scene loaded) is written as the
 * file's own `renderable` object.
 */
class RenderComponentSerializer : StdSerializer<RenderComponent>(RenderComponent::class.java) {
    private val format = AbyssusDocumentFormat()
    override fun serialize(component: RenderComponent, gen: JsonGenerator, provider: SerializerProvider) {
        val nodes = JsonNodeFactory.instance
        val renderable = component.renderable
        val raw = component.raw
        raw?.let(format::requireRenderable)
        if (renderable !is RenderableObjectDelegate) {
            gen.writeTree(nodes.objectNode().putIf("renderable", raw))
            return
        }
        val out = (raw as? ObjectNode)?.deepCopy() ?: nodes.objectNode()
        out.put("kind", ASSET_RENDERABLE_KIND)
        renderable.shaderKey?.let { out.put("shaderKey", it) }
        out.set<JsonNode>(
            "asset",
            nodes.objectNode().put("type", renderable.asset.type.name).put("assetName", renderable.asset.assetName),
        )
        gen.writeTree(nodes.objectNode().set<JsonNode>("renderable", out))
    }
}
