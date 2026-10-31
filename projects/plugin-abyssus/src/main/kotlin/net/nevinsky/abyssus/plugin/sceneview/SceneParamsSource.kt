/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.core.editor.scene.MainCamera
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.scene.renderParamsOf

import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.dto.SceneReader
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
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
            val camera = ProjectLayout.abssFor(file)?.let { abss ->
                val text = textOf(abss)
                val documents = com.intellij.openapi.components.service<net.nevinsky.abyssus.plugin.AbyssusPluginCtx>().documents
                documents.parsing.requireSupported(text, net.nevinsky.abyssus.lib.core.format.DocumentKind.PROJECT)
                MainCamera(documents.json).parse(text)
            } ?: CameraParams.DEFAULT
            val parsed = SceneDocumentCache.parsedScene(reader, textOf(file))
            renderParamsOf(parsed.scene, camera, ProjectLayout.projectDirFor(file))
        }
    }
}
