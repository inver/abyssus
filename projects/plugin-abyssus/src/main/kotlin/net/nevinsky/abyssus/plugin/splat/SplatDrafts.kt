/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.splat

import com.intellij.openapi.components.Service
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatDraft
import java.util.concurrent.ConcurrentHashMap

/**
 * The splat painting strokes in progress in one project, keyed by the **splat texture's asset folder** (not the
 * terrain's), so two terrains that share a map both follow one stroke. A stroke's [SplatDraft] lives here from the press
 * until its write has finished (or the stroke is discarded): every Scene view showing a terrain with that map copies the
 * draft's changed texels into its own splat texture on each new revision, and once the draft is gone the views load the
 * texture again from the file.
 *
 * The map is concurrent so a view on its render thread can read while the stroke on the AWT thread opens and discards.
 */
@Service(Service.Level.PROJECT)
class SplatDrafts {
    private val drafts = ConcurrentHashMap<String, SplatDraft>()

    /** The stroke in progress on the splat texture folder [splatFolder], or null. */
    fun of(splatFolder: String): SplatDraft? = drafts[splatFolder]

    /** Starts a stroke on [splatFolder] with a draft made by [create], replacing any earlier one. */
    fun open(splatFolder: String, create: () -> SplatDraft): SplatDraft = create().also { drafts[splatFolder] = it }

    /** Ends the stroke on [splatFolder]: the views reload the texture from its file. */
    fun discard(splatFolder: String) {
        drafts.remove(splatFolder)
    }

    /** Ends every stroke: the project closed. */
    fun discardAll() {
        drafts.clear()
    }
}
