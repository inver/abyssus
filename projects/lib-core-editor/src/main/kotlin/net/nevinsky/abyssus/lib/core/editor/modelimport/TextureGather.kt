/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelimport

import net.nevinsky.abyssus.lib.gdx.model.ModelData
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import javax.imageio.ImageIO

/** The textures of an import: [files] by their path in the asset folder, [uris] from texture file name to that path. */
class GatheredTextures(val files: Map<String, ByteArray>, val uris: Map<String, String>, val leftOut: List<LeftOutItem>)

/**
 * Collects the texture files the materials use into `textures/<name>.png`. Each image is read with ImageIO (PNG,
 * JPEG, BMP, GIF; no libGDX natives needed): a PNG is kept byte for byte, any other image is re-encoded as PNG.
 * Images with the same content share one file. A missing file, or one ImageIO cannot read (TGA, DDS, KTX2, WebP...),
 * is reported and left out; the writer then drops that material slot.
 */
class TextureGather {
    fun gather(data: ModelData, cancel: () -> Unit = {}): GatheredTextures {
        val files = LinkedHashMap<String, ByteArray>()
        val uris = LinkedHashMap<String, String>()
        val byContent = HashMap<String, String>()
        val leftOut = ArrayList<LeftOutItem>()
        val names = data.materials.flatMap { it.textures?.toList().orEmpty() }.mapNotNull { it.fileName }.distinct()
        for (name in names) {
            cancel()
            val source = File(name)
            if (!source.isFile) {
                leftOut += LeftOutItem(source.name, LeftOutReason.MISSING)
                continue
            }
            val png = try {
                toPng(source)
            } catch (e: IOException) {
                null
            }
            if (png == null) {
                leftOut += LeftOutItem(source.name, LeftOutReason.UNSUPPORTED_TEXTURE)
                continue
            }
            val path = byContent.getOrPut(sha256(png)) {
                val stem = source.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_.-]+"), "_").trim('_', '.').ifEmpty { "texture" }
                var candidate = "$TEXTURES_DIR/$stem.png"
                var n = 2
                while (files.keys.any { it.equals(candidate, ignoreCase = true) }) candidate = "$TEXTURES_DIR/$stem-${n++}.png"
                files[candidate] = png
                candidate
            }
            uris[name] = path
        }
        return GatheredTextures(files, uris, leftOut)
    }

    /** The image as PNG bytes; `null` when ImageIO cannot read it. */
    private fun toPng(file: File): ByteArray? {
        val bytes = file.readBytes()
        val image = ImageIO.read(bytes.inputStream()) ?: return null
        if (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) return bytes
        val out = ByteArrayOutputStream()
        if (!ImageIO.write(image, "png", out)) return null
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(Locale.ROOT, it) }
}

/** The folder of an imported asset that holds its textures. */
const val TEXTURES_DIR = "textures"

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
