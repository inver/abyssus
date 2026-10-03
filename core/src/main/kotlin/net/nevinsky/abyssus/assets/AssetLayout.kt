/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
