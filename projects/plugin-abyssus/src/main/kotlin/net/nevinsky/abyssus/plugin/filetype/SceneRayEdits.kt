/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.filetype


import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.editor.host.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.AbyssusBundle

/** Platform adapter: every accepted edit is one native document command. */
object SceneRayEdits {
    fun setting(
        project: Project,
        file: VirtualFile,
        field: SceneRayField,
        expected: Document?,
        text: String
    ): RayDataEdit = edit(project, file) { SceneRaySettingsCodec().edit(it, field, expected, text) }

    fun material(
        project: Project,
        file: VirtualFile,
        entity: String,
        id: String,
        field: RayOpticalField,
        expected: Document?,
        text: String,
        identities: List<RayMaterialIdentity>
    ): RayDataEdit = edit(project, file) { root ->
        val render =
            net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree(root).componentObject(entity, "RenderComponent")
                ?: return@edit RayDataEdit.Conflict
        RayMaterialOverrides().edit(render, id, field, expected, text, identities)
    }

    private fun edit(project: Project, file: VirtualFile, mutate: DocumentEdit<RayDataEdit>): RayDataEdit {
        var result: RayDataEdit = RayDataEdit.Rejected(RayDataError.OBJECT)
        if (file.extension != "scene") {
            return result
        }
        editSceneJson(project, file, AbyssusBundle.message("commandEditSceneRaySettings")) { root ->
            result = mutate(root)
            result == RayDataEdit.Changed
        }
        return result
    }
}
