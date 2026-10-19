/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.format.ENABLED_KEY
import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree

/** What an enable or disable edit addresses: a whole entity, or one component entry of it. */
sealed interface EnabledTarget {
    val entityId: String

    data class Entity(override val entityId: String) : EnabledTarget

    /** [kind] is the component's key in the scene, short or fully qualified. */
    data class Component(override val entityId: String, val kind: String) : EnabledTarget
}

/**
 * Enables or disables an entity or a component of a scene tree: disabling writes `enabled: false` on that entry only,
 * enabling removes the key, and nothing else is touched. The Properties check box and the tree eye both run it, through
 * `editSceneJson` or [net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEditing].
 */
class SetEnabledAction {
    /** True when [kind] can be switched: every component except Name, Parent and Position. */
    fun isTogglable(kind: String): Boolean = kind.substringAfterLast('.') !in FIXED

    /** True when the entity entry [entity] can be switched: anything that is not a `GROUP`. */
    fun isTogglable(entity: JsonNode): Boolean =
        entity.get("components")?.get("TypeComponent")?.get("type")?.takeIf { it.isTextual }?.textValue() != "GROUP"

    /** True when the entry is currently enabled (no flag, or `true`). */
    fun isEnabled(root: JsonNode, target: EnabledTarget): Boolean =
        entry(root, target)?.let { net.nevinsky.abyssus.lib.core.format.isEnabled(it) } ?: true

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
        val current = entry.get(ENABLED_KEY)
        if (enabled) {
            if (current == null) return false
            entry.remove(ENABLED_KEY)
        } else {
            if (current != null && current.isBoolean && !current.booleanValue()) return false
            entry.put(ENABLED_KEY, false)
        }
        return true
    }

    private fun entry(root: JsonNode, target: EnabledTarget): JsonNode? {
        val tree = SceneEntityTree(root)
        return when (target) {
            is EnabledTarget.Entity -> tree.entities()?.get(target.entityId)?.takeIf { it.isObject }
            is EnabledTarget.Component -> tree.components(target.entityId)?.get(target.kind)?.takeIf { it.isObject }
        }
    }

    private val FIXED = setOf("NameComponent", "ParentComponent", "PositionComponent")
}
