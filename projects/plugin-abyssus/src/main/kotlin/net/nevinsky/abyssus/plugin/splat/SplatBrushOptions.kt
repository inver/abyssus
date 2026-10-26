/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.splat

import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.lib.core.editor.pick.BrushOptions

/** What a Paint Texture stroke does to the weights under the brush. */
enum class SplatMode { PAINT, ERASE, SMOOTH, FILL }

/**
 * What the Paint Texture brush keeps in its [BrushMode] beside the shared fields: the layer [slot] (0 is Base, 1 to 4
 * are the splat map's channels R, G, B and A), the [mode], and whether painting is [exclusive].
 */
class SplatBrushOptions(val slot: Int, val mode: SplatMode, val exclusive: Boolean) : BrushOptions

/** The options of a Paint Texture mode; the mode is one the splat strip built, so it always has them. */
val BrushMode.splat: SplatBrushOptions get() = options as SplatBrushOptions
