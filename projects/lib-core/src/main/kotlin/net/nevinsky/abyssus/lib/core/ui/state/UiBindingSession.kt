/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.ui.state

import net.nevinsky.abyssus.lib.core.ui.model.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** A typed change a control proposes to the host; [originWidgetId] is the control that made it. */
data class UiWrite(val binding: String, val value: UiValue, val originWidgetId: String)

/** The host's synchronous answer: the authoritative value (possibly normalized), or a reason. Long work is the host's to schedule. */
sealed interface UiWriteResult {
    data class Accepted(val value: UiValue) : UiWriteResult
    data class Rejected(val reason: String) : UiWriteResult
}

fun interface UiValueWriter {
    fun write(write: UiWrite): UiWriteResult
}

sealed interface UiCommitOutcome {
    /** The host accepted; [value] is the authoritative value every consumer now shows. */
    data class Applied(val binding: String, val value: UiValue) : UiCommitOutcome

    /** The host rejected; [restored] is the last authoritative value, to show again with [reason]. */
    data class Rejected(val binding: String, val reason: String, val restored: UiValue) : UiCommitOutcome

    /** The proposal never reached the host: the target is not writable or the value does not fit the binding. */
    data class Refused(val diagnostic: UiBindingDiagnostic) : UiCommitOutcome
}

/** The input events a control reports; only some of them commit a value. */
enum class UiEditGesture { DRAG, RELEASE, KEY_STEP, TOGGLE, TYPING, ENTER, FOCUS_LOST }

/** Sliders commit on release or keyboard step, checkboxes on toggle, text fields on Enter or focus loss. */
fun commitsOn(type: UiWidgetType, gesture: UiEditGesture): Boolean = when (type) {
    UiWidgetType.SLIDER -> gesture == UiEditGesture.RELEASE || gesture == UiEditGesture.KEY_STEP
    UiWidgetType.CHECKBOX -> gesture == UiEditGesture.TOGGLE
    UiWidgetType.TEXT_FIELD -> gesture == UiEditGesture.ENTER || gesture == UiEditGesture.FOCUS_LOST
    else -> false
}

/**
 * The live value state of one UI collection in one host. [publish] takes the host's snapshot and never writes; [commit]
 * validates a control's proposal against its binding, hands it to the [writer] and keeps the authoritative result.
 * Single-threaded: call on the host's UI thread.
 */
class UiBindingSession(private val collection: UiCollection, private val writer: UiValueWriter) {
    private val resolver = UiBindingResolver(collection)
    private var values: Map<String, UiValue> = emptyMap()
    private var current: UiBindingView = resolver.view(UiSnapshot())

    val plan: UiBindingPlan get() = resolver.plan

    fun view(): UiBindingView = current

    /** Replaces all values with [snapshot]; listeners are not involved, so nothing is written back. */
    fun publish(snapshot: UiSnapshot): UiBindingView {
        values = snapshot.toMap()
        return rebuild()
    }

    fun commit(widget: UiWidget, proposed: UiValue): UiCommitOutcome {
        val binding = resolver.plan.writeTarget(collection, widget)
            ?: return UiCommitOutcome.Refused(
                resolver.plan.diagnostics.firstOrNull { it.widgetId == widget.id }
                    ?: UiBindingDiagnostic(UiBindingIssue.UNSUPPORTED_PROPERTY, null, widget.id),
            )
        val normalized = validate(binding, proposed, widget)
            ?: return UiCommitOutcome.Refused(UiBindingDiagnostic(UiBindingIssue.INCOMPATIBLE_VALUE, binding.name, widget.id))
        val before = current.value(binding.name)!!
        if (normalized == before) return UiCommitOutcome.Applied(binding.name, before)
        return when (val result = writer.write(UiWrite(binding.name, normalized, widget.id))) {
            is UiWriteResult.Accepted -> {
                val authoritative = result.value.takeIf { it.type == binding.type } ?: normalized
                values = values + (binding.name to authoritative)
                rebuild()
                UiCommitOutcome.Applied(binding.name, authoritative)
            }
            is UiWriteResult.Rejected -> UiCommitOutcome.Rejected(binding.name, result.reason, before)
        }
    }

    private fun rebuild(): UiBindingView = resolver.view(UiSnapshot(values)).also { current = it }

    /** The proposal clamped and snapped to the binding's constraints, or null when its type or length does not fit. */
    private fun validate(binding: UiBinding, proposed: UiValue, widget: UiWidget): UiValue? {
        if (proposed.type != binding.type) return null
        return when (proposed) {
            is UiValue.Bool -> proposed
            is UiValue.Rows -> null
            is UiValue.Text -> proposed.takeIf { binding.maxLength == null || it.value.length <= binding.maxLength }
            is UiValue.Num -> {
                if (!proposed.value.isFinite()) return null
                val low = binding.min ?: Double.NEGATIVE_INFINITY
                val high = binding.max ?: Double.POSITIVE_INFINITY
                var v = proposed.value
                val step = binding.step
                if (step != null) {
                    val origin = if (low.isFinite()) low else 0.0
                    v = origin + round((v - origin) / step) * step
                }
                UiValue.Num(min(max(v, low), high))
            }
        }
    }
}
