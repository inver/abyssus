/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("UiFonts")

package net.nevinsky.abyssus.app.game.controlline.tools

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A font family of the screens: its id prefix, its source file in `tools/fonts` and the pixel sizes to generate. */
private class Family(val id: String, val file: String, val sizes: List<Int>)

private val FAMILIES = listOf(
    Family("display", "BarlowCondensed-Bold.ttf", listOf(28, 32, 44, 64, 96)),
    Family("displayMedium", "BarlowCondensed-Medium.ttf", listOf(24, 28)),
    Family("mono", "IBMPlexMono-Regular.ttf", listOf(12, 14, 15, 18)),
)

/** The characters of every font: printable ASCII and the few others the game's texts use. */
private val CHARS: List<Char> = (' '..'~').toList() + "×½°·–—•←→↑↓".toList()

private const val GAP = 2

/** The HiDPI variant of a font is rendered [HIDPI] times larger and named with [HIDPI_SUFFIX] (`display-28@2x`). */
private const val HIDPI = 2
private const val HIDPI_SUFFIX = "@2x"

/**
 * Writes the screens' bitmap fonts into `<project>/assets/screens/fonts`: for every family and size a text `.fnt` plus
 * a white-on-transparent `.png` page (so a style's colour tints it) and a `@2x` twin of both at twice the pixel size for
 * HiDPI windows, and `index.json` with each font's line height,
 * which is what a style's `fontSize` is measured in. The source fonts (SIL OFL, see the licence files) are in
 * `tools/fonts`, so the output depends only on them and this file and running it again gives the same bytes. Run by
 * `./gradlew :app-game-control-line:generateUiFonts`.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: UiFonts <project folder> <source fonts folder>" }
    System.setProperty("java.awt.headless", "true")
    val out = Path.of(args[0]).resolve("assets/screens/fonts")
    Files.createDirectories(out)
    out.toFile().listFiles()?.forEach { if (it.extension in setOf("fnt", "png", "json")) it.delete() }
    val index = StringBuilder("{\n")
    val entries = mutableListOf<String>()
    for (family in FAMILIES) {
        val source = Font.createFont(Font.TRUETYPE_FONT, Path.of(args[1]).resolve(family.file).toFile())
        for (size in family.sizes) {
            val id = "${family.id}-$size"
            val lineHeight = writeFont(source.deriveFont(size.toFloat()), id, out)
            // the HiDPI variant: the same font at twice the pixel size, which the game loads on a denser display
            writeFont(source.deriveFont(size.toFloat() * HIDPI), "$id$HIDPI_SUFFIX", out)
            entries += "  \"$id\": $lineHeight"
        }
    }
    index.append(entries.joinToString(",\n")).append("\n}\n")
    Files.writeString(out.resolve("index.json"), index.toString())
}

private class Glyph(val ch: Char, val x: Int, val y: Int, val w: Int, val h: Int, val xOffset: Int, val yOffset: Int, val advance: Int)

/** Writes `<id>.fnt` and `<id>.png`; returns the font's line height. */
private fun writeFont(font: Font, id: String, out: Path): Int {
    val frc = FontRenderContext(null, true, true)
    val metrics = font.getLineMetrics("Hg", frc)
    val base = ceil(metrics.ascent.toDouble()).toInt()
    val lineHeight = base + ceil(metrics.descent.toDouble()).toInt() + ceil(metrics.leading.toDouble()).toInt()
    val vectors = CHARS.associateWith { font.createGlyphVector(frc, it.toString()) }
    val boxes = vectors.mapValues { (_, v) -> v.getPixelBounds(frc, 0f, 0f) }
    val page = (8..12).map { 1 shl it }.first { pack(boxes.values.map { it.width to it.height }, it) != null }
    val spots = pack(boxes.values.map { it.width to it.height }, page)!!
    val image = BufferedImage(page, page, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    g.color = Color.WHITE
    val glyphs = CHARS.mapIndexed { i, ch ->
        val box = boxes.getValue(ch)
        val (x, y) = spots[i]
        if (box.width > 0 && box.height > 0) g.drawGlyphVector(vectors.getValue(ch), (x - box.x).toFloat(), (y - box.y).toFloat())
        Glyph(ch, x, y, box.width, box.height, box.x, base + box.y, vectors.getValue(ch).getGlyphMetrics(0).advance.roundToInt())
    }
    g.dispose()
    ImageIO.write(image, "png", out.resolve("$id.png").toFile())
    val fnt = StringBuilder()
    fnt.append("info face=\"$id\" size=${font.size} bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=$GAP,$GAP\n")
    fnt.append("common lineHeight=$lineHeight base=$base scaleW=$page scaleH=$page pages=1 packed=0\n")
    fnt.append("page id=0 file=\"$id.png\"\n")
    fnt.append("chars count=${glyphs.size}\n")
    for (c in glyphs) {
        fnt.append("char id=${c.ch.code} x=${c.x} y=${c.y} width=${c.w} height=${c.h} xoffset=${c.xOffset} yoffset=${c.yOffset} xadvance=${c.advance} page=0 chnl=15\n")
    }
    fnt.append("kernings count=0\n")
    Files.writeString(out.resolve("$id.fnt"), fnt.toString())
    return lineHeight
}

/** Shelf-packs boxes of the given sizes (each with a [GAP] around it) into a square page; null when they do not fit. */
private fun pack(sizes: List<Pair<Int, Int>>, page: Int): List<Pair<Int, Int>>? {
    var x = GAP
    var y = GAP
    var shelf = 0
    val spots = ArrayList<Pair<Int, Int>>(sizes.size)
    for ((w, h) in sizes) {
        if (x + w + GAP > page) { x = GAP; y += shelf + GAP; shelf = 0 }
        if (y + h + GAP > page || w + 2 * GAP > page) return null
        spots += x to y
        x += w + GAP
        if (h > shelf) shelf = h
    }
    return spots
}
