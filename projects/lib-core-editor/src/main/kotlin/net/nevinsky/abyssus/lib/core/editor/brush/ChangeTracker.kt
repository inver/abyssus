/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.brush

/**
 * What a brush draft keeps of its changes: a [revision] that moves with every one, and the [changed] rectangle that
 * grows to cover them all, which is what a view copies into its own grid when it sees a new revision.
 */
class ChangeTracker {
    var revision: Int = 0
        private set

    var changed: GridRect = emptyGridRect()
        private set

    /** Records a change over [rect] and returns the new [revision]. */
    fun mark(rect: GridRect): Int {
        changed = changed.union(rect)
        return ++revision
    }
}
