/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.loading.SceneAssets
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.core.AnimationController
import net.nevinsky.abyssus.core.ModelInstance
import net.nevinsky.abyssus.core.model.Model
import net.nevinsky.abyssus.assets.model.PreparedModel
import java.io.File

/** One drawn entity: its own instance, its animation (null for a static model) and what it was built from. */
class ModelEntity(
    override val placement: AssetPlacement,
    val model: Model,
    val instance: ModelInstance,
    val animation: AnimationController?,
) : PlacedEntity<Model> {
    override val asset: Model get() = model

    /** The model's bounds in its own space, computed on first use (the posed meshes are walked once, not per click). */
    val localBounds: BoundingBox by lazy { instance.calculateBoundingBox(BoundingBox())!! }
}

/**
 * The model entities of the scene. Each entity gets its own instance of the shared model, so entities using one asset
 * animate and move independently. GL thread only.
 */
class SceneModels(private val assets: SceneAssets<PreparedModel, Model>) : Disposable {
    private val entities = PlacedEntities<Model, ModelEntity> { p, model, previous ->
        // a moved entity keeps its instance and animation
        val instance = previous?.instance ?: ModelInstance(model)
        p.transform.toMatrix(instance.transform!!)
        ModelEntity(p, model, instance, previous?.animation ?: firstAnimationOf(instance))
    }

    /** Entities with a loaded model, in scene order. Valid until the next [update]. */
    val drawn: Collection<ModelEntity> get() = entities.drawn

    val isLoading: Boolean get() = assets.isLoading

    /** Brings the entities in line with [placements]; advances animations by [deltaSeconds]. */
    fun update(placements: List<AssetPlacement>, projectDir: File?, deltaSeconds: Float) {
        assets.update(projectDir, placements.mapTo(HashSet()) { it.assetName })
        entities.update(placements, assets::get)
        for (e in entities.drawn) e.animation?.update(deltaSeconds)
    }

    /** The first animation loops for as long as the entity is shown. */
    private fun firstAnimationOf(instance: ModelInstance): AnimationController? =
        instance.animations.firstOrNull()?.let { first -> AnimationController(instance).also { it.setAnimation(first.id, -1) } }

    /** Loads [names] again from [files], the project's refreshed snapshot; the old assets stay drawn until each replacement is built. */
    fun revise(files: AssetFiles, names: Set<String>) {
        assets.replaceFiles(files)
        assets.invalidate(names)
    }

    /** Forgets everything without GL calls; see [AssetCache.abandon]. */
    fun abandon() {
        entities.clear()
        assets.abandon()
    }

    override fun dispose() {
        entities.clear()
        assets.dispose()
    }
}
