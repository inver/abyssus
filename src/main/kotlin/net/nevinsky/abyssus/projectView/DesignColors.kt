/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.ui.JBColor

/** Colours of the design canvas that the IDE theme has no equivalent for: the selected-row highlight and the accent. */
object DesignColors {
    /** `#2e436e` on dark themes, a light blue of the same hue on light ones. */
    @JvmField
    val SELECTION = JBColor(0xD3E2FA, 0x2E436E)

    /** `#3fb8c9` on dark themes, darkened for light ones. */
    @JvmField
    val ACCENT = JBColor(0x1E8A99, 0x3FB8C9)
}
