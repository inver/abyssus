package net.nevinsky.abyssus.core.assets.loading

import net.nevinsky.abyssus.assets.AssetMeta
import net.nevinsky.abyssus.assets.sky.RaySkySnapshot

interface RaySnapshotLoader {
    fun load(meta: AssetMeta<Any>): RaySkySnapshot?
}

/**
 * Marker interface for ray snapshots
 */
interface RaySnapshot