package net.nevinsky.abyssus.lib.core.editor.ray.snapshot

import net.nevinsky.abyssus.lib.core.assets.AssetMeta

/** A procedural sky is drawn by its own GLSL and has no CPU image, so ray mode gets no sky snapshot for it. */
class ProceduralSkyRaySnapshotLoader : RaySnapshotLoader<RaySkySnapshot, Nothing> {
    override fun load(meta: AssetMeta<Any>): RaySkySnapshot? = null
}
