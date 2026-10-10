/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.foliage

import com.intellij.openapi.components.Service
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageDraft
import java.util.concurrent.ConcurrentHashMap

/**
 * The uncommitted foliage drafts of one project, one per foliage folder (design decision 7): the properties panel and
 * the brush write them, every Scene view reads them, and Apply, a stroke release, Cancel and a selection change
 * discard them. This is how a settings edit or a brush stroke reaches every open Scene view without a file write.
 *
 * The map is concurrent because the panel writes on the EDT while the views read on the render thread; a draft's own
 * state is edited by one of them at a time, the brush taking turns with the panel.
 */
@Service(Service.Level.PROJECT)
class FoliageDrafts {
    private val drafts = ConcurrentHashMap<String, FoliageDraft>()

    /** The draft of the foliage folder [name], or null while its stored settings and masks are in use. */
    fun of(name: String): FoliageDraft? = drafts[name]

    /** The draft of [name], made from [terrain] and [maskResolution] when the folder has none yet. */
    fun open(name: String, terrain: TerrainData, maskResolution: Int): FoliageDraft =
        drafts.computeIfAbsent(name) { FoliageDraft(terrain, maskResolution) }

    /** Forgets the draft of [name]: the stored asset is what the views show again. */
    fun discard(name: String) {
        drafts.remove(name)
    }

    /** Forgets every draft: the selection changed or the project closed. */
    fun discardAll() {
        drafts.clear()
    }
}
