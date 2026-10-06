/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.document.SceneJson

import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.dto.SceneReader
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.dto.textOf

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
                net.nevinsky.abyssus.format.AbyssusDocumentFormat().requireSupported(net.nevinsky.abyssus.editor.document.SceneJson().parse(text), net.nevinsky.abyssus.format.DocumentKind.PROJECT)
                MainCamera.parse(text)
            } ?: CameraParams.DEFAULT
            SceneRenderParams.from(reader.parse(textOf(file)), camera, ProjectLayout.projectDirFor(file))
        }
    }
}
