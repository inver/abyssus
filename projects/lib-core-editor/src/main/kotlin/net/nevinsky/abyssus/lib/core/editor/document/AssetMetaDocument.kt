/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.META_VERSION_DEFAULT
import net.nevinsky.abyssus.lib.core.assets.MetaType

/** Write-only envelope: declaration order is native file order; additional data stays typed by asset kind. */
data class AssetMetaDocument<A>(
    val format: String = "abyssus",
    val formatVersion: Int = 1,
    val version: Int = META_VERSION_DEFAULT,
    val lastModified: Long,
    val uuid: String,
    val type: MetaType,
    val additional: A,
)

data class TerrainAssetAdditional(
    val terrainFile: String,
    val size: Int,
    val uv: Float,
    val splatMap: String? = null,
    val splatBase: String? = null,
    val splatR: String? = null,
    val splatG: String? = null,
    val splatB: String? = null,
    val splatA: String? = null,
)

data class FoliageAssetAdditional(val terrain: String, val maskResolution: Int)

data class ModelAssetAdditional(
    val file: String = "model.glb",
    val format: String = "GLTF",
    val binary: Boolean = true,
    val materials: List<String> = emptyList(),
)

/** The deliberately sparse initial UI document, without the optional defaults of a bound runtime widget. */
data class UiAssetAdditional(val ui: InitialUiCollection = InitialUiCollection())
data class InitialUiCollection(
    val schemaVersion: Int = 1,
    val entryScreen: String = "main",
    val screens: List<InitialUiScreen> = listOf(InitialUiScreen()),
)
data class InitialUiScreen(val id: String = "main", val name: String = "Main", val root: InitialUiPanel = InitialUiPanel())
data class InitialUiPanel(
    val id: String = "root",
    val type: String = "panel",
    val children: List<InitialUiLabel> = listOf(InitialUiLabel()),
)
data class InitialUiLabel(
    val id: String = "title",
    val type: String = "label",
    val layout: InitialUiLayout = InitialUiLayout(),
    val properties: InitialUiProperties = InitialUiProperties(),
)
data class InitialUiLayout(val anchor: String = "center")
data class InitialUiProperties(val text: String = "Hello")

data class TextureAssetAdditional(val file: String)
