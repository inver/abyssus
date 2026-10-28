/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.document.DocumentEdit
import com.badlogic.gdx.math.Vector3
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.editor.arrange.ArrangementEdits
import net.nevinsky.abyssus.lib.core.editor.arrange.ArrangementResult
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.filetype.editSceneJson

/**
 * Writes path arrangements to a scene file, each edit as one undoable command through `editSceneJson`; the rules are
 * [ArrangementEdits]'. [Changed][ArrangementResult.Changed] means the file was written.
 */
object ArrangementEditing {
    private fun run(project: Project, file: VirtualFile, command: String, edit: DocumentEdit<ArrangementResult>): ArrangementResult {
        var result: ArrangementResult = ArrangementResult.Rejected(EditorBundle.message("componentSceneUnreadable"))
        val written = editSceneJson(project, file, command) { root ->
            result = edit(root)
            result is ArrangementResult.Changed
        }
        return if (result is ArrangementResult.Changed && !written) ArrangementResult.Unchanged else result
    }

    /** Adds an arrangement of the model [asset] along [points] and its copies; the result names the new arrangement. */
    fun create(project: Project, file: VirtualFile, asset: BaseMeta, points: List<Vector3>, closed: Boolean, heights: GroundHeight): ArrangementResult =
        run(project, file, AbyssusBundle.message("commandAddPathArrangement")) {
            ArrangementEdits(EditorBundle).create(it, asset, points, closed, heights)
        }

    /** Gives the arrangement [entityId] new [points] (a dragged, inserted or removed point) and regenerates its copies. */
    fun setPoints(project: Project, file: VirtualFile, entityId: String, points: List<Vector3>, heights: GroundHeight): ArrangementResult =
        run(project, file, AbyssusBundle.message("commandEditPath")) {
            ArrangementEdits(EditorBundle).setPoints(it, entityId, points, heights)
        }

    /** Re-places the copies of [entityId] on [heights] without filling empty slots; nothing is written when none would change. */
    fun regenerate(project: Project, file: VirtualFile, entityId: String, heights: GroundHeight): ArrangementResult =
        run(project, file, AbyssusBundle.message("commandRegenerateArrangement")) {
            ArrangementEdits(EditorBundle).regenerate(it, entityId, null, heights)
        }

    /** Tells the user why an edit was refused; does nothing for any other result. */
    fun report(project: Project, result: ArrangementResult) {
        if (result is ArrangementResult.Rejected) {
            Messages.showErrorDialog(project, result.reason, AbyssusBundle.message("componentRejectedTitle"))
        }
    }
}
