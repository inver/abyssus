/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.terrain

import com.intellij.openapi.components.Service
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptDraft
import java.util.concurrent.ConcurrentHashMap

/**
 * The sculpting strokes in progress in one project, one per terrain folder. A stroke's [SculptDraft] lives here only
 * while the button is down: every Scene view showing the terrain copies the draft's changed heights into its own
 * terrain on each new revision, and once the draft is gone (committed or discarded) the views load the terrain again
 * from the files. This is how a stroke reaches every open view without a file write.
 *
 * The map is concurrent so a view on its render thread can read while the stroke on the AWT thread opens and discards.
 */
@Service(Service.Level.PROJECT)
class SculptDrafts {
    private val drafts = ConcurrentHashMap<String, SculptDraft>()

    /** The stroke in progress on the terrain folder [name], or null. */
    fun of(name: String): SculptDraft? = drafts[name]

    /** Starts a stroke on [name] with a draft made by [create], replacing any earlier one. */
    fun open(name: String, create: () -> SculptDraft): SculptDraft = create().also { drafts[name] = it }

    /** Ends the stroke on [name]: the views reload the terrain from its files. */
    fun discard(name: String) {
        drafts.remove(name)
    }

    /** Ends every stroke: the project closed. */
    fun discardAll() {
        drafts.clear()
    }
}
