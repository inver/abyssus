package net.nevinsky.abyssus.core.assets.sky.cube

import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.loading.RaySnapshotLoader
import net.nevinsky.abyssus.core.assets.sky.RAY_SKY_MAX_WIDTH
import net.nevinsky.abyssus.core.assets.sky.RaySkySnapshot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class SkyboxRaySnapshotLoader(
    private val skyboxLoader: SkyboxLoader
) : RaySnapshotLoader<RaySkySnapshot, Nothing> {
    override fun load(meta: AssetMeta<Any>): RaySkySnapshot {
        val skybox = skyboxLoader.loadPrepared(meta) ?: throw IllegalStateException("Skybox is null!")
        return resample(skybox.faces)
    }

    private fun resample(faces: List<Pixmap>): RaySkySnapshot {
        val width = RAY_SKY_MAX_WIDTH
        val height = width / 2
        val out = FloatArray(width * height * 4)
        for (y in 0 until height) {
            val phi = (y + .5f) / height * PI.toFloat()
            for (x in 0 until width) {
                val theta = ((x + .5f) / width - .5f) * 2f * PI.toFloat()
                val dx = sin(phi) * sin(theta);
                val dy = cos(phi);
                val dz = -sin(phi) * cos(theta)
                val ax = abs(dx);
                val ay = abs(dy);
                val az = abs(dz)
                // the GL cube map face selection: major axis, then the in-face coordinates (sc, tc) over |ma|
                val face: Int;
                val sc: Float;
                val tc: Float;
                val ma: Float
                if (ax >= ay && ax >= az) {
                    ma = ax; if (dx > 0) {
                        face = 0; sc = -dz; tc = -dy
                    } else {
                        face = 1; sc = dz; tc = -dy
                    }
                } else if (ay >= az) {
                    ma = ay; if (dy > 0) {
                        face = 2; sc = dx; tc = dz
                    } else {
                        face = 3; sc = dx; tc = -dz
                    }
                } else {
                    ma = az; if (dz > 0) {
                        face = 4; sc = dx; tc = -dy
                    } else {
                        face = 5; sc = -dx; tc = -dy
                    }
                }
                val pixmap = faces[face]
                val px = ((sc / ma + 1f) / 2f * pixmap.width).toInt().coerceIn(0, pixmap.width - 1)
                val py = ((tc / ma + 1f) / 2f * pixmap.height).toInt().coerceIn(0, pixmap.height - 1)
                val rgba = pixmap.getPixel(px, py)
                val i = (y * width + x) * 4
                out[i] = ((rgba ushr 24) and 255) / 255f; out[i + 1] = ((rgba ushr 16) and 255) / 255f
                out[i + 2] = ((rgba ushr 8) and 255) / 255f; out[i + 3] = 1f
            }
        }
        return RaySkySnapshot(width, height, out, hdr = false)
    }
}