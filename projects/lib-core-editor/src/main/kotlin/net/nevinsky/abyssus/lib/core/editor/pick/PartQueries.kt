/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.content.storageName
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import net.nevinsky.abyssus.lib.core.editor.hierarchy.placementOf
import net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelPartsInfo
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent

/** The parts a model asset has, by the name the asset storage knows its placement under; null while not read. */
typealias PartsSource = (storageName: String) -> ModelPartsInfo?

/** The world matrix of node [node] of model entity [entityId] at rest (pivot applied), or null when unknown. */
fun partWorldOf(content: SceneContent, entityId: Long, node: String, parts: PartsSource): Matrix4? {
    val placement = content.model(entityId) ?: return null
    val rest = parts(placement.storageName)?.rest?.get(node)
        ?: return null
    return Matrix4(placement.transform.toMatrix()).mul(rest)
}

/**
 * The frame the gizmo drags in Edit Pivot mode: the selected part's pivot frame in world space (or the frame being
 * dragged), without scale; null when Edit Pivot is off, no part is selected or its pivot cannot be applied.
 */
fun pivotFrameOf(content: SceneContent, state: SceneViewState, parts: PartsSource): PlacementTransform? {
    if (!state.editPivot) return null
    val id = state.selectedId ?: return null
    val node = state.selectedPart ?: return null
    val placement = content.model(id) ?: return null
    val info = parts(placement.storageName) ?: return null
    if (info.find(node)?.pivotRefusal != null) return null
    val frame = state.pivotPreview ?: partWorldOf(content, id, node, parts) ?: return null
    val pose = placementOf(frame)
    return PlacementTransform(pose.position, pose.rotation, Vector3(1f, 1f, 1f))
}
