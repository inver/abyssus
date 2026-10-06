package net.nevinsky.abyssus.core.assets.sky.hdr

import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.loading.RaySnapshotLoader
import net.nevinsky.abyssus.core.assets.sky.RAY_SKY_MAX_WIDTH
import net.nevinsky.abyssus.core.assets.sky.RaySkySnapshot

class HdrSkyRaySnapshotLoader(
    private val hdrSkyLoader: HdrSkyLoader
) : RaySnapshotLoader<RaySkySnapshot, Nothing> {
    override fun load(meta: AssetMeta<Any>): RaySkySnapshot {
        val prepared = hdrSkyLoader.loadPrepared(meta)
        return downsample(prepared.image)
    }

    /** A box filter over whole source pixels, so a 4096 image does not alias into the ray texture. */
    internal fun downsample(image: HdrImage): RaySkySnapshot {
        val factor = (image.width + RAY_SKY_MAX_WIDTH - 1) / RAY_SKY_MAX_WIDTH
        val width = image.width / factor
        val height = maxOf(image.height / factor, 1)
        val out = FloatArray(width * height * 4)
        val area = (factor * factor).toFloat()
        for (y in 0 until height) for (x in 0 until width) {
            var r = 0f;
            var g = 0f;
            var b = 0f
            for (dy in 0 until factor) for (dx in 0 until factor) {
                val p = image.pixel(minOf(x * factor + dx, image.width - 1), minOf(y * factor + dy, image.height - 1))
                r += p[0]; g += p[1]; b += p[2]
            }
            val i = (y * width + x) * 4
            out[i] = r / area; out[i + 1] = g / area; out[i + 2] = b / area; out[i + 3] = 1f
        }
        return RaySkySnapshot(width, height, out, hdr = true)
    }
}