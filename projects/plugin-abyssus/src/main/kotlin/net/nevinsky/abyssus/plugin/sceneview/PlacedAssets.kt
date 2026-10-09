/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.gdx.editor.scene.PlacedEntities
import net.nevinsky.abyssus.lib.gdx.editor.scene.PlacedEntity
import net.nevinsky.abyssus.lib.gdx.editor.content.AssetPlacement

import com.badlogic.gdx.utils.Disposable
import java.io.File

/**
 * The entities of the scene that show one kind of loaded asset ([A] built, [E] the entity): it loads the
 * assets the placements name and keeps one entity per placement whose asset is ready, made by [place]. Models and
 * terrains differ only in their entity, so they share this. GL thread only.
 */
open class PlacedAssets<A : Disposable, E : PlacedEntity<A>>(
    private val assets: AssetView<A>,
    place: (AssetPlacement, A, E?) -> E,
) : Disposable {
    private val entities = PlacedEntities(place)

    /** Entities with a loaded asset, in scene order. Valid until the next [update]. */
    val drawn: Collection<E> get() = entities.drawn

    val isLoading: Boolean get() = assets.isLoading

    /** Brings the entities in line with [placements]: loads the assets they name, drops the rest. */
    fun update(placements: List<AssetPlacement>, projectDir: File?) {
        assets.update(projectDir, placements.mapTo(HashSet()) { it.assetName })
        entities.update(placements, assets::get)
    }

    /** Forgets everything without GL calls; see [ViewAssets.abandon]. */
    fun abandon() {
        entities.clear()
        assets.abandon()
    }

    override fun dispose() {
        entities.clear()
        assets.dispose()
    }
}
