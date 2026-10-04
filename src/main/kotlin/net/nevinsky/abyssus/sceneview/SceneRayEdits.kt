/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.filetype.editSceneJson

/** Platform adapter: every accepted edit is one native document command. */
object SceneRayEdits {
    fun setting(project: Project,file: VirtualFile,field: SceneRayField,expected: JsonNode?,text: String): RayDataEdit =
        edit(project,file) { SceneRaySettingsCodec().edit(it,field,expected,text) }

    fun material(project: Project,file: VirtualFile,entity: String,id: String,field: RayOpticalField,expected: JsonNode?,text: String,
        identities: List<RayMaterialIdentity>): RayDataEdit = edit(project,file) { root ->
        val render=root.path("ecs").path("entities").path(entity).path("components").get("RenderComponent") as? ObjectNode
            ?: return@edit RayDataEdit.Conflict
        RayMaterialOverrides().edit(render,id,field,expected,text,identities)
    }

    private fun edit(project: Project,file: VirtualFile,mutate: (ObjectNode)->RayDataEdit): RayDataEdit {
        if(file.extension!="scene") return RayDataEdit.Rejected(RayDataError.OBJECT)
        var result: RayDataEdit=RayDataEdit.Rejected(RayDataError.OBJECT)
        editSceneJson(project,file,AbyssusBundle.message("commandEditSceneRaySettings")) { root ->
            result=mutate(root as ObjectNode)
            result==RayDataEdit.Changed
        }
        return result
    }
}
