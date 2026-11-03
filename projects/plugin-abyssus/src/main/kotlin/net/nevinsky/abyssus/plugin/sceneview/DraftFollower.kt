/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import java.util.IdentityHashMap

/**
 * The skeleton of a Scene view following the brush strokes in progress on its terrains: for each target it finds the
 * draft of the target's asset and lets [follow] copy what changed; when a draft the view had been following is gone, the
 * stroke ended, so the asset is handed to [reload] once per [apply]. [S] is what a subclass remembers of a target between
 * calls (it names the [assetOf] it followed). GL thread only.
 */
internal abstract class DraftFollower<T : Any, D : Any, S : Any>(private val reload: (Set<String>) -> Unit) {
    private val seen = IdentityHashMap<Any, S>()

    /** Tells the instances of a target apart. */
    protected abstract fun keyOf(target: T): Any

    /** The asset folder whose draft [target] follows, or null when it has none. */
    protected abstract fun draftAsset(target: T): String?

    protected abstract fun draftOf(asset: String): D?

    /** The asset folder [state] followed, for the reload when its draft is gone. */
    protected abstract fun assetOf(state: S): String

    /** Brings [target] in line with [draft]; the state to remember, or null to remember nothing new. */
    protected abstract fun follow(target: T, asset: String, draft: D, prior: S?): S?

    fun apply(targets: Collection<T>) {
        val live = IdentityHashMap<Any, Boolean>()
        var reloads: MutableSet<String>? = null
        for (target in targets) {
            val key = keyOf(target)
            live[key] = true
            val asset = draftAsset(target)
            val draft = asset?.let(::draftOf)
            val prior = seen[key]
            when {
                draft != null && asset != null -> follow(target, asset, draft, prior)?.let { seen[key] = it }
                prior != null -> {
                    seen.remove(key)
                    (reloads ?: HashSet<String>().also { reloads = it }) += assetOf(prior)
                }
            }
        }
        seen.keys.retainAll(live.keys)
        reloads?.let(reload)
    }
}
