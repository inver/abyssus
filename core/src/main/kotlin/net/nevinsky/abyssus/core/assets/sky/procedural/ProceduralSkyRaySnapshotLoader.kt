package net.nevinsky.abyssus.core.assets.sky.procedural

import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.loading.RaySnapshotLoader
import net.nevinsky.abyssus.core.assets.sky.RaySkySnapshot

class ProceduralSkyRaySnapshotLoader : RaySnapshotLoader {
    override fun load(meta: AssetMeta<Any>): RaySkySnapshot? {
        return null
    }
}