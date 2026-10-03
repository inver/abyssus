/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.ecs.scene

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.assets.json.obj
import net.nevinsky.abyssus.ecs.NO_ENTITY
import net.nevinsky.abyssus.ecs.component.IdComponent
import net.nevinsky.abyssus.ecs.component.ParentComponent
import net.nevinsky.abyssus.ecs.component.Point2PointPositionComponent
import net.nevinsky.abyssus.ecs.component.PositionComponent
import net.nevinsky.abyssus.ecs.component.RawComponentsComponent
import net.nevinsky.abyssus.ecs.render.AssetResolver

/**
 * Loads the `ecs` block of a scene file into a [SceneEngine]. Components the plugin models are read by their
 * [ComponentCodec]; everything else is carried raw (see [RawComponentsComponent]) so a scene can be written back
 * unchanged. References to ids that are not in the file become [NO_ENTITY].
 */
class SceneEcsLoader(private val resolver: AssetResolver = AssetResolver { _, _ -> null }) {
    fun load(ecs: JsonNode, engine: SceneEngine): SceneEcsDocument {
        val warnings = SceneEcsWarnings()
        val codecs = ComponentCodecs(resolver, warnings)

        ecs.obj("entities")?.fields()?.forEach { (key, node) ->
            val id = key.toIntOrNull()
            if (id == null) {
                warnings.warn("entity id '$key' is not a number; the entity is skipped")
                return@forEach
            }
            val entity = readEntity(id, node, codecs, warnings)
            engine.addEntity(entity)
            engine.ids.register(id, entity)
        }
        resolveReferences(engine, warnings)

        val extras = LinkedHashMap<String, JsonNode>()
        ecs.fields().forEach { (key, node) -> if (key != "entities") extras[key] = node }
        return SceneEcsDocument(extras, warnings.messages)
    }

    private fun readEntity(id: Int, node: JsonNode, codecs: ComponentCodecs, warnings: SceneEcsWarnings): Entity {
        val entity = Entity()
        entity.add(IdComponent(id))
        val raw = RawComponentsComponent(archetype = node.get("archetype"))
        node.obj("components")?.fields()?.forEach { (name, component) ->
            raw.order += name
            val codec = codecs[name]
            if (codec == null) {
                raw.components[name] = component
                warnings.warn("component $name is not modeled and is kept unchanged")
            } else {
                entity.add(codec.read(component) as Component)
            }
        }
        entity.add(raw)
        return entity
    }

    private fun resolveReferences(engine: SceneEngine, warnings: SceneEcsWarnings) {
        fun check(from: Int, kind: String, target: Int): Int {
            if (target == NO_ENTITY || target in engine.ids) return target
            warnings.warn("entity $from: $kind refers to entity $target, which is not in the scene")
            return NO_ENTITY
        }
        for (entity in engine.entities) {
            val from = entity.getComponent(IdComponent::class.java).id
            entity.getComponent(PositionComponent::class.java)?.let { it.lookAtId = check(from, "look-at", it.lookAtId) }
            entity.getComponent(ParentComponent::class.java)?.let { it.parentEntityId = check(from, "parent", it.parentEntityId) }
            entity.getComponent(Point2PointPositionComponent::class.java)?.let {
                it.entity1Id = check(from, "point-to-point entity1", it.entity1Id)
                it.entity2Id = check(from, "point-to-point entity2", it.entity2Id)
            }
        }
    }
}
