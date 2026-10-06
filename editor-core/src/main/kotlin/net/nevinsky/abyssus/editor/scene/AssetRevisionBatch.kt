/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor.scene

import java.io.File

/**
 * Assets of a project that changed: the [names] of their folders, and [unsaved], the `meta.json` text the editors hold
 * that is not on the disk yet, which every later load reads.
 */
class AssetRevisionBatch(val names: Set<String>, val unsaved: Map<File, String> = emptyMap()) {
    /** This batch followed by [later]: the changed names add up and the newer text wins. */
    operator fun plus(later: AssetRevisionBatch) = AssetRevisionBatch(names + later.names, later.unsaved)
}

/**
 * Revisions waiting for a frame that can safely replace GL resources. [queue] may be called from any thread at any time
 * (the view may be hidden, and renders no frames then); [take] is called by the render step and gets everything queued
 * since the last [take] as one batch.
 */
class PendingAssetRevision {
    private val pending = java.util.concurrent.atomic.AtomicReference<AssetRevisionBatch?>(null)

    fun queue(revision: AssetRevisionBatch) {
        pending.updateAndGet { it?.plus(revision) ?: revision }
    }

    fun take(): AssetRevisionBatch? = pending.getAndSet(null)
}
