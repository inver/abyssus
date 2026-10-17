/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.ui.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutNode
import net.nevinsky.abyssus.lib.core.ui.layout.UiMeasurer
import net.nevinsky.abyssus.lib.core.ui.layout.UiSizeF
import net.nevinsky.abyssus.lib.core.ui.model.UiWidget
import net.nevinsky.abyssus.lib.core.ui.model.UiWidgetType
import net.nevinsky.abyssus.lib.core.ui.state.UiBindingView
import net.nevinsky.abyssus.lib.core.ui.style.UiResolvedStyle
import net.nevinsky.abyssus.lib.core.ui.style.UiVisualState

/** What the renderer needs from the host's resource owner; the owner keeps and disposes the GL objects. */
interface UiRenderResources {
    val defaultFont: BitmapFont
    fun texture(id: String): Texture?
    fun font(id: String): BitmapFont?
}

/** What the painter reads each frame from the presenter. */
interface UiPaintContext {
    val resources: UiRenderResources
    val pixel: Texture
    val view: UiBindingView
    fun style(widget: UiWidget): UiResolvedStyle
    fun states(widget: UiWidget): Set<UiVisualState>

    /** The text a text field shows right now (its edit buffer while focused). */
    fun fieldText(widget: UiWidget): String

    /** The slider value shown right now (the drag value while dragging). */
    fun sliderValue(widget: UiWidget): Double
    fun rejected(widget: UiWidget): Boolean

    /** False when the widget or any ancestor is hidden by its `visible` binding. */
    fun shown(widget: UiWidget): Boolean
}

internal const val CHECK_BOX = 20f
internal const val CHECK_GAP = 8f
internal const val DEFAULT_FONT_SIZE = 16.0
internal val PLACEHOLDER = Color(1f, 0f, 1f, 1f)

/** Font size handling and text metrics shared by measuring and drawing. */
internal class UiText(private val context: () -> UiPaintContext) {
    private val layout = GlyphLayout()

    fun fontOf(style: UiResolvedStyle): BitmapFont {
        val resources = context().resources
        return style.resource("font")?.let(resources::font) ?: resources.defaultFont
    }

    /** What is drawn and measured for [value]: upper case under the `uppercase` style key; the widget's own text is not changed. */
    fun shown(style: UiResolvedStyle, value: String): String = if (style.flag("uppercase")) value.uppercase(java.util.Locale.ROOT) else value

    /** Lays [text] out in [style]'s font at its size; the font scale is set for this call only. */
    fun layout(style: UiResolvedStyle, text: String): GlyphLayout {
        val font = fontOf(style)
        val base = font.data.lineHeight / font.data.scaleY
        val scale = ((style.number("fontSize") ?: DEFAULT_FONT_SIZE) / base).toFloat()
        font.data.setScale(scale)
        layout.setText(font, shown(style, text))
        return layout
    }
}

/** Leaf sizes for the layout: text plus padding, fixed control sizes, image size. */
internal class UiWidgetMeasurer(private val context: () -> UiPaintContext, private val text: UiText) : UiMeasurer {
    override fun preferredSize(widget: UiWidget): UiSizeF {
        val c = context()
        val style = c.style(widget)
        val padding = (style.number("padding") ?: 0.0).toFloat()
        return when (widget.type) {
            UiWidgetType.LABEL -> text.layout(style, c.view.text(widget)).let { UiSizeF(it.width, it.height) }
            UiWidgetType.BUTTON -> text.layout(style, c.view.text(widget))
                .let { UiSizeF(it.width + 2 * padding, it.height + 2 * padding) }

            UiWidgetType.CHECKBOX -> text.layout(style, c.view.text(widget))
                .let { UiSizeF(CHECK_BOX + CHECK_GAP + it.width, maxOf(CHECK_BOX, it.height)) }

            UiWidgetType.TEXT_FIELD -> text.layout(style, "M").let { UiSizeF(200f, it.height + 2 * padding) }
            UiWidgetType.SLIDER -> UiSizeF(200f, 20f)
            UiWidgetType.PROGRESS_BAR -> UiSizeF(200f, 16f)
            UiWidgetType.IMAGE -> widget.properties["image"]?.takeIf { it.isTextual }?.textValue()
                ?.let(c.resources::texture)
                ?.let { UiSizeF(it.width.toFloat(), it.height.toFloat()) } ?: UiSizeF(64f, 64f)

            null -> UiSizeF(32f, 32f)
            else -> UiSizeF(0f, 0f)
        }
    }
}

/** Draws widgets with flat colors and bitmap text, in libGDX's y-up coordinates. */
internal class UiPainter(private val context: () -> UiPaintContext, private val text: UiText) {
    private val color = Color()

    private fun rgba(batch: Batch, rgba: Int, alpha: Float) {
        Color.rgba8888ToColor(color, rgba)
        batch.setColor(color.r, color.g, color.b, color.a * alpha)
    }

    private fun fill(batch: Batch, rgba: Int, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
        if (w <= 0f || h <= 0f) return
        rgba(batch, rgba, alpha)
        batch.draw(context().pixel, x, y, w, h)
    }

    private fun border(batch: Batch, rgba: Int, width: Float, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
        fill(batch, rgba, x, y, w, width, alpha); fill(batch, rgba, x, y + h - width, w, width, alpha)
        fill(batch, rgba, x, y, width, h, alpha); fill(batch, rgba, x + w - width, y, width, h, alpha)
    }

