/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.util

/** Host light budgets and texture bindings shared by the bundled shaders and their Kotlin consumers. */
const val EDITOR_MAX_DIRECTIONAL = 2
const val EDITOR_MAX_LOCAL = 5
const val EDITOR_MAX_POINT = EDITOR_MAX_LOCAL
const val EDITOR_MAX_SPOT = EDITOR_MAX_LOCAL
const val GAME_MAX_DIRECTIONAL = 1
const val GAME_MAX_POINT = 0
const val GAME_MAX_SPOT = 0

/** Water's shader array capacity is independent of the editor's selected directional-light budget. */
const val WATER_DIRECTIONAL_CAPACITY = 5

/** The five terrain layer textures precede the splat map; lighting textures follow it. */
const val TERRAIN_SPLAT_TEXTURE_UNIT = 5
const val TERRAIN_SHADOW_TEXTURE_UNIT = TERRAIN_SPLAT_TEXTURE_UNIT + 1
const val TERRAIN_IRRADIANCE_TEXTURE_UNIT = TERRAIN_SHADOW_TEXTURE_UNIT + 1
const val TERRAIN_IRRADIANCE_TO_TEXTURE_UNIT = TERRAIN_IRRADIANCE_TEXTURE_UNIT + 1
const val TERRAIN_SUN_VISIBILITY_TEXTURE_UNIT = TERRAIN_IRRADIANCE_TO_TEXTURE_UNIT + 1
