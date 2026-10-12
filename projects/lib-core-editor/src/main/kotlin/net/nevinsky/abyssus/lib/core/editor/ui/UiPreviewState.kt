/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.ui.model.UiBinding
import net.nevinsky.abyssus.lib.core.ui.model.UiBindingAccess
import net.nevinsky.abyssus.lib.core.ui.model.UiBindingType
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection
import net.nevinsky.abyssus.lib.core.ui.state.UiActionSink
import net.nevinsky.abyssus.lib.core.ui.state.UiSnapshot
import net.nevinsky.abyssus.lib.core.ui.state.UiValue
import net.nevinsky.abyssus.lib.core.ui.state.UiValueWriter
import net.nevinsky.abyssus.lib.core.ui.state.UiWrite
import net.nevinsky.abyssus.lib.core.ui.state.UiWriteResult

enum class UiLogKind { ACTION, WRITE, REJECTED_WRITE, SAMPLE }

/** One line of the preview's action log; [sequence] grows for the whole session, so a trimmed log still shows order. */
data class UiLogEntry(val sequence: Long, val kind: UiLogKind, val text: String)

sealed interface SampleResult {
    data class Applied(val value: UiValue) : SampleResult

    data class Rejected(val reason: SampleProblem) : SampleResult
}

enum class SampleProblem { UNKNOWN_BINDING, NOT_A_NUMBER, NOT_A_BOOLEAN, BELOW_MINIMUM, ABOVE_MAXIMUM, TOO_LONG, NOT_A_LIST, NO_SUCH_ROW, NO_SUCH_FIELD }

/**
 * The preview's own world: sample values for the declared bindings, a bounded log of what the UI did, and nothing else. It
 * is built from an immutable decoded [collection] and never sees a document, a file, a setting or game code, so no
 * interaction can reach them: a write only changes a sample here, an action only adds a log line. [reset] returns the
 * samples to the authored ones and clears the log; a changed definition is a new state ([Factory.of]).
 * UI thread only.
 */
