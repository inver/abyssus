/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.dto.ENTITY_DISABLED_FLAG
import com.fasterxml.jackson.databind.node.ArrayNode
import net.nevinsky.abyssus.lib.core.format.entityTypeOf
import net.nevinsky.abyssus.lib.core.format.isComponentEnabled
import net.nevinsky.abyssus.lib.core.format.FLAGS_KEY
import net.nevinsky.abyssus.lib.core.format.entityFlags
import net.nevinsky.abyssus.lib.core.format.isEntityEnabled
import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree

/** What an enable or disable edit addresses: a whole entity, or one component entry of it. */
sealed interface EnabledTarget {
    val entityId: String

    data class Entity(override val entityId: String) : EnabledTarget

    /** [kind] is the component's key in the scene, short or fully qualified. */
    data class Component(override val entityId: String, val kind: String) : EnabledTarget
}

/**
 * Enables or disables an entity or a component of a scene tree: disabling a component lists it in the entity's `disabledComponents` (enabling unlists it); an entity is
 * switched by the disabled bit of its `flags`, whose other bits are kept, and nothing else is touched. The Properties check box and the tree eye both run it, through
 * `editSceneJson` or [net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEditing].
 */
class SetEnabledAction {
    /** True when [kind] can be switched: every component except Name, Parent and Position. */
    fun isTogglable(kind: String): Boolean = kind.substringAfterLast('.') !in FIXED

    /** True when the entity entry [entity] can be switched: anything that is not a `GROUP`. */
    fun isTogglable(entity: JsonNode): Boolean =
        entityTypeOf(entity) != "GROUP"

    /** True when the entry is currently enabled (no flag, or `true`). */
    fun isEnabled(root: JsonNode, target: EnabledTarget): Boolean =
        entry(root, target)?.let {
            if (target is EnabledTarget.Entity) isEntityEnabled(it) else isComponentEnabled(entityEntry(root, target), (target as EnabledTarget.Component).kind)
        } ?: true

    /**
     * Sets the flag of [target] in the scene tree [root]. Returns false, with [root] untouched, when the entry is missing,
     * cannot be switched, or already has that state.
     */
    fun apply(root: JsonNode, target: EnabledTarget, enabled: Boolean): Boolean {
        val entry = entry(root, target) as? ObjectNode ?: return false
        when (target) {
            is EnabledTarget.Entity -> if (!isTogglable(entry)) return false
            is EnabledTarget.Component -> if (!isTogglable(target.kind)) return false
        }
        if (target is EnabledTarget.Entity) return setEntityFlag(entry, enabled)
        return setComponentListed(entityEntry(root, target) as? ObjectNode ?: return false, (target as EnabledTarget.Component).kind, enabled)
    }

    /** Lists or unlists [kind] in the entity's `disabledComponents`; the key is inserted for the first name and removed with the last. */
    private fun setComponentListed(entity: ObjectNode, kind: String, enabled: Boolean): Boolean {
        val short = kind.substringAfterLast('.')
        val listed = entity.get(DISABLED_KEY) as? ArrayNode
        val index = listed?.indexOfFirst { it.isTextual && it.textValue().substringAfterLast('.') == short } ?: -1
        if (enabled) {
            if (listed == null || index < 0) return false
            listed.remove(index)
            if (listed.isEmpty) entity.remove(DISABLED_KEY)
        } else {
            if (index >= 0) return false
            (listed ?: entity.putArray(DISABLED_KEY)).add(kind)
        }
        return true
    }

    private fun entityEntry(root: JsonNode, target: EnabledTarget): JsonNode? =
        SceneEntityTree(root).entities()?.get(target.entityId)?.takeIf { it.isObject }

    /** Sets or clears the disabled bit of `flags`, keeping its other bits; `flags` is only inserted for a set bit. */
    private fun setEntityFlag(entry: ObjectNode, enabled: Boolean): Boolean {
        val flags = entityFlags(entry)
        val next = if (enabled) flags and ENTITY_DISABLED_FLAG.inv() else flags or ENTITY_DISABLED_FLAG
        if (next == flags) return false
        if (next == 0) entry.remove(FLAGS_KEY) else entry.put(FLAGS_KEY, next)
        return true
    }

    private fun entry(root: JsonNode, target: EnabledTarget): JsonNode? {
        val tree = SceneEntityTree(root)
        return when (target) {
            is EnabledTarget.Entity -> tree.entities()?.get(target.entityId)?.takeIf { it.isObject }
            is EnabledTarget.Component -> tree.components(target.entityId)?.get(target.kind)?.takeIf { it.isObject }
        }
    }

    private val DISABLED_KEY = "disabledComponents"

    private val FIXED = setOf("NameComponent", "ParentComponent", "PositionComponent")
}
