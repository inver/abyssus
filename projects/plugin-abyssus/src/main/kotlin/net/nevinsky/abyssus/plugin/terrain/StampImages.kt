/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.terrain

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaReader
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.StampImage
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.util.obj
import net.nevinsky.abyssus.lib.core.util.text
import java.io.File

/** A project image asset a Stamp can use: its folder [name] and the image [file] its `meta.json` names. */
data class StampImageChoice(val name: String, val file: File) {
    override fun toString(): String = name
}

/**
 * The project's image assets as heightmaps for the Stamp tool: [list] finds the `TEXTURE` and `PIXMAP_TEXTURE` assets
 * whose file exists, and [decode] reads one as a grid of luminance values. Decoding uses a libGDX `Pixmap` only, no GL,
 * so it may run on any thread; it is blocking file work, so not on the AWT thread.
 */
class StampImages(private val json: JsonProcessor) {
    fun list(assetsDir: File): List<StampImageChoice> =
        assetsDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty().mapNotNull { folder ->
            val meta = runCatchingKeepingCancellation {
                File(folder, META_FILE).takeIf { it.isFile }?.let { AssetMetaReader(json).read(it.readText()).json }
            }.getOrNull() ?: return@mapNotNull null
            val type = meta.text("type")
            if (type != MetaType.TEXTURE.name && type != MetaType.PIXMAP_TEXTURE.name) return@mapNotNull null
            val file = meta.obj("additional")?.text("file")?.let { File(folder, it) }?.takeIf { it.isFile } ?: return@mapNotNull null
            StampImageChoice(folder.name, file)
        }

    /** The luminance of [file] (Rec. 709 weights, 0 black to 1 white), or null when it cannot be read as an image. */
    fun decode(file: File): StampImage? {
        GdxNativesLoader.load()
        val pixmap = runCatchingKeepingCancellation { Pixmap(FileHandle(file)) }.getOrNull() ?: return null
        try {
            val values = FloatArray(pixmap.width * pixmap.height)
            for (y in 0 until pixmap.height) for (x in 0 until pixmap.width) {
                val rgba = pixmap.getPixel(x, y)
                val r = (rgba ushr 24) and 0xFF
                val g = (rgba ushr 16) and 0xFF
                val b = (rgba ushr 8) and 0xFF
                values[y * pixmap.width + x] = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            }
            return StampImage(pixmap.width, pixmap.height, values)
        } finally {
            pixmap.dispose()
        }
    }
}
