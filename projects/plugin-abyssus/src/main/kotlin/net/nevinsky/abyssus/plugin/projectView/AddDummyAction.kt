/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.editor.components.AddedEntity
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.editor.dummy.DummyEntities
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.filetype.editSceneDocument
import net.nevinsky.abyssus.plugin.ui.reportRejection

/**
 * Add Dummy on an entity row (OpenSpec `scene-dummies`, "Add Dummy"): one dummy as a child of that entity at its
 * origin, in one undoable edit, then selected. Not offered on a path arrangement (its copies have no rows).
 */
open class AddDummyAction : AbyssusTreeAction<ComponentTarget>() {
    init {
        templatePresentation.text = AbyssusBundle.message("addDummyTitle")
    }

    override fun targetOf(node: Any?): ComponentTarget? = entityTargetOf(node) { _, entity ->
        entity.component(PathArrangementComponent::class.java) == null
    }

    override fun perform(project: Project, target: ComponentTarget, e: AnActionEvent) {
        val added = addDummy(project, target.file, target.entityId)
        reportRejection(project, added.result)
        added.entityId?.let { selectCreatedEntity(project, target.file, it) }
    }
}

/** Adds a dummy under [parentId] of [file] as one undoable command. */
internal fun addDummy(project: Project, file: VirtualFile, parentId: Long): AddedEntity {
    var added = AddedEntity(EditResult.Unchanged)
    editSceneDocument(project, file, AbyssusBundle.message("commandAddDummy")) { scene ->
        added = DummyEntities(EditorBundle).add(scene, parentId)
        added.result == EditResult.Changed
    }
    return added
}
