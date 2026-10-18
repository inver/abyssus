/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ecs.component.FoliageComponent
import net.nevinsky.abyssus.lib.core.ecs.component.IdComponent
import net.nevinsky.abyssus.lib.core.ecs.component.assetName

/** The foliage asset [foliage] of [entity], which renders the terrain asset [terrain]. */
class SceneFoliage(val entity: Entity, val foliage: String, val terrain: String) {
    /** The entity's id as the scene file holds it; -1 for an entity without one. */
    val entityId: Long get() = entity.getComponent(IdComponent::class.java)?.id ?: -1L
}

/**
 * Lists the foliage of a loaded scene: one [SceneFoliage] per entity that renders a terrain asset and holds a
 * [FoliageComponent] with a nonempty name, in ascending entity id order. It reads components only (no files, no GL), so
 * a name that names no project asset is listed as it is and found missing when the asset is read. A foliage component on
 * an entity that renders no terrain is left out and reported once through [warn]; a missing or empty name is left out
 * without a report, since that is how an unassigned component looks.
 */
class SceneFoliageListing(private val warn: (String) -> Unit) {
    private val reported = HashSet<Long>()

    fun list(engine: SceneEngine): List<SceneFoliage> {
        val out = ArrayList<SceneFoliage>()
        for (entity in engine.entities.sortedBy { it.getComponent(IdComponent::class.java)?.id ?: Long.MAX_VALUE }) {
            val component = entity.activeComponent(FoliageComponent::class.java) ?: continue
            val foliage = component.assetName?.takeIf { it.isNotEmpty() } ?: continue
            val terrain = assetName(entity, MetaType.TERRAIN)?.takeIf { it.isNotEmpty() }
            if (terrain == null) {
                val id = entity.getComponent(IdComponent::class.java)?.id ?: -1L
                if (reported.add(id)) warn("entity $id: a foliage component needs an entity that renders a terrain; '$foliage' is not used")
                continue
            }
            out += SceneFoliage(entity, foliage, terrain)
        }
        return out
    }
}
