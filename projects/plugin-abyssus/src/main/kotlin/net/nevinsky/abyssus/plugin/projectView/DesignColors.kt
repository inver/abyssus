/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ui.JBColor

/** Colours of the design canvas that the IDE theme has no equivalent for: the selected-row highlight and the accent. */
object DesignColors {
    /** `#2e436e` on dark themes, a light blue of the same hue on light ones. */
    @JvmField
    val SELECTION = JBColor(0xD3E2FA, 0x2E436E)

    /** `#3fb8c9` on dark themes, darkened for light ones. */
    @JvmField
    val ACCENT = JBColor(0x1E8A99, 0x3FB8C9)

    /** The light and dark RGB of each panel color, by name, so a test can check their contrast without a theme. */
    internal val PAIRS: Map<String, Pair<Int, Int>> = mapOf(
        // the field box and panel surface: `#2b2d30` on dark themes, white on light ones
        "surface" to (0xFFFFFF to 0x2B2D30),
        // the border of a field box and a badge: `#4e5157` on dark themes
        "border" to (0xA6A9B5 to 0x4E5157),
        // secondary text and the dim default values: `#868a91` on dark themes, darkened to 4.5:1 for light ones
        "secondary" to (0x666A78 to 0x868A91),
        // the accent of a check box and a link: `#548af7` on dark themes
        "blue" to (0x2F63D6 to 0x548AF7),
        // the `no scene uses this` badge: `#e0a458` on dark themes
        "amber" to (0x855400 to 0xE0A458),
        // the `game` badge: `#f47fb0` on dark themes
        "game" to (0xB03A72 to 0xF47FB0),
        // the hairline under a section header and between table rows: `#393b40` on dark themes
        "hairline" to (0xDFE1E5 to 0x393B40),
    )

    private fun pair(name: String) = PAIRS.getValue(name).let { JBColor(it.first, it.second) }

    @JvmField
    val SURFACE = pair("surface")

    @JvmField
    val BORDER = pair("border")

    @JvmField
    val SECONDARY = pair("secondary")

    @JvmField
    val BLUE = pair("blue")

    @JvmField
    val AMBER = pair("amber")

    @JvmField
    val GAME = pair("game")

    @JvmField
    val HAIRLINE = pair("hairline")
}