class UiPreviewState private constructor(
    val collection: UiCollection,
    /** The screen the preview starts on and returns to on reset. */
    val startScreen: String?,
    private val maxLog: Int,
) {
    private val values = LinkedHashMap<String, UiValue>()
    private val entries = ArrayDeque<UiLogEntry>()
    private var next = 1L

    /** Bumped by every [reset]; a host that notices a new generation returns navigation to [startScreen]. */
    var generation = 0
        private set

    val samples: Map<String, UiValue> get() = values
    val log: List<UiLogEntry> get() = entries.toList()

    init { authored() }

    private fun authored() {
        values.clear()
        for (b in collection.bindings) values[b.name] = authoredValue(b)
    }

    private fun authoredValue(b: UiBinding): UiValue = b.sample?.let { sampleOf(b.type, it, b.fields) } ?: zero(b.type)

    private fun zero(type: UiBindingType): UiValue = when (type) {
        UiBindingType.NUMBER -> UiValue.Num(0.0)
        UiBindingType.BOOLEAN -> UiValue.Bool(false)
        UiBindingType.STRING -> UiValue.Text("")
        UiBindingType.LIST -> UiValue.Rows(emptyList())
    }

    private fun sampleOf(type: UiBindingType, node: com.fasterxml.jackson.databind.JsonNode, fields: List<net.nevinsky.abyssus.lib.core.ui.model.UiRecordField>): UiValue = when (type) {
        UiBindingType.NUMBER -> UiValue.Num(node.doubleValue())
        UiBindingType.BOOLEAN -> UiValue.Bool(node.booleanValue())
        UiBindingType.STRING -> UiValue.Text(node.textValue())
        UiBindingType.LIST -> UiValue.Rows(node.map { row -> fields.associate { f -> f.name to (row.get(f.name)?.let { sampleOf(f.type, it, emptyList()) } ?: zero(f.type)) } })
    }

    /** What the UI is shown now: every binding's current sample. */
    fun snapshot(): UiSnapshot = UiSnapshot(values)

    /** The writer for a [net.nevinsky.abyssus.lib.core.ui.state.UiBindingSession]: stores a change in the samples and logs it; read-only bindings refuse. */
    fun writer(): UiValueWriter = UiValueWriter { write -> accept(write) }

    private fun accept(write: UiWrite): UiWriteResult {
        val binding = collection.binding(write.binding)
        if (binding == null || binding.access != UiBindingAccess.WRITE || write.value.type != binding.type) {
            log(UiLogKind.REJECTED_WRITE, "${write.binding} ← ${show(write.value)} (rejected)")
            return UiWriteResult.Rejected(if (binding == null) "unknown binding" else if (binding.access != UiBindingAccess.WRITE) "read-only" else "wrong type")
        }
        values[binding.name] = write.value
        log(UiLogKind.WRITE, "${binding.name} ← ${show(write.value)}")
        return UiWriteResult.Accepted(write.value)
    }

    /** Game actions are logged, never run; the preview has no game code to hand them to. */
    fun actionSink(): UiActionSink = UiActionSink { name ->
        log(UiLogKind.ACTION, name)
        true
    }

    /** Sets a sample from the text a person typed, checking type and the binding's `min`, `max` and `maxLength`. */
    fun setSample(name: String, input: String): SampleResult {
        val binding = collection.binding(name) ?: return SampleResult.Rejected(SampleProblem.UNKNOWN_BINDING)
        val value: UiValue = when (binding.type) {
            UiBindingType.NUMBER -> {
                val v = input.trim().toDoubleOrNull()?.takeIf(Double::isFinite) ?: return SampleResult.Rejected(SampleProblem.NOT_A_NUMBER)
                if (binding.min != null && v < binding.min!!) return SampleResult.Rejected(SampleProblem.BELOW_MINIMUM)
                if (binding.max != null && v > binding.max!!) return SampleResult.Rejected(SampleProblem.ABOVE_MAXIMUM)
                UiValue.Num(v)
            }
            UiBindingType.BOOLEAN -> when (input.trim().lowercase()) {
                "true", "on", "yes" -> UiValue.Bool(true)
                "false", "off", "no" -> UiValue.Bool(false)
                else -> return SampleResult.Rejected(SampleProblem.NOT_A_BOOLEAN)
            }
            UiBindingType.STRING -> {
                if (binding.maxLength != null && input.length > binding.maxLength!!) return SampleResult.Rejected(SampleProblem.TOO_LONG)
                UiValue.Text(input)
            }
            UiBindingType.LIST -> return SampleResult.Rejected(SampleProblem.NOT_A_LIST) // lists are edited row by row
        }
        values[name] = value
        log(UiLogKind.SAMPLE, "$name = ${show(value)}")
        return SampleResult.Applied(value)
    }

    private fun listBinding(name: String): Pair<UiBinding, UiValue.Rows>? {
        val b = collection.binding(name)?.takeIf { it.type == UiBindingType.LIST } ?: return null
        return b to (values[name] as? UiValue.Rows ?: UiValue.Rows(emptyList()))
    }

    /** Adds a row of zero values (or of the field texts in [fields]) to the list sample [name]. */
    fun addRow(name: String, fields: Map<String, String> = emptyMap()): SampleResult {
        val (binding, rows) = listBinding(name) ?: return SampleResult.Rejected(if (collection.binding(name) == null) SampleProblem.UNKNOWN_BINDING else SampleProblem.NOT_A_LIST)
        val row = LinkedHashMap<String, UiValue>()
        for (f in binding.fields) {
            val v = fields[f.name]?.let { parseField(f.type, it) ?: return SampleResult.Rejected(problemOf(f.type)) } ?: zero(f.type)
            row[f.name] = v
        }
        values[name] = UiValue.Rows(rows.items + row)
        log(UiLogKind.SAMPLE, "$name + row ${rows.items.size}")
        return SampleResult.Applied(values.getValue(name))
    }

    fun removeRow(name: String, index: Int): SampleResult {
        val (_, rows) = listBinding(name) ?: return SampleResult.Rejected(SampleProblem.NOT_A_LIST)
        if (index !in rows.items.indices) return SampleResult.Rejected(SampleProblem.NO_SUCH_ROW)
        values[name] = UiValue.Rows(rows.items.filterIndexed { i, _ -> i != index })
        log(UiLogKind.SAMPLE, "$name - row $index")
        return SampleResult.Applied(values.getValue(name))
    }

    /** Sets one field of one row from typed text, with the field's type checked. */
    fun setRowField(name: String, index: Int, field: String, input: String): SampleResult {
        val (binding, rows) = listBinding(name) ?: return SampleResult.Rejected(SampleProblem.NOT_A_LIST)
        if (index !in rows.items.indices) return SampleResult.Rejected(SampleProblem.NO_SUCH_ROW)
        val f = binding.fields.firstOrNull { it.name == field } ?: return SampleResult.Rejected(SampleProblem.NO_SUCH_FIELD)
        val v = parseField(f.type, input) ?: return SampleResult.Rejected(problemOf(f.type))
        values[name] = UiValue.Rows(rows.items.mapIndexed { i, r -> if (i == index) r + (field to v) else r })
        log(UiLogKind.SAMPLE, "$name[$index].$field = ${show(v)}")
        return SampleResult.Applied(values.getValue(name))
    }

    private fun parseField(type: UiBindingType, input: String): UiValue? = when (type) {
        UiBindingType.NUMBER -> input.trim().toDoubleOrNull()?.takeIf(Double::isFinite)?.let { UiValue.Num(it) }
        UiBindingType.BOOLEAN -> when (input.trim().lowercase()) { "true", "on", "yes" -> UiValue.Bool(true); "false", "off", "no" -> UiValue.Bool(false); else -> null }
        UiBindingType.STRING -> UiValue.Text(input)
        UiBindingType.LIST -> null
    }

    private fun problemOf(type: UiBindingType) = when (type) {
        UiBindingType.BOOLEAN -> SampleProblem.NOT_A_BOOLEAN
        else -> SampleProblem.NOT_A_NUMBER
    }

    /** Authored samples again, an empty log, and a new [generation] so the host returns to the start screen. */
    fun reset() {
        authored()
        entries.clear()
        generation++
    }

    private fun log(kind: UiLogKind, text: String) {
        entries.addLast(UiLogEntry(next++, kind, text))
        while (entries.size > maxLog) entries.removeFirst()
    }

    private fun show(value: UiValue) = when (value) {
        is UiValue.Num -> if (value.value == Math.rint(value.value)) value.value.toLong().toString() else value.value.toString()
        is UiValue.Bool -> value.value.toString()
        is UiValue.Text -> "\"${value.value}\""
        is UiValue.Rows -> "${value.items.size} rows"
    }

    class Factory(private val maxLog: Int = DEFAULT_LOG) {
        /** A new state for [collection]: the previous state's samples and log are not carried over (a definition change resets). */
        fun of(collection: UiCollection, startScreen: String? = collection.entryScreen ?: collection.screens.firstOrNull()?.id): UiPreviewState =
            UiPreviewState(collection, startScreen, maxLog)
    }
}

const val DEFAULT_LOG = 200
