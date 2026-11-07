/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

/*
 * The shadow atlas's per-view and per-light data lives in one float texture instead of uniform arrays, so a scene can
 * shadow hundreds of lights: uniform arrays of that size do not fit the fragment constant registers.
 *
 * The texture is [SHADOW_DATA_WIDTH] texels wide (RGBA32F) and [SHADOW_DATA_HEIGHT] rows high. A row of the view block
 * (rows 0 until [SHADOW_VIEW_ROWS]) describes one atlas view:
 *   texels 0 to 3: the light matrix (its four columns), texel 4: the tile (x, y, w, h in atlas uv),
 *   texel 5: the light position (xyz) and far distance (w), texel 6: the depth bias (x).
 * The tables that follow give, per light index (the order of the environment's lights of that kind), the first view
 * of the light or -1: [SHADOW_DIRECTIONAL_TABLE_ROW], [SHADOW_POINT_TABLE_ROW] and [SHADOW_SPOT_TABLE_ROW]. Entry i
 * is the x of texel (i % [SHADOW_DATA_WIDTH]) of row (table row + i / [SHADOW_DATA_WIDTH]). A point light's six views
 * are consecutive from its first view.
 * `shadow_visibility.glsl` repeats these numbers; ShadowDataLayoutTest keeps the two in step.
 */
const val SHADOW_DATA_WIDTH = 8
const val SHADOW_DATA_HEIGHT = 2048
const val SHADOW_MAX_DIRECTIONAL = 2
const val SHADOW_MAX_LOCAL = 256
const val SHADOW_POINT_FACES = 6
const val SHADOW_VIEW_ROWS = 1544 // at least SHADOW_MAX_DIRECTIONAL + SHADOW_MAX_LOCAL * SHADOW_POINT_FACES
const val SHADOW_MAX_VIEWS = SHADOW_VIEW_ROWS
const val SHADOW_DIRECTIONAL_TABLE_ROW = SHADOW_VIEW_ROWS
const val SHADOW_POINT_TABLE_ROW = 1552
const val SHADOW_SPOT_TABLE_ROW = SHADOW_POINT_TABLE_ROW + SHADOW_MAX_LOCAL / SHADOW_DATA_WIDTH
