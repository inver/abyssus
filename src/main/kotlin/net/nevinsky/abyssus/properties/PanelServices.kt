/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import net.nevinsky.abyssus.editor.meta.AssetFieldDescriptions
import net.nevinsky.abyssus.editor.meta.AssetMetaEditor
import net.nevinsky.abyssus.core.io.JsonProcessor
import net.nevinsky.abyssus.editor.terrain.TerrainGenerator
import net.nevinsky.abyssus.editor.terrain.TerrainHeightEncoder
import net.nevinsky.abyssus.editor.terrain.TerrainRecipeCodec
import net.nevinsky.abyssus.dto.MetaFiles
import net.nevinsky.abyssus.projectView.HdrPreviewSource
import net.nevinsky.abyssus.editor.ray.RayMaterialIdentity
import net.nevinsky.abyssus.SceneRayControls
import java.io.File
import net.nevinsky.abyssus.schema.ComponentSchemas

/**
 * What the Properties panel and its state readers use, passed in by the tool window factory instead of being looked up:
 * asset metadata and its editor, the HDR preview pieces, the terrain generation pieces, the Scene views' Ray Tracing
 * switches, and the material tables of models for their optical overrides.
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
    /**
     * The material table of model [String] in project directory [File], or null when the model cannot be found. Called off the
     * EDT; it may parse the model file and may throw.
     */
    val rayMaterials: (File, String) -> List<RayMaterialIdentity>? = { _, _ -> null },
) {
    val facts: net.nevinsky.abyssus.editor.facts.SceneFacts<*> get() = rayControls
}
