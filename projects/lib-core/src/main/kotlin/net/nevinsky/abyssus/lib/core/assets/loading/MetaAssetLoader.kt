/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.loading

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.baseAssetName
import net.nevinsky.abyssus.lib.core.assets.forVariant
import net.nevinsky.abyssus.lib.core.assets.isModelVariant

/** Reads validated metadata before handing preparation to the asset-specific loader. No GL. */
abstract class MetaAssetLoader<P : Any, U : Any, T : Disposable> internal constructor(
    private val metaLoader: AssetMetaLoader,
) : AssetLoader<P, U, T> {
    final override fun prepare(name: String): Prepared<P, U>? =
        metaLoader.loadBaseMeta(baseAssetName(name))?.let { base ->
            loadPrepared(if (isModelVariant(name)) base.forVariant(name) else base)
        }
}

/** A [MetaAssetLoader] with no prepare-time parameter: [read] builds the staged value from the validated metadata. No GL. */
abstract class StagedMetaAssetLoader<U : Any, T : Disposable> internal constructor(
    metaLoader: AssetMetaLoader,
) : MetaAssetLoader<Unit, U, T>(metaLoader) {
    protected abstract fun read(meta: AssetMeta<Any>): U

    final override fun loadPrepared(meta: AssetMeta<Any>): Prepared<Unit, U> = Prepared(read(meta))
}
