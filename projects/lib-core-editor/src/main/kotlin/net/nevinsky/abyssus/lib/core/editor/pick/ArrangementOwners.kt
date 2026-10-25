/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.arrange.pointsOf
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent

/** The id of the path arrangement that lists [entityId] in its `slots` (a linked copy of it), or null for any other entity. */
fun SceneContent.arrangementOf(entityId: String): String? {
    val id = entityId.toIntOrNull() ?: return null
    return arrangements.firstOrNull { id in it.arrangement.slots }?.entityId
}

/** The ids of the linked copies of the arrangement [arrangementId]; empty when it is none or has no copies. */
fun SceneContent.copiesOf(arrangementId: String): List<String> =
    arrangements.firstOrNull { it.entityId == arrangementId }?.arrangement?.slots.orEmpty().filter { it >= 0 }.map(Int::toString)

/** The control points of the arrangement [id] as shown now: the ones being dragged, else the unapplied draft, else the scene's; null when it is none. */
fun pathPointsIn(c: SceneContent, state: SceneViewState, id: String): List<Vector3>? {
    state.pathEdit?.takeIf { it.entityId == id }?.let { return it.points }
    state.pathDraft?.takeIf { it.entityId == id }?.let { return it.points }
    return c.arrangements.firstOrNull { it.entityId == id }?.arrangement?.let(::pointsOf)
}
