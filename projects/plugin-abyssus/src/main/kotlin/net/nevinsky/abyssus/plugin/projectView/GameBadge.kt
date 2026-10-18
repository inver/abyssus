/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.physics.PHYSICS_COMPONENTS
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints

private val builtInRegistry = ComponentRegistry()
private val physicsNames = PHYSICS_COMPONENTS.values.flatMap { listOf(it.name, it.simpleName) }.toSet() + PHYSICS_COMPONENTS.keys

/**
 * True for a component key (short or fully qualified class name, as the scene file writes it) that Abyssus itself does not
 * ship: the built-in components and the fixed physics kinds are not game components, anything else a scene names is
 * registered by the game project. Pure over the key; touches no runtime.
 */
fun isGameComponent(key: String): Boolean = builtInRegistry.get(key) == null && key !in physicsNames

/** The `game` badge painted after the name of a game component's row. */
internal object GameBadge {
    const val TEXT = "game"

    /** Marks the badge's fragment in the row renderer, so its outline is painted around exactly that text. */
    val TAG = Any()

    val FOREGROUND: Color = JBColor(0xC2457F, 0xF47FB0)
    val BORDER: Color = JBColor(0xE6A8C3, 0x6A3A52)
    val ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, FOREGROUND)

    fun paintOutline(g: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = BORDER
        val pad = JBUI.scale(3)
        g.drawRoundRect(x - pad, y, width + 2 * pad - 1, height - 1, JBUI.scale(4), JBUI.scale(4))
    }
}
