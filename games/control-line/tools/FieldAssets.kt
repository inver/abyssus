/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("FieldAssets")

package net.nevinsky.abyssus.games.controlline.tools

import net.nevinsky.abyssus.assets.terrain.generation.TerrainGenerationSettings
import net.nevinsky.abyssus.assets.terrain.generation.TerrainGenerator
import net.nevinsky.abyssus.assets.terrain.generation.TerrainHeightEncoder
import net.nevinsky.abyssus.assets.terrain.noise.FastNoiseSamplerFactory
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The field is [FIELD_SIZE] metres square, one height per metre. */
const val FIELD_SIZE = 200
const val FIELD_RESOLUTION = 201

/** Inside this radius around the field's centre the ground is flat at [FLAT_HEIGHT]; it blends into the hills by [BLEND_RADIUS]. */
const val FLAT_RADIUS = 26f
const val BLEND_RADIUS = 40f
const val FLAT_HEIGHT = 1f

/**
 * Writes the field's terrain (`terrain_field`: rolling hills with a flat circle in the middle) and its textures
 * (`texture_grass`, `texture_mown` and the splat map `texture_field_splat`, which mows the circle) under
 * `<project>/assets`. Seeded and with a fixed `lastModified`, so running it again gives the same bytes. Run by
 * `./gradlew :games:control-line:generateField`.
 */
fun main(args: Array<String>) {
    require(args.size == 1) { "usage: FieldAssets <project folder>" }
    val assets = Path.of(args[0]).resolve("assets")
    val grass = texture(assets, "texture_grass", "grass.png", noiseImage(256, 7, intArrayOf(62, 112, 40), 22))
    val mown = texture(assets, "texture_mown", "mown.png", noiseImage(256, 11, intArrayOf(98, 148, 62), 14))
    val splat = texture(assets, "texture_field_splat", "splat.png", splatImage(256))

    val heights = TerrainGenerator(FastNoiseSamplerFactory()).generate(
        FIELD_RESOLUTION, FIELD_SIZE,
        TerrainGenerationSettings(seed = 2026, featureSize = 70f, minHeight = 0f, maxHeight = 5f, octaves = 4),
    )
    val centre = FIELD_SIZE / 2f
    val cell = FIELD_SIZE.toFloat() / (FIELD_RESOLUTION - 1)
    for (z in 0 until FIELD_RESOLUTION) for (x in 0 until FIELD_RESOLUTION) {
        val r = hypot(x * cell - centre, z * cell - centre)
        val i = z * FIELD_RESOLUTION + x
        heights[i] = when {
            r <= FLAT_RADIUS -> FLAT_HEIGHT
            r >= BLEND_RADIUS -> heights[i]
            else -> {
                val t = (r - FLAT_RADIUS) / (BLEND_RADIUS - FLAT_RADIUS)
                val s = t * t * (3 - 2 * t)
                FLAT_HEIGHT + (heights[i] - FLAT_HEIGHT) * s
            }
        }
    }
    val dir = assets.resolve("terrain_field")
    Files.createDirectories(dir)
    Files.write(dir.resolve("terrain.data"), TerrainHeightEncoder().encode(heights))
    Files.writeString(dir.resolve("meta.json"),
        """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":$GENERATED_AT,"uuid":"${uuid("terrain_field")}",""" +
            """"type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":$FIELD_SIZE,"uv":40.0,"splatMap":"$splat",""" +
            """"splatBase":"$grass","splatR":"$mown","splatG":null,"splatB":null,"splatA":null}}""")
}

private fun uuid(name: String) = UUID.nameUUIDFromBytes("abyssus-control-line/$name".toByteArray()).toString()

/** Writes a texture asset folder; returns its uuid. */
private fun texture(assets: Path, name: String, file: String, image: BufferedImage): String {
    val dir = assets.resolve(name)
    Files.createDirectories(dir)
    ImageIO.write(image, "png", dir.resolve(file).toFile())
    val id = uuid(name)
    Files.writeString(dir.resolve("meta.json"),
        """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":$GENERATED_AT,"uuid":"$id","type":"TEXTURE",""" +
            """"additional":{"file":"$file"}}""")
    return id
}

/** A tileable-enough grass: [base] colour with seeded per-pixel noise of [spread]. */
private fun noiseImage(size: Int, seed: Long, base: IntArray, spread: Int): BufferedImage {
    val random = Random(seed)
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until size) for (x in 0 until size) {
        val n = random.nextGaussian() * spread / 2
        val (r, g, b) = base.map { (it + n).roundToInt().coerceIn(0, 255) }
        image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
    }
    return image
}

/** Red over the flat circle (the mown grass), fading out over two metres at its edge. */
private fun splatImage(size: Int): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    val metres = FIELD_SIZE.toFloat() / size
    for (y in 0 until size) for (x in 0 until size) {
        val r = hypot((x + 0.5f) * metres - FIELD_SIZE / 2f, (y + 0.5f) * metres - FIELD_SIZE / 2f)
        val red = ((FLAT_RADIUS - 1f - r) / 2f).coerceIn(0f, 1f)
        image.setRGB(x, y, (red * 255).roundToInt() shl 16)
    }
    return image
}
