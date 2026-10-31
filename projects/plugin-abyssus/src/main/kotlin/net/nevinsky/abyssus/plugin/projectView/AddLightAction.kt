/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.editor.host.*
import net.nevinsky.abyssus.lib.core.util.isObjectAt
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.components.LightPreset
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.projectView.scene.SceneComponentEdits
import net.nevinsky.abyssus.plugin.ui.reportRejection
import net.nevinsky.abyssus.plugin.ui.showActionGroup

/** The same three choices in the tree and toolbar; placement is read when a choice is made. */
class AddLightGroup(
    private val project: Project,
    private val file: VirtualFile,
    private val position: () -> Vector3,
    private val select: (String) -> Unit = { selectCreatedEntity(project, file, it) },
) : AddChildGroup<LightPreset>(AbyssusBundle.message("addLightTitle")) {
    init {
        for (preset in LightPreset.entries) addChild(preset, EditorBundle.message(preset.labelKey))
    }

    override fun canAdd(target: LightPreset) = canAddLight(file, SceneDocumentCache.of(project))

    override fun addTarget(target: LightPreset) {
        val added = SceneComponentEdits.addLight(project, file, target, position(), SceneDocumentCache.of(project))
        reportRejection(project, added.result)
        added.entityId?.let(select)
    }
}

/** Publish a new entity immediately, then select its row when the asynchronous tree refresh reaches it. */
internal fun selectCreatedEntity(project: Project, file: VirtualFile, entityId: String) {
    val root = runCatchingKeepingCancellation { parseDocument(textOf(file)) }.getOrNull()
    val entity = root?.at("ecs", entityId)
    if (entity?.isObjectAt() == true) {
        val node = DtoEntryNode(project, file.path, DtoRow(entityId, entity), file, SceneEntityTree(root).entityKeys())
        AbyssusSelection.of(project).select(node)
    }
    selectEntityInAbyssusView(project, file, entityId)
}

internal fun canAddLight(file: VirtualFile, cache: SceneDocumentCache): Boolean =
    cache.read(file)?.let { SceneEntityTree(it.root).canAdd() } ?: false

/** Only scene rows can create an entity; the tree uses the origin as its placement point. */
open class AddLightAction : AbyssusTreeAction<VirtualFile>() {
    init {
        templatePresentation.text = AbyssusBundle.message("addLightTitle")
    }

    override fun targetOf(node: Any?): VirtualFile? = viewableSceneFile(node)

    override fun isEnabled(e: AnActionEvent, target: VirtualFile) =
        e.project?.let { canAddLight(target, SceneDocumentCache.of(it)) } ?: false

    override fun perform(project: Project, target: VirtualFile, e: AnActionEvent) {
        showActionGroup(e, AddLightGroup(project, target, { Vector3(0f, 0f, 0f) }), AbyssusBundle.message("addLightTitle"))
    }
}
