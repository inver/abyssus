/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.terrain

import net.nevinsky.abyssus.assets.terrain.generation.TerrainGenerationDraft
import net.nevinsky.abyssus.assets.terrain.generation.TerrainGenerator
import kotlin.coroutines.cancellation.CancellationException

/** How a started preview ended; a superseded or cancelled one reports nothing at all. */
sealed interface PreviewOutcome {
    /** The draft accepted the heights: its preview is now the one to apply. */
    data object Ready : PreviewOutcome

    data class Failed(val message: String) : PreviewOutcome
}

/**
 * Runs the previews of a [TerrainGenerationDraft] in the background and hands the result back on the UI thread. One
 * request is current at a time: starting another, [invalidate] (a changed setting, Cancel) or [dispose] (a changed
 * selection, a closed dialog) stops the running one at its next row and drops its result. Shared by the regeneration
 * controls and the New terrain dialog, so both gate Apply and Create on a preview that matches the current settings.
 */
class TerrainPreviewRunner(
    private val generator: TerrainGenerator,
    private val background: (Runnable) -> Unit,
    private val ui: (Runnable) -> Unit,
) {
    @Volatile
    private var active = -1L

    @Volatile
    private var disposed = false

    /** Starts a preview of [draft]'s current inputs; false (nothing started) when they are invalid. [onOutcome] runs on the UI thread. */
    fun start(draft: TerrainGenerationDraft, onOutcome: (PreviewOutcome) -> Unit): Boolean {
        if (disposed) return false
        val request = draft.begin() ?: return false
        active = request.token
        background {
            val result = runCatching {
                generator.generate(request.resolution, request.size, request.settings) {
                    if (disposed || active != request.token) throw CancellationException("superseded")
                }
            }
            ui {
                if (disposed) return@ui
                result.fold(
                    onSuccess = { heights -> if (draft.complete(request, heights)) onOutcome(PreviewOutcome.Ready) },
                    onFailure = { e ->
                        if (e is CancellationException) return@ui
                        draft.fail(request)
                        if (active == request.token) onOutcome(PreviewOutcome.Failed(e.message ?: e.javaClass.simpleName))
                    },
                )
            }
        }
        return true
    }

    /** Stops the running preview, if any, and drops its result. */
    fun invalidate() {
        active = -1
    }

    fun dispose() {
        disposed = true
        active = -1
    }
}
