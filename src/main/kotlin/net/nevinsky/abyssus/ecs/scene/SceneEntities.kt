/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.ecs.scene

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.editor.document.SceneEntityTree

/**
 * Inserts new entities into a scene's JSON tree, in either layout (see [SceneEntityTree]). Edits only the tree; callers
 * write it through `editSceneJson`.
 */
object SceneEntities {
    /** Whether an entity can be added to [root]: a native scene whose `ecs`, when present, is an object. */
    fun canAdd(root: JsonNode): Boolean = SceneEntityTree(root).canAdd()

    /**
     * Adds an entity holding the components [build] makes for its id (one above the highest numeric entity id); in a
     * wrapped layout it gets the archetype listing exactly those components, reusing a matching one. Null when [root]
     * cannot take an entity.
     */
    fun insert(root: JsonNode, build: (id: String) -> ObjectNode): String? = SceneEntityTree(root).insert(build)

    /**
     * Points entity [id] of a wrapped layout at the archetype listing exactly its components, adding one when none
     * matches. Does nothing (and is true) in a native layout. False when the entity or the table cannot be read.
     */
    fun matchArchetype(root: JsonNode, id: String): Boolean = SceneEntityTree(root).matchArchetype(id)
}
