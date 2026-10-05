/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.terrain

/** The splat map: per-pixel weights of the four channel layers. */
const val SPLAT_MAP = "splatMap"

/** The layer textures in blend order: the base, then the splat map's R, G, B and A channels. */
val SPLAT_LAYERS = listOf("splatBase", "splatR", "splatG", "splatB", "splatA")

/** Every `meta.json` splat field, in file order. */
val SPLAT_FIELDS = listOf(SPLAT_MAP) + SPLAT_LAYERS

val TERRAIN_META_FILE_NAME_DEFAULT = "terrain.data"

const val NEW_TERRAIN_UV_DEFAULT = 1f

/** The `additional` block of a terrain `meta.json`. */
data class TerrainMeta(
    val file: String? = null,
    val size: Int = 0,
    val uv: Float = 0f,
    val splatMap: String? = null,
    val splatBase: String? = null,
    val splatR: String? = null,
    val splatG: String? = null,
    val splatB: String? = null,
    val splatA: String? = null,
) {
    /** The reference stored in [field] (one of [SPLAT_FIELDS]); null when unset or blank. */
    fun splat(field: String): String? = when (field) {
        SPLAT_MAP -> splatMap
        SPLAT_LAYERS[0] -> splatBase
        SPLAT_LAYERS[1] -> splatR
        SPLAT_LAYERS[2] -> splatG
        SPLAT_LAYERS[3] -> splatB
        SPLAT_LAYERS[4] -> splatA
        else -> null
    }?.takeIf { it.isNotBlank() }
}
