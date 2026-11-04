/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.dto.SceneDto

/**
 * The scene `skybox` row of the properties panel: the saved `skyboxName` and whether the field offers a Browse button.
 */
data class SkyboxRow(val value: String?, val browsable: Boolean)

/** What the properties panel shows for a scene that the view layer turns into controls. */
data class ScenePanelModel(val skybox: SkyboxRow)

/**
 * The panel model of [scene]. The skybox row holds `skyboxName` (null when absent); Browse is offered only for a scene
 * that belongs to a project ([inProject]), since only a project has skybox assets to choose.
 */
fun scenePanelModel(scene: SceneDto, inProject: Boolean): ScenePanelModel =
    ScenePanelModel(SkyboxRow(scene.skyboxName, browsable = inProject))
