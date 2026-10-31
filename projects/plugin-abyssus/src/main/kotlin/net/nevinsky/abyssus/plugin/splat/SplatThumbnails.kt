/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.splat

import com.intellij.util.concurrency.AppExecutorUtil
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.SwingUtilities

private const val THUMBNAIL_SIDE = 24

/**
 * The small icons of the layer slots: a texture's image scaled to [THUMBNAIL_SIDE] pixels, decoded off the AWT thread and
 * kept by file path, valid while the file has the length and modification time it had. [scale] is the pure part.
 */
class SplatThumbnails(private val decode: (File) -> Icon? = { file -> scale(file)?.let(::ImageIcon) }) {
    private class Entry(val length: Long, val modified: Long, val icon: Icon?)

    private val cache = ConcurrentHashMap<String, Entry>()

    /** The icon kept for [file] if it is still current, else null. */
    fun cached(file: File): Icon? =
        cache[file.path]?.takeIf { it.length == file.length() && it.modified == file.lastModified() }?.icon

    /** Decodes [file] on a pooled thread unless a current icon is kept, and calls [done] with the icon on the AWT thread. */
    fun load(file: File, done: (Icon?) -> Unit) {
        cached(file)?.let { done(it); return }
        AppExecutorUtil.getAppExecutorService().execute {
            val length = file.length()
            val modified = file.lastModified()
            val icon = runCatchingKeepingCancellation { decode(file) }.getOrNull()
            cache[file.path] = Entry(length, modified, icon)
            SwingUtilities.invokeLater { done(icon) }
        }
    }
}

/** [file] scaled to fit [THUMBNAIL_SIDE] pixels, or null when it cannot be read as an image. */
fun scale(file: File): Image? {
    val image: BufferedImage = ImageIO.read(file) ?: return null
    return image.getScaledInstance(THUMBNAIL_SIDE, THUMBNAIL_SIDE, Image.SCALE_SMOOTH)
}
