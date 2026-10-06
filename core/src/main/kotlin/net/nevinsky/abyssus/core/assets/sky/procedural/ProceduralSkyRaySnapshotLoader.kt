package net.nevinsky.abyssus.core.assets.sky.procedural

import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.loading.RaySnapshotLoader
import net.nevinsky.abyssus.core.assets.sky.RaySkySnapshot

/** A procedural sky is drawn by its own GLSL and has no CPU image, so ray mode gets no sky snapshot for it. */
class ProceduralSkyRaySnapshotLoader : RaySnapshotLoader<RaySkySnapshot, Nothing> {
    override fun load(meta: AssetMeta<Any>): RaySkySnapshot? = null
}
