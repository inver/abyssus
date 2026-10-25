/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.foliage

import com.badlogic.gdx.math.Vector3
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDataFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.assets.foliage.foliageMaskFileName
import net.nevinsky.abyssus.lib.core.assets.foliage.fullMask
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.brush.GridRect
import net.nevinsky.abyssus.lib.core.editor.brush.emptyGridRect
import net.nevinsky.abyssus.lib.core.editor.foliage.*
import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.plugin.brush.TerrainStrokeCommand
import net.nevinsky.abyssus.lib.core.editor.pick.TerrainTarget
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.DocumentServices
import net.nevinsky.abyssus.plugin.assetfiles.*
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import java.io.File

/** One view's brush adapter. Stamps are CPU-only; release stages its bake on the supplied background scheduler. */
class FoliageStrokeCommand(
    private val project: Project,
    private val scene: VirtualFile,
    private val folder: VirtualFile,
    initial: FoliageSource.Ready,
    private val drafts: FoliageDrafts,
    private val documents: DocumentServices,
    private val undoCache: FoliageUndoCache,
    mode: () -> BrushMode?,
    private val background: (Runnable) -> Unit,
    private val ui: (Runnable) -> Unit,
    private val report: (String) -> Unit,
    private val readCurrent: (() -> FoliageSource)? = null,
) : TerrainStrokeCommand<FoliageStrokeCommand.Stroke>(mode) {
    private var source = initial
    @Volatile
    private var disposed = false
    @Volatile
    private var generation = 0
    private val stagedBackups = HashSet<FoliageUndoCache.Handle>()
    private val meta get() = folder.findChild("meta.json")
    private val projectDir = File(folder.path).parentFile.parentFile

    override fun begin(terrain: TerrainTarget, at: Vector3, mode: BrushMode): Stroke? {
        val selected = mode
        if (disposed || source.settings.layers.none { it.id == selected.layerId } || source.read.problems.isNotEmpty()) return null
        val existing = drafts.of(source.folderName)
        if (existing?.settings?.let { it != source.settings } == true || existing?.masks?.isNotEmpty() == true) {
            report(AbyssusBundle.message("foliagePendingSettings"))
            return null
        }
        val draft = drafts.open(source.folderName, source.terrain, source.settings.maskResolution)
        draft.beginStroke()
        val mask = draft.editableMask(selected.layerId, source.masks.getValue(selected.layerId))
        return Stroke(
            source, draft, selected, FoliageBrush(terrain.data, source.settings.maskResolution, terrain.world),
            Vector3(at), mask, ++generation
        )
    }

    override fun press(stroke: Stroke, at: Vector3, mode: BrushMode) = stamp(stroke, at, mode.reversed)

    override fun drag(stroke: Stroke, to: Vector3, reversed: Boolean) {
        if (!stroke.releasing) stamp(stroke, to, reversed)
    }

    private fun stamp(stroke: Stroke, to: Vector3, reversed: Boolean) {
        val rect = stroke.brush.stroke(
            stroke.mask, stroke.point, to, stroke.mode.radius, stroke.mode.strength,
            if (reversed) FoliageBrushMode.Erase else FoliageBrushMode.Paint
        )
        stroke.point.set(to)
        stroke.rect = stroke.rect.union(rect)
        stroke.draft.stamped(stroke.mode.layerId, rect)
        stroke.revision = stroke.draft.revision
    }

    override fun released() {
        val stroke = active ?: return
        if (stroke.releasing) return
        if (stroke.rect.isEmpty || stroke.mask.contentEquals(stroke.source.masks.getValue(stroke.mode.layerId))) {
            discard(stroke)
            return
        }
        stroke.releasing = true
        val metadata = meta ?: run { discard(stroke); return }
        // Capture the document on EDT; the worker uses this immutable text, never a Document or VFS read.
        val currentText = textOf(metadata)
        val settings = stroke.source.settings
        val masks = stroke.source.masks + (stroke.mode.layerId to stroke.mask.copyOf())
        background(Runnable {
            val staged = runCatchingKeepingCancellation {
                val parsing = documents.parsing
                parsing.requireSupported(currentText, DocumentKind.ASSET)
                val maskPresent = File(folder.path, foliageMaskFileName(stroke.mode.layerId)).isFile
                val expected = settings.layers.filter { it.id != stroke.mode.layerId }.associate { layer ->
                    val path = "assets/${folder.name}/${foliageMaskFileName(layer.id)}"
                    path to if (File(projectDir, path).isFile)
                        FileSnapshot.Bytes(stroke.source.masks.getValue(layer.id)) else FileSnapshot.Absent
                }.toMutableMap()
                // Capture terrain dependencies before the fresh read. A change during or after it is rejected
                // by the transaction, including the gap between worker publication and the UI write command.
                val terrainFolder = File(projectDir, "assets/${settings.terrain}")
                val terrainMeta = File(terrainFolder, "meta.json").readBytes()
                expected["assets/${settings.terrain}/meta.json"] = FileSnapshot.Bytes(terrainMeta)
                val terrainFile = parsing.assetFields(terrainMeta.toString(Charsets.UTF_8)).terrainFile
                expected["assets/${settings.terrain}/$terrainFile"] =
                    FileSnapshot.Bytes(File(terrainFolder, terrainFile).readBytes())
                val fresh = readCurrent?.invoke() ?: run {
                    readFoliageSource(
                        folder.name,
                        projectDir,
                        documents.json,
                        currentText,
                        parsing.foliageMeta(currentText)
                    )
                }
                require(fresh is FoliageSource.Ready && fresh.sameAs(stroke.source)) {
                    AbyssusBundle.message("foliageSourceChanged")
                }
                val counted = FoliageScatter(FoliageFingerprint()).scatter(stroke.source.terrain, settings, masks)
                if (counted.refusal != null) {
                    val candidate = counted.refusal == ScatterRefusal.CANDIDATE_LIMIT
                    throw IllegalArgumentException(
                        AbyssusBundle.message(
                            if (candidate) "foliageOverCandidates" else "foliageOverLimit", counted.estimate,
                            if (candidate) FOLIAGE_CANDIDATE_LIMIT else FOLIAGE_COPY_LIMIT,
                        )
                    )
                }
                val bytes = FoliageDataFile().write(counted.bake)
                val backup = if (stroke.source.stale && stroke.source.dataSha256 != null)
                    undoCache.preserve(
                        File(folder.path, settings.dataFileName()).toPath(), stroke.source.dataSha256,
                    ) else null
                synchronized(stagedBackups) {
                    if (disposed || generation != stroke.generation) backup?.close()
                    else if (backup != null) stagedBackups += backup
                }
                Staged(bytes, sha256Hex(bytes), counted.bake.fingerprint, backup, expected, maskPresent)
            }
            ui(Runnable {
                if (!owns(stroke) || disposed || project.isDisposed || !scene.isValid || !folder.isValid || !metadata.isValid) {
                    release(staged.getOrNull()?.backup)
                    if (active === stroke && drafts.of(stroke.source.folderName) === stroke.draft)
                        stroke.draft.revertStroke()
                    abandon(stroke)
                    return@Runnable
                }
                staged.fold(onSuccess = { commit(stroke, metadata, currentText, masks, it) }, onFailure = {
                    report(it.documentDisplayMessage())
                    discard(stroke)
                })
            })
        })
    }

    private fun commit(
        stroke: Stroke,
        metadata: VirtualFile,
        currentText: String,
        masks: Map<Int, ByteArray>,
        staged: Staged
    ) {
        if (currentText != stroke.source.metaText || textOf(metadata) != currentText) {
            release(staged.backup)
            report(AbyssusBundle.message("foliageSourceChanged"))
            discard(stroke)
            return
        }
        val settings = stroke.source.settings
        val before = stroke.source.masks.getValue(stroke.mode.layerId)
        val after = masks.getValue(stroke.mode.layerId)
        val width = settings.maskResolution
        val beforePatch = patch(before, width, stroke.rect)
        val afterPatch = patch(after, width, stroke.rect)
        val maskName = foliageMaskFileName(stroke.mode.layerId)
        val base = "assets/${folder.name}"
        val change = FileChange(
            "$base/$maskName",
            if (staged.maskPresent) FileSnapshot.Patch(beforePatch) else FileSnapshot.Absent,
            // A missing mask means full density; the initial write must materialize the whole mask once.
            if (staged.maskPresent) FileSnapshot.Patch(afterPatch) else FileSnapshot.Bytes(after)
        )
        val terrain = stroke.source.terrain
        val layer = stroke.mode.layerId
        val folderPath = folder.path
        val oldHash = stroke.source.dataSha256
        val backup = staged.backup
        val otherMasks = stroke.source.masks.filterKeys { it != layer }
        // Only the dirty rectangle of the edited layer survives in history, never the full before/after masks.
        var firstWrite: ByteArray? = staged.bytes
        val rebuild: (Boolean) -> ByteArray = { forward ->
            val first = firstWrite
            if (forward && first != null) {
                firstWrite = null
                first
            } else if (!forward && backup != null) backup.read()
            else {
                val stored = File(folderPath, foliageMaskFileName(layer)).takeIf { it.isFile }?.readBytes()
                    ?: fullMask(width)
                val chosen = (if (forward) afterPatch else beforePatch).applyTo(stored)
                FoliageDataFile().write(
                    FoliageScatter(FoliageFingerprint()).scatter(
                        terrain, settings,
                        otherMasks + (layer to chosen)
                    ).bake
                )
            }
        }
        val txn = AssetTransaction(
            AbyssusBundle.message("commandFoliageStroke"), listOf(change),
            expectedFiles = staged.expectedFiles,
            derived = listOf(DerivedFile("$base/${settings.dataFileName()}", oldHash, staged.sha256, rebuild))
        )
        var done = false
        val result = try {
            AssetFileCommand(project, LocalAssetFileStore(projectDir)).execute(
                txn,
                affected = { listOf(scene, metadata) })
                .also { done = it == AssetCommandResult.Done }
        } finally {
            firstWrite = null
            synchronized(stagedBackups) { stagedBackups.remove(backup) }
            if (!done) backup?.close()
        }
        if (done) source = FoliageSource.Ready(
            source.folderName, source.metaText, source.modelAssets,
            source.read, terrain, masks, staged.sha256, false, staged.fingerprint
        )
        else report(
            when (result) {
                is AssetCommandResult.Conflict -> AbyssusBundle.message(
                    "foliageApplyConflict",
                    result.path.substringAfterLast('/')
                )

                is AssetCommandResult.Collision -> AbyssusBundle.message(
                    "foliageApplyConflict",
                    result.path.substringAfterLast('/')
                )

                is AssetCommandResult.Blocked -> result.reason
                is AssetCommandResult.Failed -> AbyssusBundle.message(
                    "foliageApplyFailed",
                    result.cause.documentDisplayMessage()
                )

                AssetCommandResult.Cancelled -> AbyssusBundle.message("foliageApplyCancelled")
                AssetCommandResult.Done -> ""
            }
        )
        discard(stroke)
    }

    override fun cancelled() {
        val stroke = active
        val owned = stroke?.let(::owns) == true
        generation++
        synchronized(stagedBackups) {
            stagedBackups.forEach { it.close() }
            stagedBackups.clear()
        }
        if (stroke != null) {
            if (owned) discard(stroke) else {
                if (drafts.of(stroke.source.folderName) === stroke.draft) stroke.draft.revertStroke()
                abandon(stroke)
            }
        }
    }

    /** A re-read after Undo or an external file change becomes the base of the next stroke. */
    fun sourceRead(fresh: FoliageSource.Ready) {
        if (!source.sameAs(fresh)) {
            cancelled()
            source = fresh
        }
    }

    override fun dispose() {
        disposed = true
        cancelled()
    }

    private fun release(backup: FoliageUndoCache.Handle?) {
        synchronized(stagedBackups) { stagedBackups.remove(backup) }
        backup?.close()
    }

    private fun owns(stroke: Stroke) = active === stroke && generation == stroke.generation &&
            drafts.of(stroke.source.folderName) === stroke.draft && stroke.revision == stroke.draft.revision

    private fun discard(stroke: Stroke) {
        stroke.draft.revertStroke()
        if (drafts.of(stroke.source.folderName) === stroke.draft && stroke.draft.settings == null)
            drafts.discard(stroke.source.folderName)
        abandon(stroke)
    }

    private fun abandon(stroke: Stroke) {
        if (active === stroke) active = null
    }

    private fun patch(mask: ByteArray, width: Int, rect: GridRect): MaskPatch {
        val values = ByteArray(rect.width * rect.height)
        for (z in rect.minZ..rect.maxZ)
            mask.copyInto(values, (z - rect.minZ) * rect.width, z * width + rect.minX, z * width + rect.maxX + 1)
        return MaskPatch(width, rect, values, sha256Hex(mask))
    }

    class Stroke(
        val source: FoliageSource.Ready, val draft: FoliageDraft, val mode: BrushMode,
        val brush: FoliageBrush, val point: Vector3, val mask: ByteArray, val generation: Int
    ) {
        var rect = emptyGridRect()
        var revision = draft.revision
        var releasing = false
    }

    private class Staged(
        val bytes: ByteArray, val sha256: String, val fingerprint: ByteArray,
        val backup: FoliageUndoCache.Handle?, val expectedFiles: Map<String, FileSnapshot>, val maskPresent: Boolean
    )
}
