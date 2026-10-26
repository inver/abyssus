/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import net.nevinsky.abyssus.plugin.ui.showActionGroup
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.water.WaterCodec
import net.nevinsky.abyssus.lib.core.editor.water.WaterEdit
import net.nevinsky.abyssus.plugin.water.reportWaterRejection
import net.nevinsky.abyssus.lib.core.editor.water.WaterSurface
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.water.WATER_ROW
import net.nevinsky.abyssus.plugin.water.WaterSceneEdits
import net.nevinsky.abyssus.plugin.water.waterErrorText
import net.nevinsky.abyssus.plugin.water.waterPresetLabel

/** A water surface of a scene file, as a tree row or the Scene view's selection stands for it. */
data class WaterTarget(val file: VirtualFile, val id: String, val name: String)

/** The water surface the Abyssus view row [node] stands for, or null for any other node. */
fun waterTargetOf(node: Any?): WaterTarget? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    val surface = entry.value as? WaterSurface ?: return null
    val file = entry.source?.takeIf { it.isValid } ?: return null
    return WaterTarget(file, surface.id, surface.name)
}

/** Whether water can be added to [file]: a readable scene whose `abyssus` and `waterSurfaces` are objects when present. */
internal fun canAddWater(file: VirtualFile, cache: SceneDocumentCache): Boolean =
    cache.read(file)?.let { !WaterCodec().read(it.root.get("abyssus")).incompatible } ?: false

/** Select the new surface at once, then its row when the asynchronous tree refresh reaches it. */
internal fun selectCreatedWater(project: Project, file: VirtualFile, id: String) {
    val surface = runCatchingKeepingCancellation { WaterCodec().read(SceneJson().parse(textOf(file)).get("abyssus")) }
        .getOrNull()?.surfaces?.firstOrNull { it.id == id }
    if (surface != null) {
        val node = DtoEntryNode(
            project,
            "${file.path}/$WATER_ROW",
            DtoRow(id, surface, (surface as? net.nevinsky.abyssus.lib.core.editor.water.ValidWater)?.settings?.enabled),
            file,
            listOf("abyssus", "waterSurfaces")
        )
        AbyssusSelection.of(project).select(node)
    }
    selectWaterInAbyssusView(project, file, id)
}

/** The same two presets in the tree and the Scene view toolbar; placement is read when a choice is made. */
class AddWaterGroup(
    private val project: Project,
    private val file: VirtualFile,
    private val position: () -> Vector3,
    private val select: (String) -> Unit = { selectCreatedWater(project, file, it) },
) : AddChildGroup<WaterPreset>(AbyssusBundle.message("addWaterTitle")) {
    init { for (preset in WaterPreset.entries) addChild(preset, waterPresetLabel(preset)) }

    override fun canAdd(target: WaterPreset) = canAddWater(file, SceneDocumentCache.of(project))
    override fun addTarget(target: WaterPreset) = addWater(target)

    /** Adds a [preset] surface at the position, reports a refusal and selects the new one. */
    internal fun addWater(preset: WaterPreset) {
        val at = position()
        val added = WaterSceneEdits(project).add(file, preset, at.x.toDouble(), at.y.toDouble(), at.z.toDouble())
        reportWaterRejection(project, added.edit)
        added.id?.let(select)
    }
}

/** Only scene rows can take water; the tree places it at the origin. */
open class AddWaterAction : AbyssusTreeAction<VirtualFile>() {
    init {
        templatePresentation.text = AbyssusBundle.message("addWaterTitle")
    }

    override fun targetOf(node: Any?): VirtualFile? = viewableSceneFile(node)

    override fun isEnabled(e: AnActionEvent, target: VirtualFile) =
        e.project?.let { canAddWater(target, SceneDocumentCache.of(it)) } ?: false

    override fun perform(project: Project, target: VirtualFile, e: AnActionEvent) {
        showActionGroup(e, AddWaterGroup(project, target, { Vector3(0f, 0f, 0f) }), AbyssusBundle.message("addWaterTitle"))
    }
}

/** Right-click "Rename Water..." on a water row. */
open class RenameWaterAction : AbyssusTreeAction<WaterTarget>() {
    init {
        templatePresentation.text = AbyssusBundle.message("renameWaterTitle") + "..."
    }

    override fun targetOf(node: Any?): WaterTarget? = waterTargetOf(node)

    override fun perform(project: Project, target: WaterTarget, e: AnActionEvent) {
        val name = Messages.showInputDialog(
            project,
            AbyssusBundle.message("renameWaterPrompt"),
            AbyssusBundle.message("renameWaterTitle"),
            null,
            target.name,
            null,
        )?.trim()
        if (!name.isNullOrEmpty() && name != target.name) reportWaterRejection(
            project,
            WaterSceneEdits(project).rename(target.file, target.id, name)
        )
    }
}

/** Right-click "Remove Water" on a water row; removes only that surface. */
open class RemoveWaterAction : AbyssusTreeAction<WaterTarget>() {
    init {
        templatePresentation.text = AbyssusBundle.message("removeWaterTitle")
    }

    override fun targetOf(node: Any?): WaterTarget? = waterTargetOf(node)

    override fun perform(project: Project, target: WaterTarget, e: AnActionEvent) {
        reportWaterRejection(project, WaterSceneEdits(project).remove(target.file, target.id))
    }
}
