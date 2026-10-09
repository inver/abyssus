/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.gdx.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.plugin.SceneRayControls

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.AbyssusCore
import net.nevinsky.abyssus.plugin.dto.SceneReader

/** A [SceneFileEditor] with the real collaborators the editor provider hands it, and a view of the test's choosing. */
internal fun newSceneEditor(
    project: Project,
    file: VirtualFile,
    paramsSource: SceneParamsSource = SceneParamsSource.editorText(service<SceneReader>()),
    host: SceneViewHost = net.nevinsky.abyssus.plugin.projectView.ProjectSceneViewHost(project),
    viewFactory: (SceneRenderParams) -> SceneView,
) = SceneFileEditor(project, file, service<AbyssusCore>().json, project.service<SceneRayControls>(), paramsSource, host, viewFactory)
