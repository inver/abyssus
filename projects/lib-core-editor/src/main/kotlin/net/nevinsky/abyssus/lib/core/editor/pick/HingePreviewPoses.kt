/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.content.Pose
import net.nevinsky.abyssus.lib.core.editor.content.storageName
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import net.nevinsky.abyssus.lib.core.editor.hierarchy.placementOf
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent

/** The rotation of a hinge preview: [degrees] about the X axis of the node's frame, as a matrix. */
fun hingeRotation(degrees: Float): Matrix4 = Matrix4().set(Vector3(), Quaternion(Vector3.X, degrees))

/**
 * Where the entities bound to a previewed node go (OpenSpec `model-part-hinges`, "Deflection preview"): turning the node
 * by `Rx(a)` about its own X axis carries each entity bound to it, and that entity's descendants, along:
 * `world' = M x Rx(a) x M^-1 x world` with `M` the node's world frame. The poses are for the view only.
 */
class HingePreviewPoses {
    fun poses(content: SceneContent, preview: HingePreview, parts: PartsSource): Map<Long, Pose> {
        val model = content.model(preview.entityId) ?: return emptyMap()
        val rest = parts(model.storageName)?.rest?.get(preview.node) ?: return emptyMap()
        val frame = Matrix4(model.transform.toMatrix()).mul(rest)
        val delta = Matrix4(frame).mul(hingeRotation(preview.degrees)).mul(Matrix4(frame).inv())

        val hierarchy = content.hierarchy
        val bound = hierarchy.childrenOf(preview.entityId).filter { hierarchy.nodes[it] == preview.node }
        val moved = LinkedHashSet<Long>()
        for (child in bound) {
            moved += child
            moved += hierarchy.descendantsOf(child)
        }
        return moved.associateWith { id ->
            val pose = placementOf(Matrix4(delta).mul(hierarchy.worldOf(id)))
            Pose(pose.position, pose.rotation)
        }
    }
}
