/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

/** What to do about one event that may change what the scene view shows. */
enum class Reload {
    /** Re-read the scene now, and forget any delayed re-read: this one covers it. */
    NOW,

    /** Re-read after the typing pause; more typing before then merges into the same re-read. */
    LATER,

    /** Do nothing: the editor is closed. */
    NEVER,
}

/**
 * When the scene view re-reads its scene: typing in a text tab is waited out (a burst of keystrokes is one re-read of
 * the final text), while a saved change on disk, a plugin edit, and Undo or Redo are shown at once. Pure state, so the
 * rules are tested without an editor; the editor asks it for each event and for each time the pause runs out.
 */
class ReloadPolicy {
    private var pending = false
    private var disposed = false

    /** An unsaved edit in a text tab. */
    fun typing(): Reload {
        if (disposed) return Reload.NEVER
        pending = true
        return Reload.LATER
    }

    /** A change that must show at once: a file change on disk, a plugin edit, Undo or Redo. */
    fun immediate(): Reload {
        if (disposed) return Reload.NEVER
        pending = false
        return Reload.NOW
    }

    /** The typing pause ran out: true when a delayed re-read is still wanted (and no longer is after this). */
    fun due(): Boolean {
        val wanted = pending && !disposed
        pending = false
        return wanted
    }

    /** The editor is closed: nothing pending is delivered, and nothing new is accepted. */
    fun dispose() {
        disposed = true
        pending = false
    }
}
