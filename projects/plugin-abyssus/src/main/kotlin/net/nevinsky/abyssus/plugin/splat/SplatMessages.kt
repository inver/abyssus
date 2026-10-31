/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.splat

import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatProblem
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatState
import net.nevinsky.abyssus.plugin.AbyssusBundle

/** The sentence the strip shows when the splat map cannot be painted as it is, or null when it can (or there is none yet). */
fun SplatState.reason(): String? = when (this) {
    is SplatState.Ready -> null
    is SplatState.None -> null
    is SplatState.Unusable -> when (problem) {
        SplatProblem.TERRAIN_META -> AbyssusBundle.message("splatUnavailableTerrain", AbyssusBundle.message("splatUnsupportedMeta"))
        SplatProblem.TEXTURE_META -> AbyssusBundle.message("splatUnavailableTextureMeta")
        SplatProblem.IMAGE_MISSING -> AbyssusBundle.message("splatUnavailableImageMissing", detail)
        SplatProblem.IMAGE -> AbyssusBundle.message("splatUnavailable.${image?.name ?: "UNREADABLE"}", detail)
    }
}

/** Whether Create splat map is offered: there is no map, or the one named cannot be painted (but the terrain `meta.json` is fine). */
val SplatState.offersCreate: Boolean
    get() = this is SplatState.None || (this is SplatState.Unusable && problem != SplatProblem.TERRAIN_META)
