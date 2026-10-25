/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.content.Quat
import net.nevinsky.abyssus.lib.core.editor.pick.PathEdit
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent

/**
 * Shows the copies of an arrangement along points that are not written yet: the scene's own copies are replaced by ones
 * laid out as [ArrangementEdits.regenerate] would, so the view matches what Apply writes (which passes the line's own
 * heights, not the terrain's). Nothing is written.
 */
class ArrangementPreview {
    /** [content] with the copies of the arrangement of [draft] placed along the draft points on [heights]. */
    fun apply(content: SceneContent, draft: PathEdit, heights: GroundHeight): SceneContent {
        val placement = content.arrangements.firstOrNull { it.entityId == draft.entityId } ?: return content
        val along = arrangementAlong(draft.points, placement.arrangement)
        val slots = PathLayout().slots(along) ?: return content
        val old = placement.arrangement.slots
        val oldIds = old.filter { it >= 0 }.map(Int::toString).toSet()
        val existing = content.models.filter { it.entityId in oldIds }.associateBy { it.entityId }
        val copies = slots.mapIndexed { k, slot ->
            val id = old.getOrNull(k)?.takeIf { it >= 0 }?.toString()
            val scale = id?.let { existing[it]?.transform?.scale } ?: Vector3(1f, 1f, 1f)
            val q = Quaternion().setFromAxis(0f, 1f, 0f, slot.yawDegrees)
                .let { if (it.w < 0f) it.set(-it.x, -it.y, -it.z, -it.w) else it }
            AssetPlacement(
                id ?: "preview-${draft.entityId}-$k",
                along.asset.assetName,
                PlacementTransform(
                    Vector3(slot.x, heights.heightAt(slot.x, slot.z, slot.fallbackY), slot.z),
                    Quat(q.x, q.y, q.z, q.w),
                    scale
                ),
            )
        }
        return content.copy(models = content.models.filter { it.entityId !in oldIds } + copies)
    }
}
