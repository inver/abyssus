/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.foliage

import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.lib.core.editor.pick.BrushOptions

/** What the Paint Foliage brush keeps in its [BrushMode] beside the shared fields: the density layer it paints. */
class FoliageBrushOptions(val layerId: Int) : BrushOptions

/** The density layer of a Paint Foliage mode; the mode is one the foliage strip built, so it always has its options. */
val BrushMode.layerId: Int get() = (options as FoliageBrushOptions).layerId
