/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree

/** The tree edits that put foliage on a scene's terrain entities and take entities out of a scene. Stateless. */
class FoliageComponentEdits {

    /**
     * Gives the entity [entityId] a `FoliageComponent` naming [foliage], keeping its other members; false when the
     * entity does not exist or already names it.
     */
    fun attach(root: JsonNode, entityId: String, foliage: String): Boolean {
        val components = SceneEntityTree(root).components(entityId) ?: return false
        val component = components["FoliageComponent"] as? ObjectNode ?: components.putObject("FoliageComponent")
        if (component["assetName"]?.textValue() == foliage) return false
        component.put("assetName", foliage)
        return true
    }

    /**
     * Removes the entities [ids] from the scene tree. Nothing changes and the first reference is returned when a
     * remaining entity still refers to a removed one (as its parent, look-at target or point-to-point end).
     */
    fun remove(root: JsonNode, ids: Set<String>): Removal {
        val entities = SceneEntityTree(root).entities() as? ObjectNode ?: return Removal(false, null)
        val present = ids.filter { entities.has(it) }
        if (present.isEmpty()) return Removal(false, null)
        for ((id, entity) in entities.properties()) {
            if (id in present) continue
            val components = entity["components"] ?: continue
            for ((component, member) in REFERENCES) {
                val target = components[component]?.get(member)?.takeIf { it.isIntegralNumber }?.asInt()?.toString() ?: continue
                if (target in present) return Removal(false, "entity $id refers to entity $target through $component.$member")
            }
        }
        present.forEach(entities::remove)
        return Removal(true, null)
    }

    /** What [remove] did: whether the tree changed, and the reference that stopped it, if any. */
    class Removal(val changed: Boolean, val blockedBy: String?)

    private val REFERENCES = listOf(
        "ParentComponent" to "parentEntityId",
        "PositionComponent" to "lookAtId",
    )
}
