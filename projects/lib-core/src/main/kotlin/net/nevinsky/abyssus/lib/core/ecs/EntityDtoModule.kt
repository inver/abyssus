/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import com.badlogic.ashley.core.Entity
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.format.entityDisabledComponents
import net.nevinsky.abyssus.lib.core.format.entityTypeOf

/**
 * Teaches a Jackson mapper to bind an `ecs` entry to an [Entity]: the entry's `type`, `flags` and `disabledComponents`
 * are
 * its [HeaderComponent] (with the entry's id, its key in the `ecs` map), and each member of `components` is keyed by
 * the class name of a component [registry] knows (fully qualified
 * or short) and bound into that class, with no per-component codec. A component named in `disabledComponents` is
 * recorded
 * as disabled. A name nobody registered and a value Jackson cannot bind become a [RawComponent] that keeps the payload
 * as
 * text, so the scene can be written back unchanged; an entity holds one component per class, so these share one
 * [RawComponentsWrapper]. A scene is project data, so a class name in it never loads an
 * arbitrary class.
 */
class EntityDtoModule(registry: ComponentRegistry) : SimpleModule("AbyssusEntity") {
    init {
        addDeserializer(EntityDto::class.java, EntityDeserializer(registry))
    }
}

private const val FLAGS_KEY = "flags"

private const val COMPONENTS = "components"

const val ENTITY_DISABLED_FLAG = 1


private class EntityDeserializer(private val registry: ComponentRegistry) :
    StdDeserializer<EntityDto>(Entity::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EntityDto {
        // the entry's key in the `ecs` map; read before the entry is consumed
        val id = p.parsingContext.parent?.currentName?.toLongOrNull() ?: -1L
        val node = ctxt.readTree(p)

        val disabledNames = entityDisabledComponents(node)
        val disabledClasses = disabledNames.mapNotNull { registry.get(it) }.toMutableSet()
        val flags = node.get(FLAGS_KEY)?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue() ?: 0

        val entity = EntityDto()
        val raw = RawComponentsWrapper()
        entity.add(
            HeaderComponent(
                id = id,
                type = entityTypeOf(node)?.let { EntityType.entries.firstOrNull { et -> et.name == it } },
                entityEnabled = flags and ENTITY_DISABLED_FLAG == 0,
                disabledComponents = LinkedHashSet(disabledClasses),
                flags = flags and ENTITY_DISABLED_FLAG.inv(),
            )
        )

        node.get(COMPONENTS)?.takeIf { it.isObject }?.properties()?.forEach { (name, value) ->
            val clazz = registry.get(name)
            val off = name in disabledNames || clazz in disabledClasses
            if (clazz == null) {
                raw.entries.add(
                    RawComponent(
                        name,
                        value.toString(),
                        "component $name is not modeled and is kept unchanged",
                        off
                    )
                )
                return@forEach
            }
            val component = try {
                ctxt.readTreeAsValue(value, clazz)
            } catch (e: Exception) {
                raw.entries.add(
                    RawComponent(
                        name,
                        value.toString(),
                        "component $name could not be read (${
                            e.message?.lineSequence()?.first()
                        }) and is kept unchanged",
                        off,
                    )
                )
                return@forEach
            }
            entity.add(component)
        }
        if (raw.entries.isNotEmpty()) {
            entity.add(raw)
        }
        return entity
    }
}
