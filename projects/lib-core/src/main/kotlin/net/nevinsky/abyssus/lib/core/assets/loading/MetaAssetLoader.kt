/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader

/** Reads validated metadata before handing preparation to the asset-specific loader. No GL. */
abstract class MetaAssetLoader<P : Any, U : Any, T : Disposable> internal constructor(
    private val metaLoader: AssetMetaLoader,
) : AssetLoader<P, U, T> {
    final override fun prepare(name: String): Prepared<P, U>? =
        metaLoader.loadBaseMeta(name)?.let(::loadPrepared)
}
