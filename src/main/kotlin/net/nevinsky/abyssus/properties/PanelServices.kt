/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import net.nevinsky.abyssus.assets.edit.AssetFieldDescriptions
import net.nevinsky.abyssus.assets.edit.AssetMetaEditor
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.terrain.generation.TerrainGenerator
import net.nevinsky.abyssus.assets.terrain.generation.TerrainHeightEncoder
import net.nevinsky.abyssus.assets.terrain.generation.TerrainRecipeCodec
import net.nevinsky.abyssus.dto.MetaFiles
import net.nevinsky.abyssus.projectView.HdrPreviewSource
import net.nevinsky.abyssus.sceneview.SceneRayControls
import net.nevinsky.abyssus.schema.ComponentSchemas

/**
 * What the Properties panel and its state readers use, passed in by the tool window factory instead of being looked up:
 * asset metadata and its editor, the HDR preview pieces, the terrain generation pieces, and the Scene views' Ray Tracing
 * switches.
 */
class PanelServices(
    val metaFiles: MetaFiles,
    val hdr: HdrPreviewSource,
    val json: JsonProcessor,
    val assetFields: AssetFieldDescriptions,
    val assetEditor: AssetMetaEditor,
    val terrainGenerator: TerrainGenerator,
    val heightEncoder: TerrainHeightEncoder,
    val terrainRecipes: TerrainRecipeCodec,
    val rayControls: SceneRayControls,
    /** The component editor of each scene, from the project's and the contributed component schemas. */
    val schemas: ComponentSchemas,
)
