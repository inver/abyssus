/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.brush.GridRect
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatDraft
import net.nevinsky.abyssus.plugin.splat.SplatDrafts

/** One terrain a Scene view shows, as the splat sync sees it. */
internal interface SplatTarget {
    /** Tells instances apart. */
    val key: Any

    /** The asset folder of the splat texture, or null when the terrain has none. */
    val splatAsset: String?

    /** Identity of the view's built splat texture, or null while it is not built; a new one means a full upload. */
    val textureKey: Any?

    /** Copies [rect] of [draft]'s weights into the view's own splat texture; false when that cannot be done now. */
    fun upload(draft: SplatDraft, rect: GridRect): Boolean
}

/**
 * Brings the splat textures of one Scene view in line with the painting stroke in progress on them (the project's
 * [SplatDrafts]): when a draft has a new revision the texels that changed since the revision this view last saw are
 * copied into its own texture; a view that first sees a draft mid-stroke (or whose texture was rebuilt) takes the whole
 * map once; when a draft the view had been following is gone, the stroke ended, so the view loads the texture again
 * through [reload]. GL thread only.
 */
internal class SplatViewSync(private val drafts: SplatDrafts, reload: (Set<String>) -> Unit) :
    DraftFollower<SplatTarget, SplatDraft, SplatViewSync.Seen>(reload) {
    class Seen(val draft: SplatDraft, val revision: Int, val texture: Any?, val asset: String)

    override fun keyOf(target: SplatTarget) = target.key
    override fun draftAsset(target: SplatTarget) = target.splatAsset
    override fun draftOf(asset: String) = drafts.of(asset)
    override fun assetOf(state: Seen) = state.asset

    override fun follow(target: SplatTarget, asset: String, draft: SplatDraft, prior: Seen?): Seen? {
        val texture = target.textureKey
        val same = prior != null && prior.draft === draft && prior.texture === texture
        if (same && prior!!.revision == draft.revision) return null
        if (texture == null) return null
        val revision = draft.revision
        val rect = if (same) draft.changedSince(prior!!.revision) else wholeMap(draft)
        return if (rect.isEmpty || target.upload(draft, rect)) Seen(draft, revision, texture, asset) else null
    }

    private fun wholeMap(draft: SplatDraft) = GridRect(0, 0, draft.resolution - 1, draft.resolution - 1)
}
