/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Edits of the project file's `uiAssetNames` registry on its parsed tree. Both return whether the tree changed, so they
 * fit the mutate callback of a document write; a missing list is created only by [register], an emptied one is removed
 * so an unused registry leaves no trace, and entries that are not strings are never touched.
 */
class UiRegistryEdits {
    fun register(project: JsonNode, name: String): Boolean {
        val root = project as? ObjectNode ?: return false
        if (name.isBlank()) return false
        val existing = root.get("uiAssetNames")
        if (existing != null && existing !is ArrayNode) return false // malformed lists are left for the author to fix
        val list = existing as? ArrayNode ?: root.putArray("uiAssetNames")
        if (list.any { it.isTextual && it.textValue() == name }) return false
        list.add(name)
        return true
    }

    fun unregister(project: JsonNode, name: String): Boolean {
        val root = project as? ObjectNode ?: return false
        val list = root.get("uiAssetNames") as? ArrayNode ?: return false
        val at = list.indexOfFirst { it.isTextual && it.textValue() == name }
        if (at < 0) return false
        list.remove(at)
        if (list.isEmpty) root.remove("uiAssetNames")
        return true
    }
}
