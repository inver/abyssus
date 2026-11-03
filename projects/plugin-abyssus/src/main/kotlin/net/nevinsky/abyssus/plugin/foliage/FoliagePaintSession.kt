/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.foliage

import net.nevinsky.abyssus.lib.core.editor.pick.BrushStroke

/** The loaded layers and owned brush adapter of one Scene view; closing it cancels a pending stroke. */
class FoliagePaintSession(
    val layers: List<Int>,
    val paint: BrushStroke,
    private val close: () -> Unit,
) {
    fun dispose() = close()
}
