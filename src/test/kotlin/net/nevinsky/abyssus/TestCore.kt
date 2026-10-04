/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import net.nevinsky.abyssus.properties.PanelServices
import net.nevinsky.abyssus.sceneview.SceneRayControls

/** The collaborators production code is handed by its provider, factory or action, for tests that call it directly. */
internal val testCore: AbyssusCore get() = service()

internal fun testMetaFiles() = testCore.metaFiles

internal fun testPanelServices(project: Project) = testCore.let {
    PanelServices(
        it.metaFiles, it.hdrPreviews, it.json, it.assetFields, it.assetEditor,
        it.terrainGenerator, it.heightEncoder, it.terrainRecipes, project.service<SceneRayControls>(),
    )
}
