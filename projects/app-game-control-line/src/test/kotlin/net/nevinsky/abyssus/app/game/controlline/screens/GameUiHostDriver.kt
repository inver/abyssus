/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import net.nevinsky.abyssus.app.game.controlline.flight.FlightSession
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternJudge
import net.nevinsky.abyssus.lib.core.ui.model.UiWidget
import net.nevinsky.abyssus.lib.core.ui.model.UiWidgetType
import net.nevinsky.abyssus.lib.core.ui.state.expandLists
import java.util.Locale

/** The colour role a style name stands for in the dump: the roles the hand-written screens gave a label's colour. */
private fun roleOf(style: String?): String = when (style) {
    "highlight", "big", "toast" -> "highlight"
    "muted", "fail", "selected", "ideal", "flown" -> style
    else -> "normal"
}

/** Walks the authored screens in a [GameUiHost] and reads back what its presenter shows, in the dump format of [ScreenDriver]. */
class GameUiHostDriver(private val host: GameUiHost) : ScreenDriver {
    private val presenter = host.presenter

    override fun update(session: FlightSession?, judge: PatternJudge?, seconds: Float) {
        host.update(session, judge, seconds)
        presenter.resize(1280, 720)
        presenter.render(0f)
    }

    override fun keyDown(keycode: Int) = host.keyDown(keycode)

    override fun dump(): List<String> {
        val view = presenter.view
        val lines = mutableListOf<String>()
        fun firstLabel(widget: UiWidget): String? =
            if (widget.type == UiWidgetType.LABEL) view.text(widget) else widget.children.firstNotNullOfOrNull(::firstLabel)

        fun walk(widget: UiWidget) {
            if (!view.visible(widget)) return
            val style = view.styleName(widget)
            val focused = presenter.navigator.focus == widget.id
            when {
                widget.type == UiWidgetType.LABEL -> view.text(widget).takeIf { it.isNotEmpty() }?.let { lines += "label|${roleOf(style)}|$it" }
                widget.type == UiWidgetType.BUTTON ->
                    lines += "button|${if (view.enabled(widget)) "enabled" else "disabled"}${if (focused) "|checked" else ""}|${view.text(widget)}"
                widget.type == UiWidgetType.TEXT_FIELD -> lines += "field|${view.text(widget)}"
                widget.type == UiWidgetType.PROGRESS_BAR -> lines += String.format(Locale.ROOT, "bar|%.2f", view.progress(widget))
                // a score-table tab is a clickable container: it reads as a toggle button
                style == "tab" || style == "tabSelected" ->
                    lines += "button|enabled${if (style == "tabSelected") "|checked" else ""}|${firstLabel(widget).orEmpty()}"
                else -> widget.children.forEach(::walk)
            }
        }
        for (layer in presenter.navigator.layers().asReversed()) {
            val screen = presenter.navigator.screenOf(layer.ref) ?: continue
            walk(expandLists(screen.root, view).root)
        }
        return lines
    }
}
