/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.assets.files.AssetFiles

/**
 * Assets of a project that changed: the [names] of their folders, and [files], a fresh snapshot of the project's asset
 * metadata (unsaved editor text included) that every later load reads from.
 */
class AssetRevisionBatch(val names: Set<String>, val files: AssetFiles) {
    /** This batch followed by [later]: the changed names add up and the newer snapshot wins. */
    operator fun plus(later: AssetRevisionBatch) = AssetRevisionBatch(names + later.names, later.files)
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
