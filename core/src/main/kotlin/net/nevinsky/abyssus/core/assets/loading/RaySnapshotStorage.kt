package net.nevinsky.abyssus.core.assets.loading

import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.sky.RaySkySnapshot

class RaySnapshotStore {
}

interface RaySnapshotLoader {
    fun load(meta: AssetMeta<Any>): RaySkySnapshot?
}

/**
 * Marker interface for ray snapshots
 */
interface RaySnapshot