/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.assets.sky

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.files.MetaType
import net.nevinsky.abyssus.assets.sky.cube.SkyboxMeta
import net.nevinsky.abyssus.assets.sky.hdr.HdrChoice
import net.nevinsky.abyssus.assets.sky.hdr.HdrImage
import net.nevinsky.abyssus.assets.sky.hdr.HdrSkyFiles
import net.nevinsky.abyssus.assets.sky.hdr.HdrSkyMeta
import net.nevinsky.abyssus.assets.sky.hdr.RadianceDecoder
import net.nevinsky.abyssus.assets.sky.procedural.ProceduralSkyMeta
import net.nevinsky.abyssus.core.loader.Pixmaps
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reads a sky asset into a [RaySkySnapshot] off the GL thread: an HDR image decoded again on the CPU, or the six faces
 * of a cube skybox resampled with the GL cube lookup the raster sky uses. A procedural sky is the asset's own GLSL and
 * has no CPU form, so it reads as null (the caller falls back to the background colour). No GL calls.
 */
class RaySkySnapshotReader(private val decoder: RadianceDecoder, private val hdrFiles: HdrSkyFiles) {
    /** Null for a missing asset or a procedural sky; throws, with the reason, for a sky that cannot be decoded. */
    fun read(files: AssetFiles, name: String): RaySkySnapshot? {
        val asset = files.loadAsset(ProceduralSkyMeta::class.java, name) ?: return null
        return when (asset.meta.type) {
            MetaType.SKYBOX_PROCEDURAL -> null
            MetaType.SKYBOX_HDR -> hdr(files, name)
            else -> cube(files, name)
        }
    }

    private fun hdr(files: AssetFiles, name: String): RaySkySnapshot? {
        val asset = files.loadAsset(HdrSkyMeta::class.java, name) ?: return null
        val dir = asset.baseDir
        val named = asset.meta.additional.orEmpty().values.filterIsInstance<String>()
        val choice: HdrChoice = hdrFiles.choose(dir.list()?.toList().orEmpty(), named)
            ?: throw IllegalStateException("HDR sky '$name' has no .hdr file")
        val file =
            files.file(dir, choice.file) ?: throw IllegalStateException("HDR sky '$name': cannot read '${choice.file}'")
        return downsample(decoder.read(file))
    }

    /** A box filter over whole source pixels, so a 4096 image does not alias into the ray texture. */
    private fun downsample(image: HdrImage): RaySkySnapshot {
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

    /** Faces are (back, front, left, right, bottom, top) = (+X, -X, +Y, -Y, +Z, -Z), as the raster cube loader orders them. */
    private fun cube(files: AssetFiles, name: String): RaySkySnapshot? {
        val additional = files.loadAsset(SkyboxMeta::class.java, name)?.meta?.additional ?: return null
        val faces = ArrayList<Pixmap>(6)
        try {
            for (face in listOf(
                additional.back,
                additional.front,
                additional.left,
                additional.right,
                additional.bottom,
                additional.top
            )) {
                val file = files.loadFile(name, face)
                    ?: throw IllegalStateException("Skybox '$name': cannot read face '$face'")
                faces += Pixmaps.load(FileHandle(file))
            }
            return resample(faces)
        } finally {
            faces.forEach(Pixmap::dispose)
        }
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
