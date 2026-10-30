/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("UiArt")

package net.nevinsky.abyssus.app.game.controlline.tools

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

private val RULE = Color(0x1E3342)
private val ACCENT = Color(0xFF6A2B)
private val TEXT = Color(0xE8F1F5)

/** The menu plane (points of its silhouette around its centre, nose up), as the design draws it. */
private val MENU_PLANE = listOf(
    0 to -26, 5 to -8, 34 to 4, 34 to 11, 5 to 8, 3 to 24, 14 to 29, 14 to 34, 0 to 31, -14 to 34, -14 to 29, -3 to 24,
    -5 to 8, -34 to 11, -34 to 4, -5 to -8,
)

/**
 * Writes the screens' illustrations into `<project>/assets/screens/art`: `menu-track.png` (the control circle with its
 * lines and a plane, for the main menu). Drawn from the design's geometry with Java2D; the output depends only on this file, so running it
 * again gives the same bytes. Run by `./gradlew :app-game-control-line:generateUiArt`.
 */
fun main(args: Array<String>) {
    require(args.size == 1) { "usage: UiArt <project folder>" }
    System.setProperty("java.awt.headless", "true")
    val out = Path.of(args[0]).resolve("assets/screens/art")
    Files.createDirectories(out)
    ImageIO.write(menuTrack(), "png", out.resolve("menu-track.png").toFile())
}

private fun canvas(size: Int, draw: Graphics2D.() -> Unit): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    g.draw()
    g.dispose()
    return image
}

private fun silhouette(points: List<Pair<Int, Int>>): Path2D.Double = Path2D.Double().apply {
    points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x.toDouble(), y.toDouble()) else lineTo(x.toDouble(), y.toDouble()) }
    closePath()
}

private fun Graphics2D.circle(cx: Double, cy: Double, r: Double) = draw(Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r))

/** A 640 x 640 picture: three rings, axes, the dashed flight circle, the line to the handle and a plane on the circle. */
private fun menuTrack(): BufferedImage = canvas(640) {
    val c = 320.0
    color = RULE
    stroke = BasicStroke(1.5f)
    for (r in listOf(120.0, 220.0, 320.0 - 1)) circle(c, c, r)
    stroke = BasicStroke(1f)
    draw(Line2D.Double(c, 0.0, c, 640.0))
    draw(Line2D.Double(0.0, c, 640.0, c))
    color = ACCENT
    stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(2f, 10f), 0f)
    circle(c, c, 220.0)
    color = TEXT
    stroke = BasicStroke(1.5f)
    draw(Line2D.Double(c, c, c + 156, c - 156))
    fill(Ellipse2D.Double(c - 8, c - 8, 16.0, 16.0))
    val move = AffineTransform.getTranslateInstance(c + 156, c - 156).apply { rotate(Math.toRadians(45.0)) }
    color = ACCENT
    fill(move.createTransformedShape(silhouette(MENU_PLANE)))
}
