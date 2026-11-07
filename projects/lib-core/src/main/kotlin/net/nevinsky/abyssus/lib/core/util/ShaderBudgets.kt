/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.util

import net.nevinsky.abyssus.lib.gdx.shader.SHADOW_MAX_LOCAL

/** Host light budgets and texture bindings shared by the bundled shaders and their Kotlin consumers. */
const val EDITOR_MAX_DIRECTIONAL = 2
const val EDITOR_MAX_LOCAL = 256

/**
 * Point lights, and spot lights, the editor terrain shader lights. Its arrays are plain uniforms, and 256 of each
 * needs more than the 1024 constant registers a fragment program may use, so the terrain keeps to the nearest lights
 * of each kind.
 */
const val TERRAIN_MAX_LOCAL = 64
const val EDITOR_MAX_POINT = EDITOR_MAX_LOCAL
const val EDITOR_MAX_SPOT = EDITOR_MAX_LOCAL

/**
 * How many point lights, and how many spot lights, can have a shadow: every light the editor selects. The shadow atlas
 * keeps the data of its views in a texture, so this is not limited by uniform arrays.
 */
const val SHADOWED_LOCAL = SHADOW_MAX_LOCAL
const val GAME_MAX_DIRECTIONAL = 1
const val GAME_MAX_POINT = 0
const val GAME_MAX_SPOT = 0

/**
 * Point lights, and spot lights, a game lights one model with when it lights with the scene's local lights: those that
 * reach the model, the strongest first. A room is often one model that every lamp of it reaches (the hangar's 64), so
 * the budget is the lamps of a room; a small model gets only the few around it, so its fragment loop stays short.
 */
const val GAME_SCENE_MAX_POINT = 64
const val GAME_SCENE_MAX_SPOT = 16

/** Water's shader array capacity is independent of the editor's selected directional-light budget. */
const val WATER_DIRECTIONAL_CAPACITY = 5

/** The five terrain layer textures precede the splat map; lighting textures follow it. */
const val TERRAIN_SPLAT_TEXTURE_UNIT = 5
const val TERRAIN_SHADOW_TEXTURE_UNIT = TERRAIN_SPLAT_TEXTURE_UNIT + 1
const val TERRAIN_IRRADIANCE_TEXTURE_UNIT = TERRAIN_SHADOW_TEXTURE_UNIT + 1
const val TERRAIN_IRRADIANCE_TO_TEXTURE_UNIT = TERRAIN_IRRADIANCE_TEXTURE_UNIT + 1
const val TERRAIN_SUN_VISIBILITY_TEXTURE_UNIT = TERRAIN_IRRADIANCE_TO_TEXTURE_UNIT + 1
const val TERRAIN_SHADOW_DATA_TEXTURE_UNIT = TERRAIN_SUN_VISIBILITY_TEXTURE_UNIT + 1
