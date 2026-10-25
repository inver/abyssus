/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.brush.MaskRect
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptDraft
import net.nevinsky.abyssus.plugin.terrain.SculptDrafts
import java.util.IdentityHashMap

/** One terrain a Scene view shows, as the sculpt sync sees it: [key] tells instances apart, [refresh] re-sends [rect]. */
internal interface SculptTarget {
    val key: Any
    val assetName: String
    val data: TerrainData
    fun refresh(rect: MaskRect)
}

/**
 * Brings the terrains of one Scene view in line with the sculpting stroke in progress on them (the project's
 * [SculptDrafts]): when a draft has a new revision the changed rectangle of its heights is copied into the view's own
 * [SculptTarget.data] in place and the target sends those vertices again; when a draft the view had been following is
 * gone, the stroke ended, so the view loads the terrain again through [reload]. GL thread only.
 */
internal class SculptViewSync(private val drafts: SculptDrafts, private val reload: (Set<String>) -> Unit) {
    private class Seen(val draft: SculptDraft, val revision: Int)

    private val seen = IdentityHashMap<Any, Seen>()

    fun apply(targets: Collection<SculptTarget>) {
        val live = IdentityHashMap<Any, Boolean>()
        var reloads: MutableSet<String>? = null
        for (target in targets) {
            val mesh = target.key
            live[mesh] = true
            val name = target.assetName
            val draft = drafts.of(name)
            val prior = seen[mesh]
            when {
                draft != null -> if (prior == null || prior.draft !== draft || prior.revision != draft.revision) {
                    follow(target, draft)
                    seen[mesh] = Seen(draft, draft.revision)
                }
                prior != null -> {
                    seen.remove(mesh)
                    (reloads ?: HashSet<String>().also { reloads = it }) += name
                }
            }
        }
        seen.keys.retainAll(live.keys)
        reloads?.let(reload)
    }

    private fun follow(target: SculptTarget, draft: SculptDraft) {
        val rect = draft.changed
        if (rect.isEmpty || target.data.resolution != draft.resolution) return
        target.data.copyHeights(draft.composed, rect.minX, rect.minZ, rect.maxX, rect.maxZ)
        target.refresh(rect)
    }
}
