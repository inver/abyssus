/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assimp

import org.slf4j.Logger
import net.nevinsky.abyssus.lib.gdx.ModelLogging

import com.badlogic.gdx.files.FileHandle
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.AITexture
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.UncheckedIOException
import javax.imageio.ImageIO

private val log: Logger get() = ModelLogging.logger

/**
 * Resolves texture paths reported by Assimp to files. File textures keep their relative sub folders; embedded
 * textures (paths like `*0`) are written once to the embedded output directory (cache by texture address).
 * Without an output directory embedded textures are skipped.
 */
internal class TextureProcessor(
    private val scene: AIScene,
    private val modelDir: String,
    private val embeddedDir: FileHandle?
) {
    private val extracted: MutableMap<Long, String> = HashMap<Long, String>()

    /** @return the file name for the `ModelTexture`, or `null` if the texture can't be used
     */
    fun resolve(assimpPath: String): String? {
        val relative: String = normalizePath(assimpPath)
        if (relative.isEmpty()) {
            return null
        }
        val embedded = findEmbedded(assimpPath)
        if (embedded != null) {
            return extract(embedded)
        }
        if (relative.startsWith("*")) {
            log.warn("Embedded texture $assimpPath not found in scene")
            return null
        }
        return if (modelDir.isEmpty()) relative else modelDir + "/" + relative
    }

    /** Embedded textures are referenced either by index (`*N`) or by their original file name.  */
    private fun findEmbedded(assimpPath: String): AITexture? {
        val count = scene.mNumTextures()
        if (count == 0) {
            return null
        }
        val textures = scene.mTextures()
        if (assimpPath.startsWith("*")) {
            try {
                val index = assimpPath.substring(1).toInt()
                return if (index >= 0 && index < count) AITexture.create(textures!!.get(index)) else null
            } catch (e: NumberFormatException) {
                return null
            }
        }
        val wanted: String = fileName(assimpPath)
        for (i in 0..<count) {
            val texture = AITexture.create(textures!!.get(i))
            val name = texture.mFilename().dataString()
            if (!name.isEmpty() && fileName(name) == wanted) {
                return texture
            }
        }
        return null
    }

    private fun extract(texture: AITexture): String? {
        if (embeddedDir == null) {
            log.warn("Embedded texture skipped: no output directory given")
            return null
        }
        return extracted.computeIfAbsent(texture.address()) { address: Long? ->
            val name = "embedded_" + extracted.size
            val file = if (texture.mHeight() == 0) writeCompressed(texture, name) else writeRaw(texture, name)
            file.file().getPath().replace('\\', '/')
        }
    }

    private fun writeCompressed(texture: AITexture, name: String?): FileHandle {
        val buffer = texture.pcDataCompressed()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val hint = texture.achFormatHintString()
        val file = embeddedDir!!.child(name + "." + (if (hint.isEmpty()) "png" else hint))
        file.writeBytes(bytes, false)
        return file
    }

    /** Raw textures are ARGB8888 texels; they are stored as PNG.  */
    private fun writeRaw(texture: AITexture, name: String?): FileHandle {
        val width = texture.mWidth()
        val height = texture.mHeight()
        val texels = texture.pcData()
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (i in 0..<width * height) {
            val t = texels.get(i)
            val argb = (t.a().toInt() and 0xff) shl 24 or ((t.r().toInt() and 0xff) shl 16) or ((t.g()
                .toInt() and 0xff) shl 8) or (t.b().toInt() and 0xff)
            image.setRGB(i % width, i / width, argb)
        }
        val file = embeddedDir!!.child(name + ".png")
        embeddedDir.mkdirs()
        try {
            ImageIO.write(image, "png", file.file())
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
        return file
    }

    companion object {
        private fun fileName(path: String): String {
            val p: String = normalizePath(path)
            return p.substring(p.lastIndexOf('/') + 1)
        }

        /** Keeps relative sub folders (e.g. `textures/a.png`) but normalizes separators.  */
        fun normalizePath(path: String): String {
            var p = path.replace('\\', '/')
            while (p.startsWith("./")) {
                p = p.substring(2)
            }
            return p.replace("/{2,}".toRegex(), "/")
        }
    }
}
