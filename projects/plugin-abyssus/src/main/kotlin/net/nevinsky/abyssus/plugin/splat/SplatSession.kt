/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.splat

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.editor.meta.AssetMetaEditor
import net.nevinsky.abyssus.lib.core.editor.meta.FieldValue
import net.nevinsky.abyssus.lib.core.editor.pick.BrushStroke
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SPLAT_FILE_NAME
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatAssetWriter
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatLayer
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatLayers
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatPlan
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatState
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatStateReader
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileCommand
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileStore
import net.nevinsky.abyssus.plugin.assetfiles.FileSnapshot
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.assetfiles.AssetEditResult
import net.nevinsky.abyssus.plugin.assetfiles.AssetMetaEdits
import java.io.File

/** What the Paint Texture strip shows: the splat state of the terrain, its five layer slots and the terrain `meta.json` they were read from. */
class SplatSnapshot(val state: SplatState, val layers: List<SplatLayer>, val metaText: String)

/**
 * What a Scene view knows about painting the selected terrain: the [stroke] that paints (it paints nothing while the splat
 * map cannot be), a [snapshot] of the splat map and layers, and the three operations the strip offers on them: Create
 * splat map, Make unique copy and assigning a texture to a layer slot. Every file write is one undoable command.
 * [snapshot] reads files, so off the EDT; the operations run on it.
 */
class SplatSession(
    private val project: Project,
    val terrainFolder: VirtualFile,
    private val projectDir: File,
    private val json: JsonProcessor,
    private val store: AssetFileStore,
    private val assetEditor: AssetMetaEditor,
    val stroke: BrushStroke?,
    private val closeStroke: () -> Unit,
    private val cache: SplatStateCache,
) {
    private val writer = SplatAssetWriter(json, ImageIoSplatCodec())
    private val transactions = SplatTransactions(store) { File(projectDir, ASSETS_DIR).isDirectory }
    private val layers = SplatLayers(json)

    val assetsDir: File get() = File(projectDir, ASSETS_DIR)

    /** The state and layers now; reads files and decodes the image, so off the EDT. */
    fun snapshot(): SplatSnapshot {
        val state = cache.refresh()
        val text = cache.metaText()
        return SplatSnapshot(state, layers.read(assetsDir, text), text)
    }

    /** Choices for a slot currently holding [current]: none, the unresolved current value, then the project's readable textures. */
    fun choices(current: String?) = layers.choices(assetsDir, current)

    /** Create splat map: a blank map of [resolution] pixels, as one undoable step. UI thread. */
    fun create(resolution: Int, report: (String) -> Unit, onDone: () -> Unit = {}): AssetCommandResult? =
        plan(report, "commandCreateSplatMap") { text -> writer.create(projectDir, terrainFolder.name, text, resolution) }
            ?.also { if (it == AssetCommandResult.Done) onDone() }

    /** Make unique copy: this terrain gets its own copy of the shared map. UI thread. */
    fun makeUnique(report: (String) -> Unit, onDone: () -> Unit = {}): AssetCommandResult? {
        val ready = cache.latest() as? SplatState.Ready ?: return null
        return plan(report, "commandMakeUniqueSplatMap") { text -> writer.uniqueCopy(projectDir, terrainFolder.name, text, ready.pixels) }
            ?.also { if (it == AssetCommandResult.Done) onDone() }
    }

    private fun plan(report: (String) -> Unit, command: String, make: (String) -> SplatPlan): AssetCommandResult? {
        val metaPath = "$ASSETS_DIR/${terrainFolder.name}/$META_FILE"
        val before = (store.read(metaPath) as? FileSnapshot.Bytes)?.toByteArray()
        if (before == null) { report(AbyssusBundle.message("splatCreateFailed", metaPath)); return null }
        val files = when (val plan = make(before.toString(Charsets.UTF_8))) {
            is SplatPlan.Planned -> plan.files
            is SplatPlan.Refused -> { report(AbyssusBundle.message("splatCreateFailed", plan.problem.name)); return null }
        }
        val txn = transactions.create(AbyssusBundle.message(command), terrainFolder.name, files, before, SPLAT_FILE_NAME)
        val result = AssetFileCommand(project, store).execute(txn, affected = { emptyList() })
        when (result) {
            AssetCommandResult.Done -> Unit
            AssetCommandResult.Cancelled -> report(AbyssusBundle.message("splatCreateCancelled"))
            is AssetCommandResult.Conflict, is AssetCommandResult.Collision ->
                report(AbyssusBundle.message("splatCreateFailed", AbyssusBundle.message("splatStrokeConflict")))
            is AssetCommandResult.Blocked -> report(result.reason)
            is AssetCommandResult.Failed -> report(AbyssusBundle.message("splatCreateFailed", result.cause.message.orEmpty()))
        }
        cache.invalidate()
        return result
    }

    /**
     * Stores the texture asset with [uuid] in the layer [layer] (null clears it) through the same meta edit as the
     * Properties panel's pickers: one undoable step on the terrain's `meta.json`. [expected] is the value the strip showed.
     */
    fun assign(layer: SplatLayer, uuid: String?): AssetEditResult {
        val expected = layer.uuid?.let { FieldValue.Text(it) } ?: FieldValue.None
        val value = uuid?.let { FieldValue.Text(it) } ?: FieldValue.None
        return AssetMetaEdits.update(project, terrainFolder, layer.field, expected, value, assetEditor)
    }

    fun dispose() = closeStroke()
}

/**
 * The splat state of one terrain, read once and kept while it is still what the files say: the press of a stroke asks for
 * it on the UI thread and must not decode a large PNG each time. It is current while the image has the length and time it
 * had and the terrain's `meta.json` text is the same.
 */
class SplatStateCache(
    private val reader: SplatStateReader,
    private val assetsDir: File,
    private val terrainFolder: VirtualFile,
) {
    @Volatile
    private var state: SplatState? = null

    @Volatile
    private var text: String = ""

    /** The terrain `meta.json` as the editors hold it. */
    fun metaText(): String = runReadAction { terrainFolder.findChild(META_FILE)?.let(::textOf).orEmpty() }

    /** The cached state when it is still current, else a fresh read. */
    fun current(): SplatState {
        val cached = state
        if (cached != null && metaText() == text && (cached !is SplatState.Ready || cached.isCurrent())) return cached
        return refresh()
    }

    /** The last state read, whether or not it is still current; null before the first read. */
    fun latest(): SplatState? = state

    fun refresh(): SplatState {
        val now = metaText()
        return reader.read(assetsDir, File(terrainFolder.path), now).also { state = it; text = now }
    }

    fun invalidate() {
        state = null
    }
}
