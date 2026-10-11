/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import net.nevinsky.abyssus.lib.core.assets.sky.clouds.Clouds

/**
 * Numbers the cloud asset a sky displays: it changes whenever the built [Clouds] object does, which is what a reload, a
 * replacement or a removal of the asset looks like to the sky (it reads the asset from the storage on every draw). Things
 * derived from the weather compare revisions to know they are stale.
 */
class CloudRevision {
    private var last: Clouds? = null
    private var revision = 0L

    /** The revision of [clouds]: the same number while it is the same object as last time. */
    fun of(clouds: Clouds?): Long {
        if (clouds !== last) {
            last = clouds
            revision++
        }
        return revision
    }
}
