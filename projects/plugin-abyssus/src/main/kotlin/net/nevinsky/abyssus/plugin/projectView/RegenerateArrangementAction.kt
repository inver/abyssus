/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import net.nevinsky.abyssus.lib.core.editor.arrange.ARRANGEMENT_COMPONENT
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.sceneview.ArrangementEditing
import net.nevinsky.abyssus.plugin.sceneview.ArrangementHeights

/**
 * Regenerate on the tree row of a path arrangement: re-places each of its copies from the current terrain and settings,
 * leaves empty slots empty, and writes nothing when no copy would change.
 */
open class RegenerateArrangementAction : AbyssusTreeAction<ComponentTarget>() {
    init { templatePresentation.text = AbyssusBundle.message("regenerateArrangementTitle") }

    override fun targetOf(node: Any?): ComponentTarget? {
        val entry = (node as? DtoEntryNode)?.value ?: return null
        val json = entry.value as? com.fasterxml.jackson.databind.JsonNode ?: return null
        val target = componentTargetOf(node)?.takeIf { it.kind == null } ?: return null
        return target.takeIf { json.get("components")?.has(ARRANGEMENT_COMPONENT) == true }
    }

    override fun perform(project: Project, target: ComponentTarget, e: AnActionEvent) {
        val heights = ArrangementHeights.of(project).forScene(target.file) ?: return
        ArrangementEditing.report(project, ArrangementEditing.regenerate(project, target.file, target.entityId, heights))
    }
}
