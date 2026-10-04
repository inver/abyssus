/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs.render

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.core.ModelBatch
import net.nevinsky.abyssus.core.ModelInstance
import net.nevinsky.abyssus.core.RenderableProvider

/** What a render pass draws with; the scene view supplies it. Opaque to the systems. */
interface RenderContext

/** A [RenderContext] of the `:gdx-model` [batch] and the lighting [environment]. */
class BatchRenderContext(val batch: ModelBatch, val environment: Environment?) : RenderContext

enum class AssetType { MODEL, TERRAIN }

/** An asset a render component names: a model or a terrain, optionally with geometry. */
interface RenderableSceneObject : RenderableProvider {
    val assetName: String
    val type: AssetType

    /** Null for a reference that carries no geometry. */
    val modelInstance: ModelInstance?

    fun toDto() = Dto(assetName, type)

    data class Dto(val assetName: String, val type: AssetType)
}

/** A [RenderableSceneObject] that only names the asset and draws nothing. */
class AssetReference(override val assetName: String, override val type: AssetType) : RenderableSceneObject {
    override val modelInstance: ModelInstance? = null

    override fun getRenderables(
        renderables: com.badlogic.gdx.utils.Array<net.nevinsky.abyssus.core.Renderable>,
        pool: com.badlogic.gdx.utils.Pool<net.nevinsky.abyssus.core.Renderable>,
    ) = Unit
}

/** Turns an asset name into a [RenderableSceneObject], or null when the project has no such asset. */
fun interface AssetResolver {
    fun resolve(type: AssetType, name: String): RenderableSceneObject?
}

/** Resolves project folder names to references without allocating GL resources. */
class FolderAssetResolver(assetFolders: Collection<String>) : AssetResolver {
    private val names = assetFolders.toSet()
    override fun resolve(type: AssetType, name: String): RenderableSceneObject? =
        if (name in names) AssetReference(name, type) else null
}

interface RenderableDelegate {
    val modelInstance: ModelInstance?

    fun setPosition(position: Matrix4) = Unit

    fun set2PointPosition(point1: Vector3, point2: Vector3) = Unit

    fun render(context: RenderContext, delta: Float)

    fun asComponent() = RenderComponent(this)

    data class Dto(val clazz: String, val shaderKey: String)
}

/**
 * The renderable of an entity. [renderable] is null when the file's renderable is not one the plugin knows (an
 * editor-only delegate, an asset the project lacks); [raw] then holds the file's `renderable` object to write back.
 */
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
