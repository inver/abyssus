package net.nevinsky.abyssus.plugin.ui

import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.gdx.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrPreview
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/** The file [fileName] in [folder], or null when the folder is gone or holds no such file (a directory does not count). */
private fun imageFile(folder: VirtualFile, fileName: String): VirtualFile? =
    folder.takeIf { it.isValid }?.findChild(fileName)?.takeIf { it.isValid && !it.isDirectory }

internal fun thumbnail(
    folder: VirtualFile,
    fileName: String,
    maxWidth: Int = 320,
    maxHeight: Int = 144
): BufferedImage? = runCatchingKeepingCancellation {
    val file = imageFile(folder, fileName) ?: return@runCatchingKeepingCancellation null
    val source =
        ImageIO.read(ByteArrayInputStream(file.contentsToByteArray())) ?: return@runCatchingKeepingCancellation null
    scaled(source, maxWidth, maxHeight)
}.getOrNull()

private fun scaled(source: BufferedImage, maxWidth: Int, maxHeight: Int): BufferedImage {
    val ratio = minOf(maxWidth.toDouble() / source.width, maxHeight.toDouble() / source.height, 1.0)
    val w = maxOf(1, (source.width * ratio).toInt())
    val h = maxOf(1, (source.height * ratio).toInt())
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    out.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        drawImage(source, 0, 0, w, h, null)
        dispose()
    }
    return out
}

/** A tone-mapped thumbnail at most [width] wide of the OpenEXR image [fileName] in [folder], or null when it is absent or unreadable. Off the EDT. */
fun hdrThumbnail(folder: VirtualFile, fileName: String, width: Int, preview: HdrPreview): BufferedImage? =
    runCatchingKeepingCancellation {
        val file = imageFile(folder, fileName) ?: return@runCatchingKeepingCancellation null
        preview.image(File(file.path), width)
    }.getOrNull()

/** A small square-bounded thumbnail of the image [fileName] in [folder], or null when it is absent or cannot be decoded. Safe off the EDT. */
fun smallThumbnail(folder: VirtualFile, fileName: String, size: Int): BufferedImage? =
    thumbnail(folder, fileName, size, size)

