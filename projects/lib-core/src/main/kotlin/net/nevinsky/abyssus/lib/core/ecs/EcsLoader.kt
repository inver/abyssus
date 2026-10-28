/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY
import net.nevinsky.abyssus.lib.core.dto.ENTITY_DISABLED_FLAG
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper

/**
 * Puts the typed entities of a scene's `ecs` block into a [SceneEngine]. The components were already bound when the scene
 * was read (see [EntityDtoModule]), header included, so the entities go into the engine as they are; a [RawComponent] is not added to the entity but carried (see [EcsLoadingWarns.carried])
 * with one warning, so a scene can be written back unchanged. The `flags` of an entity set whether it is enabled
 * ([ENTITY_DISABLED_FLAG]) in its [HeaderComponent], which also holds the entity id and type; its other bits are kept in [HeaderComponent.flags]. References to ids that are not in the
 * file become [NO_ENTITY].
 *
 * The Mundus-era `archetype` of an entity and the `archetypes` table are not part of the format.
 */
class EcsLoader {

    fun loadToEngine(entities: Map<Long, Entity>, engine: SceneEngine): EcsLoadingWarns {
        val warnings = LinkedHashSet<String>()
        val carried = HashMap<Long, Map<String, RawComponent>>()

        for ((id, entity) in entities) {
            // the raw entries are carried, not part of the running entity
            entity.remove(RawComponentsWrapper::class.java)?.let { wrapper ->
                val carriedRaw = LinkedHashMap<String, RawComponent>()
                for (raw in wrapper.entries) {
                    carriedRaw[raw.name] = raw
                    warnings.add("entity $id: ${raw.reason}")
                }
                carried[id] = carriedRaw
            }
            engine.addEntity(entity)
            engine.ids.register(id.toInt(), entity)
        }
        resolveReferences(engine, warnings)
        return EcsLoadingWarns(warnings.toList(), carried)
    }

    private fun resolveReferences(engine: SceneEngine, warnings: MutableSet<String>) {
        for (entity in engine.entities) {
            val from = entity.getComponent(HeaderComponent::class.java).id
            entity.getComponent(PositionComponent::class.java)
                ?.let { it.lookAtId = check(engine, warnings, from, "look-at", it.lookAtId) }
            entity.getComponent(ParentComponent::class.java)
                ?.let { it.parentEntityId = check(engine, warnings, from, "parent", it.parentEntityId) }
        }
    }

    private fun check(
        engine: SceneEngine,
        warnings: MutableSet<String>,
        from: Long,
        kind: String,
        target: Int
    ): Int {
        if (target == NO_ENTITY || target in engine.ids) {
            return target
        }
        warnings.add("entity $from: $kind refers to entity $target, which is not in the scene")
        return NO_ENTITY
    }
}
