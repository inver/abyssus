/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.editor.hierarchy.modelNodeTransforms
import net.nevinsky.abyssus.lib.core.hierarchy.NodeTransforms
import net.nevinsky.abyssus.lib.core.hierarchy.noNodeTransforms
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.core.editor.scene.MainCamera
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.scene.renderParamsOf
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.dto.SceneReader
import net.nevinsky.abyssus.plugin.dto.textOf

/** Reads what the scene view shows for a `.scene` file; throws when the scene cannot be read. */
fun interface SceneParamsSource {
    fun read(sceneFile: VirtualFile): SceneRenderParams

    /** [read]s through this source, plus every other file whose change must refresh the view of [sceneFile]. */
    fun sources(sceneFile: VirtualFile): Set<VirtualFile> = setOfNotNull(sceneFile, ProjectLayout.abssFor(sceneFile))

    companion object {
        /**
         * The scene and its project's main camera as the editors show them (unsaved text included); the default camera
         * when the scene has no project or its camera is unreadable.
         */
        fun editorText(reader: SceneReader) = SceneParamsSource { file ->
            val camera =
                ProjectLayout.abssFor(file)?.let { abss ->
                    val text = textOf(abss)
                    val documents =
                        com.intellij.openapi.components
                            .service<net.nevinsky.abyssus.plugin.AbyssusPluginCtx>()
                            .documents
                    documents.parsing.requireSupported(text, net.nevinsky.abyssus.lib.core.format.DocumentKind.PROJECT)
                    MainCamera(documents.json).parse(text)
                } ?: CameraParams.DEFAULT
            val parsed = SceneDocumentCache.parsedScene(reader, textOf(file))
            val dir = ProjectLayout.projectDirFor(file)
            renderParamsOf(parsed.scene, camera, dir, cachedNodeTransforms(parsed.scene, dir))
        }
    }
}

/**
 * The node rest transforms of the models in [scene] that are already read (OpenSpec `scene-entity-hierarchy`, "A child
 * can sit on a node of its parent's model"); a model not read yet answers `NotLoaded`, so its node-bound children sit
 * on
 * their parent until [SceneFileEditor] has the model read and re-reads the scene.
 */
internal fun cachedNodeTransforms(scene: SceneDto, projectDir: File?): NodeTransforms {
    projectDir ?: return noNodeTransforms()
    val loading = com.intellij.openapi.components.service<net.nevinsky.abyssus.plugin.AbyssusPluginCtx>().assets.loading
    return modelNodeTransforms(scene) { asset -> loading.cachedModelNodes(projectDir, asset) }
}
