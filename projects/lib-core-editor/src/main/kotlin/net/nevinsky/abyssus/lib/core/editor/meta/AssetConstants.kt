/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.meta

/** The faces of a skybox in `meta.json`'s order: the property names of `SkyboxMeta`. */
val SKYBOX_FACES = listOf("top", "bottom", "left", "right", "front", "back")

/** The height file a new terrain folder holds. */
const val TERRAIN_DATA_FILE = "terrain.data"

/** `terrainFile` of a new terrain's meta. */
const val TERRAIN_META_FILE_NAME_DEFAULT = TERRAIN_DATA_FILE

/** `uv` of a new terrain's meta. */
const val NEW_TERRAIN_UV_DEFAULT = 1f
