/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets

/** The folder beside a project's `.abss` that holds one folder per asset. */
const val ASSETS_DIR = "assets"

/** The metadata file inside every asset folder. */
const val META_FILE = "meta.json"

/** The terrain `meta.json` field naming the splat map texture asset. */
const val SPLAT_MAP = "splatMap"

/** The terrain `meta.json` fields naming the base and the four channel layer textures, in shader unit order. */
val SPLAT_LAYERS = listOf("splatBase", "splatR", "splatG", "splatB", "splatA")

/** Every terrain `meta.json` field that names a texture asset by its `uuid`. */
val SPLAT_FIELDS = listOf(SPLAT_MAP) + SPLAT_LAYERS