    private fun write(
        batch: Batch,
        style: UiResolvedStyle,
        value: String,
        rgba: Int,
        x: Float,
        centerY: Float,
        width: Float,
        center: Boolean,
        alpha: Float
    ) {
        val layout = text.layout(style, value)
        val font = text.fontOf(style)
        rgba(batch, rgba, alpha)
        font.color = batch.color
        font.draw(batch, text.shown(style, value), if (center) x + (width - layout.width) / 2f else x, centerY + layout.height / 2f)
        font.data.setScale(1f)
    }

    /** Draws [widget] into [x], [y], [w], [h] (y-up), clipped to [clip] when it is smaller. */
    fun draw(batch: Batch, widget: UiWidget, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
        val c = context()
        val style = c.style(widget)
        val textColor = style.color("color") ?: 0xffffffff.toInt()
        style.color("background")
            ?.takeIf { widget.type != UiWidgetType.SLIDER && widget.type != UiWidgetType.PROGRESS_BAR }
            ?.let { fill(batch, it, x, y, w, h, alpha) }
        val padding = (style.number("padding") ?: 0.0).toFloat()
        when (widget.type) {
            UiWidgetType.LABEL -> write(batch, style, c.view.text(widget), textColor, x, y + h / 2f, w, false, alpha)
            UiWidgetType.BUTTON -> write(batch, style, c.view.text(widget), textColor, x, y + h / 2f, w, true, alpha)
            UiWidgetType.IMAGE -> {
                val texture =
                    widget.properties["image"]?.takeIf { it.isTextual }?.textValue()?.let(c.resources::texture)
                if (texture != null) {
                    rgba(batch, style.color("tint") ?: 0xffffffff.toInt(), alpha)
                    batch.draw(texture, x, y, w, h)
                } else placeholder(batch, x, y, w, h, alpha)
            }

            UiWidgetType.PROGRESS_BAR -> {
                style.color("background")?.let { fill(batch, it, x, y, w, h, alpha) }
                style.color("fill")?.let {
                    fill(
                        batch,
                        it,
                        x + padding,
                        y + padding,
                        (w - 2 * padding) * c.view.progress(widget),
                        h - 2 * padding,
                        alpha
                    )
                }
            }

            UiWidgetType.SLIDER -> {
                val range = c.view.range(widget)
                val span = (range.endInclusive - range.start).takeIf { it > 0.0 } ?: 1.0
                val fraction = ((c.sliderValue(widget) - range.start) / span).coerceIn(0.0, 1.0).toFloat()
                val trackH = maxOf(2f, h / 4f)
                style.color("background")?.let { fill(batch, it, x, y + (h - trackH) / 2f, w, trackH, alpha) }
                style.color("fill")?.let { fill(batch, it, x, y + (h - trackH) / 2f, w * fraction, trackH, alpha) }
                style.color("knob")?.let { fill(batch, it, x + (w - 8f) * fraction, y, 8f, h, alpha) }
            }

            UiWidgetType.CHECKBOX -> {
                val box = minOf(CHECK_BOX, h)
                val checked = c.view.flag(widget, "checked", false)
                style.color("background")?.let { fill(batch, it, x, y + (h - box) / 2f, box, box, alpha) }
                if (checked) style.color("fill")
                    ?.let { fill(batch, it, x + 4f, y + (h - box) / 2f + 4f, box - 8f, box - 8f, alpha) }
                write(
                    batch,
                    style,
                    c.view.text(widget),
                    textColor,
                    x + box + CHECK_GAP,
                    y + h / 2f,
                    w - box - CHECK_GAP,
                    false,
                    alpha
                )
            }

            UiWidgetType.TEXT_FIELD -> {
                val value = c.fieldText(widget)
                write(batch, style, value, textColor, x + padding, y + h / 2f, w - 2 * padding, false, alpha)
                if (UiVisualState.FOCUSED in c.states(widget)) {
                    val caretX = x + padding + (if (value.isEmpty()) 0f else text.layout(
                        style,
                        value
                    ).width + 1f).also { text.fontOf(style).data.setScale(1f) }
                    fill(batch, textColor, caretX, y + padding, 1.5f, h - 2 * padding, alpha)
                }
            }

            null -> placeholder(batch, x, y, w, h, alpha)
            else -> Unit
        }
        val borderWidth = (style.number("borderWidth") ?: 0.0).toFloat()
        if (borderWidth > 0f) style.color("borderColor")
            ?.let { border(batch, it, borderWidth, x, y, w, h, alpha) }
        if (c.rejected(widget)) border(batch, 0xff5555ff.toInt(), 2f, x, y, w, h, alpha)
        batch.color = Color.WHITE
    }

    private fun placeholder(batch: Batch, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
        fill(batch, 0xff00ff40.toInt(), x, y, w, h, alpha)
        border(batch, 0xff00ffff.toInt(), 1f, x, y, w, h, alpha)
    }
}

/** One Scene2D actor per widget: a rectangle in the layer stage whose draw is clipped to the widget's visible area. */
internal class UiWidgetActor(
    var widget: UiWidget,
    private val painter: UiPainter,
    private val context: () -> UiPaintContext,
) : Actor() {
    /** The area to clip to, in the same y-up space as the actor bounds. */
    val clip = Rectangle()
    private val scissor = Rectangle()

    fun update(node: UiLayoutNode, referenceHeight: Float) {
        setBounds(node.bounds.x, referenceHeight - node.bounds.bottom, node.bounds.width, node.bounds.height)
        clip.set(node.visible.x, referenceHeight - node.visible.bottom, node.visible.width, node.visible.height)
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        if (clip.width <= 0f || clip.height <= 0f || !context().shown(widget)) return
        stage.calculateScissors(clip, scissor)
        batch.flush()
        if (!ScissorStack.pushScissors(scissor)) return
        painter.draw(batch, widget, x, y, width, height, parentAlpha)
        batch.flush()
        ScissorStack.popScissors()
    }
}
