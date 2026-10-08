/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.runtime.ecs.render

import net.nevinsky.abyssus.lib.runtime.ecs.putIf
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
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.ModelBatch
import net.nevinsky.abyssus.lib.core.ModelInstance
import net.nevinsky.abyssus.lib.core.RenderableProvider
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.runtime.ecs.scene.SceneEcsWarnings

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
        renderables: com.badlogic.gdx.utils.Array<net.nevinsky.abyssus.lib.core.Renderable>,
        pool: com.badlogic.gdx.utils.Pool<net.nevinsky.abyssus.lib.core.Renderable>,
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
        if (name in names) {
            AssetReference(name, type)
        } else null
}

interface RenderableDelegate {
    val modelInstance: ModelInstance?

    fun setPosition(position: Matrix4) = Unit

    fun set2PointPosition(point1: Vector3, point2: Vector3) = Unit

    fun render(context: RenderContext, delta: Float)

    fun asComponent() = RenderComponent(this)
}

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
