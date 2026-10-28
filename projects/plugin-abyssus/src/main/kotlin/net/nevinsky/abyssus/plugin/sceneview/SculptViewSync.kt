/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.brush.GridRect
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptDraft
import net.nevinsky.abyssus.plugin.terrain.SculptDrafts

/** One terrain a Scene view shows, as the sculpt sync sees it: [key] tells instances apart, [refresh] re-sends [rect]. */
internal interface SculptTarget {
    val key: Any
    val assetName: String
    val data: TerrainData
    fun refresh(rect: GridRect)
}

/**
 * Brings the terrains of one Scene view in line with the sculpting stroke in progress on them (the project's
 * [SculptDrafts]): when a draft has a new revision the changed rectangle of its heights is copied into the view's own
 * [SculptTarget.data] in place and the target sends those vertices again; when a draft the view had been following is
 * gone, the stroke ended, so the view loads the terrain again through [reload]. GL thread only.
 */
internal class SculptViewSync(private val drafts: SculptDrafts, reload: (Set<String>) -> Unit) :
    DraftFollower<SculptTarget, SculptDraft, SculptViewSync.Seen>(reload) {
    class Seen(val draft: SculptDraft, val revision: Int, val asset: String)

    override fun keyOf(target: SculptTarget) = target.key
    override fun draftAsset(target: SculptTarget) = target.assetName
    override fun draftOf(asset: String) = drafts.of(asset)
    override fun assetOf(state: Seen) = state.asset

    override fun follow(target: SculptTarget, asset: String, draft: SculptDraft, prior: Seen?): Seen? {
        if (prior != null && prior.draft === draft && prior.revision == draft.revision) return null
        val rect = draft.changed
        if (!rect.isEmpty && target.data.resolution == draft.resolution) {
            target.data.copyHeights(draft.composed, rect.minX, rect.minZ, rect.maxX, rect.maxZ)
            target.refresh(rect)
        }
        return Seen(draft, draft.revision, asset)
    }
}
