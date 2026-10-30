/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.loading.AssetLoader

/**
 * Foliage assets for games: the same [FoliageDrawable] as [FoliageLoader] builds, but prepared with the runtime read
 * policy of [FoliageRuntimeLoad] instead of the editor's. The bake is used as written (a stale or unverifiable one keeps
 * its saved copies, copies that cannot be placed are dropped) and nothing is ever generated; a missing, unsupported or
 * unreadable asset fails to load, which a game treats as the foliage being absent. The problems of one read go to
 * [warn] once, from the preparing thread. The terrain and every layer model with a readable meta are declared as
 * dependencies, so the storage builds them first; a missing model drops only its own copies when the drawable is built.
 */
class RuntimeFoliageLoader(
    metaLoader: AssetMetaLoader,
    runtime: FoliageRuntimeLoad,
    warn: (String) -> Unit,
) : AssetLoader<Unit, PreparedFoliage, FoliageDrawable> by FoliageLoader(
    metaLoader, RuntimeFoliageReadPolicy(metaLoader, runtime, warn),
)

internal class RuntimeFoliageReadPolicy(
    private val metaLoader: AssetMetaLoader,
    private val runtime: FoliageRuntimeLoad,
    private val warn: (String) -> Unit,
) : FoliageReadPolicy {
    override fun read(meta: AssetMeta<Any>): PreparedFoliage? {
        val name = meta.name
        val base = metaLoader.loadBaseMeta(name)
        if (base == null || base.type != MetaType.FOLIAGE) return null
        val read = runtime.read(name)
        read.warnings.forEach(warn)
        val foliage = read.foliage ?: throw FoliageUnavailable(read.warnings.firstOrNull() ?: "foliage '$name' cannot be read")
        val declared = foliageDependencies(foliage.meta, metaLoader)
        // Games never regenerate: retain the bake's own fingerprint.
        return PreparedFoliage(foliage.meta, foliage.terrain, emptyMap(), foliage.bake, foliage.bake.fingerprint, declared)
    }
}

/** A foliage asset a game cannot show: missing, unsupported, or without a readable terrain or bake. */
class FoliageUnavailable(message: String) : RuntimeException(message)
