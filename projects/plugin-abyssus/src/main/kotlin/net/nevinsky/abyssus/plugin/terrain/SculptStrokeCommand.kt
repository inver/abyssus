/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.terrain

import com.badlogic.gdx.math.Vector3
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.plugin.brush.TerrainStrokeCommand
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptBrush
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptDraft
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptState
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptTool
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.decodeSculptGrid
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileCommand
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileStore
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import java.io.File

/**
 * One Scene view's Sculpt Terrain brush: the press opens a [SculptDraft] in the project's [SculptDrafts] from the
 * terrain's files, the drags stamp the [tool] into it (every view shows it as it changes), and the release writes
 * `terrain.data`, the delta, the manifest and, for a first stroke without a usable recipe, the stored base as one
 * undoable command. Nothing is written before the release, an empty stroke writes nothing, and the draft is dropped
 * whatever happens so every view loads the terrain from its files again. AWT thread.
 */
class SculptStrokeCommand(
    private val project: Project,
    private val folder: VirtualFile,
    private val drafts: SculptDrafts,
    private val sculpt: SculptServices,
    private val store: AssetFileStore,
    private val readCurrent: () -> TerrainSource,
    mode: () -> BrushMode?,
    private val tool: () -> SculptTool?,
    private val report: (String) -> Unit,
) : TerrainStrokeCommand<SculptStrokeCommand.Stroke>(mode) {
    class Stroke(
        val source: TerrainSource.Ready,
        val draft: SculptDraft,
        val brush: SculptBrush,
        val tool: SculptTool,
        var last: Vector3,
    )

    override fun begin(terrain: TerrainTarget, at: Vector3, mode: BrushMode): Stroke? {
        val picked = tool() ?: return null
        val source = when (val read = readCurrent()) {
            is TerrainSource.Unusable -> { report(read.reason); return null }
            is TerrainSource.Ready -> read
        }
        val files = source.files ?: return null
        val state = source.sculpt
        if (state is SculptState.Unsafe) {
            report(AbyssusBundle.message("sculptUnavailableUnsafe"))
            return null
        }
        val composed = decodeSculptGrid(files.terrainBytes, source.resolution) ?: return null
        if (terrain.data.resolution != source.resolution) return null
        val draft = drafts.open(folder.name) {
            val sculpted = state as? SculptState.Sculpted
            SculptDraft(source.resolution, sculpted?.base ?: composed.copyOf(), composed, sculpted?.delta?.copyOf() ?: FloatArray(composed.size))
        }
        return Stroke(source, draft, SculptBrush(draft, source.size, terrain.world), picked, Vector3(at))
    }

    override fun press(stroke: Stroke, at: Vector3, mode: BrushMode) {
        stroke.brush.stroke(at, at, mode.radius, mode.strength, stroke.tool, mode.reversed)
    }

    override fun drag(stroke: Stroke, to: Vector3, reversed: Boolean) {
        val m = mode() ?: return
        stroke.brush.stroke(stroke.last, to, m.radius, m.strength, stroke.tool, reversed)
        stroke.last = Vector3(to)
    }

    override fun released() {
        val stroke = active ?: return
        active = null
        try {
            if (stroke.draft.changed.isEmpty) return
            commit(stroke)
        } finally {
            drafts.discard(folder.name)
        }
    }

    override fun cancelled() {
        if (active == null) return
        active = null
        drafts.discard(folder.name)
    }

    private fun commit(stroke: Stroke) {
        val current = readCurrent() as? TerrainSource.Ready
        if (current == null || current.source != stroke.source.source) {
            report(AbyssusBundle.message("sculptStrokeConflict"))
            return
        }
        stroke.draft.recompose()
        val set = sculpt.planner.commitStroke(checkNotNull(current.files), current.sculpt, stroke.draft)
        val txn = SculptTransactions(store).plan(
            AbyssusBundle.message("commandSculptStroke"), current.folderName, current.dataName, set,
            expected = mapOf(current.metaPath to current.metaBytes),
        ) ?: return
        val result = AssetFileCommand(project, store).execute(
            txn,
            affected = { listOfNotNull(folder.findChild("meta.json"), LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(folder.path, current.dataName))) },
        )
        when (result) {
            AssetCommandResult.Done -> Unit
            is AssetCommandResult.Conflict -> report(AbyssusBundle.message("sculptStrokeConflict"))
            is AssetCommandResult.Collision -> report(AbyssusBundle.message("sculptStrokeConflict"))
            is AssetCommandResult.Blocked -> report(result.reason)
            AssetCommandResult.Cancelled -> Unit
            is AssetCommandResult.Failed -> report(AbyssusBundle.message("sculptStrokeFailed", result.cause.documentDisplayMessage()))
        }
    }
}

/**
 * Reads the terrain asset in [folder] as it is now (the editors' unsaved `meta.json` text included): usable, with its
 * sculpt state, or why not. Reads files, so a caller on the EDT keeps it to the one small terrain a stroke starts on.
 */
fun readTerrainFolder(
    folder: VirtualFile,
    json: net.nevinsky.abyssus.lib.core.io.JsonProcessor,
    recipes: net.nevinsky.abyssus.lib.core.editor.terrain.TerrainRecipeCodec,
    sculpt: SculptServices,
): TerrainSource {
    val metaFile = folder.findChild("meta.json") ?: return TerrainSource.Unusable("meta.json")
    val text = com.intellij.openapi.application.runReadAction { net.nevinsky.abyssus.plugin.dto.textOf(metaFile) }
    val meta = net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation {
        net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parse(text)
    }.getOrElse { return TerrainSource.Unusable("meta.json") }
    return readTerrainSource(File(folder.path), text, meta, net.nevinsky.abyssus.lib.core.editor.meta.AssetReferenceChoices(json), recipes, sculpt.reader)
}
