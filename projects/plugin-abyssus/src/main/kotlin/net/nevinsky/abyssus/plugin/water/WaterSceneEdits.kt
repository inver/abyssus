/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.water

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.editor.water.WaterEdit
import net.nevinsky.abyssus.lib.core.editor.water.WaterEdits
import net.nevinsky.abyssus.lib.core.water.WaterField
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.filetype.editSceneJson

/** A finished [add]: the new surface's id when it was written. */
data class WaterAdded(val id: String?, val edit: WaterEdit)

/**
 * Platform adapter over [WaterEdits]: each accepted edit is one undoable scene command through [editSceneJson], the
 * only writer. A rejected or no-op edit creates no undo step and writes nothing.
 */
class WaterSceneEdits(private val project: Project, private val edits: WaterEdits = WaterEdits()) {
    /** Adds a [preset] surface centred on ([x], [z]) at [level]; its id is known only when the edit was written. */
    fun add(file: VirtualFile, preset: WaterPreset, x: Double, level: Double, z: Double): WaterAdded {
        var id: String? = null
        val result = run(file, "commandAddWater", global = true) { root ->
            val settings = edits.create(root, preset, x, level, z)
            edits.add(root, settings).also { if (it == WaterEdit.Changed) id = settings.id }
        }
        return WaterAdded(id.takeIf { result == WaterEdit.Changed }, result)
    }

    fun setField(file: VirtualFile, id: String, field: WaterField, value: Double): WaterEdit =
        run(file, "commandEditWater") { edits.setField(it, id, field, value) }

    fun move(file: VirtualFile, id: String, x: Double, level: Double, z: Double): WaterEdit =
        run(file, "commandMoveWater") { edits.move(it, id, x, level, z) }

    /** Moves only the given coordinates of the surface (as one command); values equal to the saved ones are no-ops. */
    fun move(file: VirtualFile, id: String, values: Map<WaterField, Double>): WaterEdit =
        run(file, "commandMoveWater") { edits.setFields(it, id, values) }

    fun rename(file: VirtualFile, id: String, name: String): WaterEdit =
        run(file, "commandRenameWater", global = true) { edits.rename(it, id, name) }

    fun setEnabled(file: VirtualFile, id: String, enabled: Boolean): WaterEdit =
        run(file, "commandToggleWater") { edits.setEnabled(it, id, enabled) }

    fun remove(file: VirtualFile, id: String): WaterEdit =
        run(file, "commandRemoveWater", global = true) { edits.remove(it, id) }

    /** [global]: the edit starts from the Abyssus tree, so Undo and Redo must reach it with the tree focused, not only from the scene's editor. */
    private fun run(file: VirtualFile, command: String, global: Boolean = false, edit: (com.fasterxml.jackson.databind.JsonNode) -> WaterEdit): WaterEdit {
        var result: WaterEdit = WaterEdit.Unchanged
        val written = editSceneJson(project, file, AbyssusBundle.message(command), global) { root ->
            result = edit(root)
            result == WaterEdit.Changed
        }
        return if (result == WaterEdit.Changed && !written) WaterEdit.Unchanged else result
    }
}
