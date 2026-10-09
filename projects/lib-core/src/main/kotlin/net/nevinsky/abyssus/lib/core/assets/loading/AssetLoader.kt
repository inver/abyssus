/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.AssetMeta

/** The built assets of a storage, for a loader to read the ones its asset needs. GL thread only. */
fun interface BuiltAssets {
    /** The built asset of [name]; null when it is not built (or has failed). */
    fun get(name: String): Disposable?
}

/**
 * Loads one asset.
 *
 * `prepare` / `loadPrepared` run off the GL thread.
 * `dependencies`, `upload`, `build`, and disposal of [T] run on the thread that calls
 * [AssetStorage.update].
 *
 * [P] is consumed by `onPrepared` then [discard].
 * [U] is the upload/build value and is released with [discardStaged], not [discard].
 */
interface AssetLoader<P : Any, U : Any, T : Disposable> {
    /** Rehydrates a snapshot. Default rejects metas; storage still uploads and builds the result. */
    fun loadPrepared(meta: AssetMeta<Any>): Prepared<P, U>?

    /** Reads and decodes [name]. Null when it has no usable files. No GL. */
    fun prepare(name: String): Prepared<P, U>?

    /** One GPU-upload slice. True when nothing is left. [AssetStorage.update] calls this at most once. */
    fun upload(staged: U): Boolean = true

    /** Names that must already be built before the first [upload]. Called once, on the GL thread. */
    fun dependencies(staged: U): Set<String> = emptySet()

    /** Creates the GPU object. Must not free [staged]; storage calls [discardStaged] afterwards. */
    fun build(staged: U, assets: BuiltAssets): T

    /** Frees [model]. Invoked once after the `onPrepared` chain, off the GL thread. Idempotent. */
    fun discard(model: P)

    /**
     * Frees [staged]. Invoked once: after `onBuilt` on success, or when the staged value is
     * dropped without a successful build. May run off the GL thread only if upload never started.
     * Must not touch GL. Must not free the object returned from [build].
     */
    fun discardStaged(staged: U) {}
}