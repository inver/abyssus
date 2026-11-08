/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelmesh

import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.model.ModelMeta
import net.nevinsky.abyssus.lib.core.assets.model.ModelPartMeta
import net.nevinsky.abyssus.lib.core.assets.model.PartHinge
import net.nevinsky.abyssus.lib.core.assets.model.PartPivot
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome

/** Why a hinge's limits are not allowed. */
enum class HingeProblem {
    NOT_FINITE,
    MIN_ABOVE_ZERO,
    MAX_BELOW_ZERO,
    OUT_OF_RANGE,
}

/** The rules for hinge limits: finite, `min <= 0 <= max`, within +-180 degrees. */
class HingeLimits {
    fun problem(min: Float, max: Float): HingeProblem? =
        when {
            !min.isFinite() || !max.isFinite() -> HingeProblem.NOT_FINITE
            min > 0f -> HingeProblem.MIN_ABOVE_ZERO
            max < 0f -> HingeProblem.MAX_BELOW_ZERO
            min < -180f || max > 180f -> HingeProblem.OUT_OF_RANGE
            else -> null
        }

    fun problem(hinge: PartHinge): HingeProblem? = problem(hinge.min, hinge.max)

    /** The text for [problem] from [messages] (key `hingeError.<NAME>`). */
    fun describe(problem: HingeProblem, messages: EditorMessages): String = messages.message("hingeError.${problem.name}")
}

/**
 * Edits of the `parts` of a MODEL asset's `meta.json`, as text transforms over [AssetMetaTextEditor]. An entry with
 * neither a pivot nor a hinge is removed and an empty `parts` is dropped, so clearing what was set restores the text.
 */
class ModelPartEdits(
    private val editor: AssetMetaTextEditor,
    private val messages: EditorMessages,
    private val limits: HingeLimits = HingeLimits(),
) {
    fun setPivot(text: String, node: String, pivot: PartPivot): TextEditOutcome = change(text, node) { it.copy(pivot = pivot) }

    fun clearPivot(text: String, node: String): TextEditOutcome = change(text, node) { it.copy(pivot = null) }

    /** Refused when the limits break [HingeLimits]. */
    fun setHinge(text: String, node: String, hinge: PartHinge): TextEditOutcome {
        limits.problem(hinge)?.let {
            return TextEditOutcome.Refused(IllegalArgumentException(limits.describe(it, messages)))
        }
        return change(text, node) { it.copy(hinge = hinge) }
    }

    fun clearHinge(text: String, node: String): TextEditOutcome = change(text, node) { it.copy(hinge = null) }

    private fun change(text: String, node: String, edit: (ModelPartMeta) -> ModelPartMeta): TextEditOutcome =
        editor.edit<ModelMeta>(text, MetaType.MODEL) { meta ->
            val parts = LinkedHashMap(meta.parts.orEmpty())
            parts[node] = edit(parts[node] ?: ModelPartMeta())
            meta.copy(parts = normalized(parts))
        }

    /** [parts] without entries that hold neither member; null when nothing is left. */
    fun normalized(parts: Map<String, ModelPartMeta>): Map<String, ModelPartMeta>? =
        parts.filterValues { it.pivot != null || it.hinge != null }.ifEmpty { null }
}
