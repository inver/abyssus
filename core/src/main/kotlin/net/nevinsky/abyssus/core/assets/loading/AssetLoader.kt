/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.assets.AssetMeta

/**
 * Turns one asset folder into a GPU object, in the steps [AssetCache] runs: [prepare] off the GL thread (file IO,
 * decoding), then [upload] one slice per frame and [build] on the GL thread.
 */
interface AssetLoader<P : Any, T : Disposable> {

    fun loadPrepared(meta: AssetMeta<Any>): P?

    /** Reads and decodes the asset [name]; null when it has no usable files. No GL; runs on a pool thread. */
    fun prepare(name: String): P?

    /** Does one slice of the GPU upload; true when nothing is left. */
    fun upload(prepared: P): Boolean = true

    fun build(prepared: P): T

    /** Releases whatever [prepared] still holds. Safe to call more than once. */
    fun discard(prepared: P)
}
