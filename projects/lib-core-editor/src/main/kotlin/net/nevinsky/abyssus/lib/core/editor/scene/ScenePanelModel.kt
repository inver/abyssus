/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import com.fasterxml.jackson.databind.JsonNode

/** The scene `skybox` row of the properties panel: the saved `skyboxName` and whether the field offers a Browse button. */
data class SkyboxRow(val value: String?, val browsable: Boolean)

/** What the properties panel shows for a scene that the view layer turns into controls. */
data class ScenePanelModel(val skybox: SkyboxRow)

/**
 * The panel model of a scene document [root]. The skybox row holds `skyboxName` (null when absent, null or not text);
 * Browse is offered only for a scene that belongs to a project ([inProject]), since only a project has skybox assets to choose.
 */
fun scenePanelModel(root: JsonNode, inProject: Boolean): ScenePanelModel =
    ScenePanelModel(SkyboxRow(root.get("skyboxName")?.takeIf { it.isTextual }?.textValue(), browsable = inProject))
