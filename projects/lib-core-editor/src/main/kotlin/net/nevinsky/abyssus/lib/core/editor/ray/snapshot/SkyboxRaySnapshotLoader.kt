package net.nevinsky.abyssus.lib.core.editor.ray.snapshot

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.sky.cube.SkyboxLoader

class SkyboxRaySnapshotLoader(
    private val skyboxLoader: SkyboxLoader
) : RaySnapshotLoader<RaySkySnapshot, Nothing> {
    // the GL cube map convention: the in-face coordinates (sc, tc) of the major axis, with tc running down the image
    private val mapping = EquirectCubeMapping(
        RAY_SKY_MAX_WIDTH, RAY_SKY_MAX_WIDTH / 2, listOf(
            axes(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f), axes(-1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f),
            axes(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, -1f), axes(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
            axes(0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f), axes(0f, 0f, -1f, -1f, 0f, 0f, 0f, 1f, 0f),
        )
    )

    override fun load(meta: AssetMeta<Any>): RaySkySnapshot {
        val skybox = checkNotNull(skyboxLoader.loadPrepared(meta)).staged
        try {
            return RaySkySnapshot(mapping.width, mapping.height, mapping.resample(skybox.faces.map(::image)), hdr = false)
        } finally {
            skyboxLoader.discardStaged(skybox)
        }
    }

    /** The pixmap as bottom-origin RGBA bytes. */
    private fun image(pixmap: Pixmap): CubeFaceImage {
        val rgba = ByteArray(pixmap.width * pixmap.height * 4)
        for (row in 0 until pixmap.height) for (x in 0 until pixmap.width) {
            val color = pixmap.getPixel(x, pixmap.height - 1 - row)
            val i = (row * pixmap.width + x) * 4
            rgba[i] = (color ushr 24).toByte(); rgba[i + 1] = (color ushr 16).toByte()
            rgba[i + 2] = (color ushr 8).toByte(); rgba[i + 3] = color.toByte()
        }
        return CubeFaceImage(pixmap.width, pixmap.height, rgba)
    }

    private fun axes(dx: Float, dy: Float, dz: Float, rx: Float, ry: Float, rz: Float, vx: Float, vy: Float, vz: Float) =
        CubeFaceAxes(Vector3(dx, dy, dz), Vector3(rx, ry, rz), Vector3(vx, vy, vz))
}
