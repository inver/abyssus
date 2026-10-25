/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import net.nevinsky.abyssus.lib.core.editor.arrange.ArrangementPreview
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight
import net.nevinsky.abyssus.lib.core.editor.arrange.LineHeights
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.editor.water.WaterPreview

/**
 * The scene as the user sees it now: the file's content with the changes that are not written yet laid over it, in
 * one order for everything that asks. [posed] puts the simulated poses over the authored content; [apply] then adds the
 * gizmo drag, the water move and the copies of the path arrangement being edited. Each step returns its input itself while
 * its layer is empty, and remembers its last result until an input is replaced (compared by reference, as the view's
 * caches are).
 */
class ScenePreviewLayers(
    private val state: SceneViewState,
    private val heights: GroundHeight = LineHeights(),
) {
    private var posedBase: SceneContent? = null
    private var posedPoses: Map<String, *>? = null
    private var posedOf: SceneContent? = null

    /** [base] with the simulated poses applied: [base] itself while nothing plays. */
    fun posed(base: SceneContent): SceneContent {
        val poses = state.poses
        if (poses.isEmpty()) return base
        posedOf?.takeIf { base === posedBase && poses === posedPoses }?.let { return it }
        return ScenePreview().withPoses(base, poses).also {
            posedOf = it
            posedBase = base
            posedPoses = poses
        }
    }

    private var shownBase: SceneContent? = null
    private var shownDrag: Map<String, DragResult>? = null
    private var shownWater: Map<String, *>? = null
    private var shownDraft: PathEdit? = null
    private var shownOf: SceneContent? = null

    /** [base] with the drag, the water move and the path draft's copies applied: [base] itself while all three are empty. */
    fun apply(base: SceneContent): SceneContent {
        val drag = state.preview
        val water = state.waterPreview
        val draft = state.pathDraft
        if (drag.isEmpty() && water.isEmpty() && draft == null) return base
        shownOf?.takeIf { base === shownBase && drag === shownDrag && water === shownWater && draft === shownDraft }
            ?.let { return it }
        val moved = if (drag.isEmpty()) base else ScenePreview().apply(base, drag)
        val withWater = if (water.isEmpty()) moved else moved.copy(water = WaterPreview().apply(moved.water, water))
        val shown = if (draft == null) withWater else ArrangementPreview().apply(withWater, draft, heights)
        return shown.also {
            shownOf = it
            shownBase = base
            shownDrag = drag
            shownWater = water
            shownDraft = draft
        }
    }

    /** Forgets the remembered results so they do not keep a closed scene alive. */
    fun clear() {
        posedBase = null; posedPoses = null; posedOf = null
        shownBase = null; shownDrag = null; shownWater = null; shownDraft = null; shownOf = null
    }
}
