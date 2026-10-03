/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.files.AssetRevisionTracker
import net.nevinsky.abyssus.assets.files.MetaTextSource
import net.nevinsky.abyssus.assets.files.ProjectRevisions
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import java.io.File

/**
 * Works out which assets of one project changed, from events the caller feeds in, and hands them on as an
 * [AssetRevisionBatch]. Everything is judged by the effective revision of the assets (metadata as the editors hold it,
 * saved or not, plus the stamps of the files it names), so saving text that is already shown, Undo and Redo back to a
 * revision, or an event that changed nothing produce no batch.
 *
 * Threads: [start], [changed] and [dispose] and every callback to [deliver] run on the UI thread; the project is read
 * through [background] and never from IDE documents (those are captured on the UI thread by [unsavedMeta]). Events
 * arriving while a read is under way cost one more read after it, not one each.
 */
class AssetRefresh(
    private val projectDir: File,
    private val json: JsonProcessor,
    private val unsavedMeta: () -> Map<File, String>,
    private val background: (Runnable) -> Unit,
    private val ui: (Runnable) -> Unit,
    private val deliver: (AssetRevisionBatch) -> Unit,
) {
    private val tracker = AssetRevisionTracker(json)
    private val assetsDir = File(projectDir.absoluteFile, net.nevinsky.abyssus.assets.ASSETS_DIR)

    private var baseline: ProjectRevisions? = null
    private var running = false
    private var again = false
    private var disposed = false

    /** Reads the starting revision. Call once, before the view loads from the files. */
    fun start() = schedule()

    /** Something under the project's assets (a file, or the text of a metadata document) changed. */
    fun changed() {
        if (disposed) return
        if (running) again = true else schedule()
    }

    fun dispose() {
        disposed = true
    }

    private fun schedule() {
        if (disposed) return
        running = true
        again = false
        val overrides = unsavedMeta()
        val source = MetaTextSource { file ->
            overrides[file.absoluteFile] ?: file.takeIf { it.isFile }?.readText()
        }
        background {
            val result = runCatchingKeepingCancellation { tracker.snapshot(assetsDir, source) }
            ui { finished(result.getOrNull(), source) }
        }
    }

    private fun finished(snapshot: ProjectRevisions?, source: MetaTextSource) {
        running = false
        if (disposed) return
        if (snapshot != null) {
            val before = baseline
            baseline = snapshot
            if (before != null) {
                val names = tracker.changed(before, snapshot)
                if (names.isNotEmpty()) deliver(AssetRevisionBatch(names, AssetFiles(projectDir, json, source)))
            }
        }
        if (again) schedule()
    }
}
