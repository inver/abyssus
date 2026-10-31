/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.splat

import net.nevinsky.abyssus.plugin.assetfiles.strokeResultMessage
import net.nevinsky.abyssus.plugin.assetfiles.assetFilesAffected
import com.badlogic.gdx.math.Vector3
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.ImageIoSplatCodec
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatBrush
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatDraft
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatImageCodec
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatPixels
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatState
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatTool
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileCommand
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileStore
import net.nevinsky.abyssus.plugin.brush.TerrainStrokeCommand
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage

/**
 * One Scene view's Paint Texture brush: the press opens a [SplatDraft] in the project's [SplatDrafts] from the splat
 * image (every view shows it as it changes), the drags stamp the tool of the mode into it, and the release writes the
 * image as one undoable command. Nothing is written before the release, a stroke that changed no byte writes nothing,
 * and the draft is kept until the write has been applied (so no view reloads the old image early) and then dropped
 * whatever happened. The PNG is encoded on [background] and applied on [ui]; a [generation] guard drops the result of a
 * stroke the view has since closed. AWT thread.
 *
 * [readState] gives the splat state to paint (re-read when the file changed), and `terrainFolder` names the terrain
 * asset whose `meta.json` the undo step joins.
 */
class SplatStrokeCommand(
    private val project: Project,
    private val terrainFolder: VirtualFile,
    private val drafts: SplatDrafts,
    private val transactions: SplatTransactions,
    private val store: AssetFileStore,
    private val readState: () -> SplatState,
    mode: () -> BrushMode?,
    private val background: (Runnable) -> Unit,
    private val ui: (Runnable) -> Unit,
    private val report: (String) -> Unit,
    private val codec: SplatImageCodec = ImageIoSplatCodec(),
) : TerrainStrokeCommand<SplatStrokeCommand.Stroke>(mode) {
    class Stroke(val state: SplatState.Ready, val draft: SplatDraft, val brush: SplatBrush, var last: Vector3)

    @Volatile
    private var disposed = false

    @Volatile
    private var generation = 0
    private var committing = false

    override fun begin(terrain: TerrainTarget, at: Vector3, mode: BrushMode): Stroke? {
        if (disposed || committing) return null
        val state = readState() as? SplatState.Ready ?: return null
        val splatFolder = state.folder.name
        if (drafts.of(splatFolder) != null) return null
        val options = mode.splat
        val draft = drafts.open(splatFolder) { SplatDraft(state.pixels.side, terrain.data.size, state.pixels.rgba.copyOf()) }
        return Stroke(state, draft, SplatBrush(draft, options.exclusive, terrain.world), Vector3(at))
    }

    override fun press(stroke: Stroke, at: Vector3, mode: BrushMode) {
        val options = mode.splat
        if (options.mode == SplatMode.FILL) {
            stroke.draft.fill(options.slot)
        } else {
            stamp(stroke, at, at, mode, mode.reversed)
        }
    }

    override fun drag(stroke: Stroke, to: Vector3, reversed: Boolean) {
        val m = mode() ?: return
        if (m.splat.mode != SplatMode.FILL) stamp(stroke, stroke.last, to, m, reversed)
        stroke.last = Vector3(to)
    }

    private fun stamp(stroke: Stroke, from: Vector3, to: Vector3, mode: BrushMode, reversed: Boolean) {
        val options = mode.splat
        val tool = when (options.mode) {
            SplatMode.PAINT -> SplatTool.Paint(options.slot)
            SplatMode.ERASE -> SplatTool.Erase
            SplatMode.SMOOTH -> SplatTool.Smooth
            SplatMode.FILL -> return
        }
        stroke.brush.stroke(from, to, mode.radius, mode.strength, tool, reversed && options.mode == SplatMode.PAINT)
    }

    override fun released() {
        val stroke = active ?: return
        active = null
        val folder = stroke.state.folder.name
        if (stroke.draft.changed.isEmpty || stroke.draft.pixels.contentEquals(stroke.state.pixels.rgba)) {
            drafts.discard(folder)
            return
        }
        committing = true
        val token = ++generation
        val pixels = SplatPixels(stroke.draft.resolution, stroke.draft.pixels.copyOf())
        background {
            val encoded = runCatchingKeepingCancellation { codec.encode(pixels) }
            ui {
                if (disposed || token != generation) {
                    // the view closed: the stroke is dropped and the views load the image from its file again
                    drafts.discard(folder)
                    return@ui
                }
                try {
                    encoded.fold({ commit(stroke, it) }, { report(AbyssusBundle.message("splatStrokeFailed", it.documentDisplayMessage())) })
                } finally {
                    committing = false
                    drafts.discard(folder)
                }
            }
        }
    }

    override fun cancelled() {
        val stroke = active
        active = null
        if (stroke != null) drafts.discard(stroke.state.folder.name)
    }

    override fun dispose() {
        disposed = true
        generation++
        committing = false
        cancelled()
    }

    private fun commit(stroke: Stroke, image: ByteArray) {
        val state = stroke.state
        val splatDir = "$ASSETS_DIR/${state.folder.name}"
        val txn = transactions.stroke(
            AbyssusBundle.message("commandPaintTexture"), state.folder.name, state.file.name, state.fileBytes, image,
            expected = listOf("$splatDir/$META_FILE", "$ASSETS_DIR/${terrainFolder.name}/$META_FILE"),
        ) ?: return
        val result = AssetFileCommand(project, store).execute(
            txn,
            affected = { assetFilesAffected(terrainFolder, state.file) },
        )
        strokeResultMessage(result, "splatStrokeConflict", "splatStrokeFailed")?.let(report)
    }
}
