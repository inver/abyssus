/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.runtime.schema

import com.badlogic.ashley.core.Component

/** The kinds of value a game component field holds, by the [id] the schema file uses. */
enum class FieldType(val id: String) {
    DECIMAL("decimal"), WHOLE("whole"), BOOLEAN("boolean"), TEXT("text"), CHOICE("choice"),
    VECTOR("vector"), COLOR("color"), ENTITY("entity"), ASSET("asset"),
}

/** A [FieldType.VECTOR] value. */
data class SchemaVector(val x: Float, val y: Float, val z: Float)

/** A [FieldType.COLOR] value. */
data class SchemaColor(val r: Float, val g: Float, val b: Float, val a: Float)

/**
 * One field of a [ComponentSchema]. [default] holds the value type of [type]: `Float` (decimal), `Int` (whole,
 * entity), `Boolean`, `String` (text, choice, asset), [SchemaVector] or [SchemaColor]. [min] / [max] bound numbers
 * only and are null when unbounded.
 */
data class SchemaField(
    val name: String,
    val label: String,
    val type: FieldType,
    val default: Any,
    val group: String = "",
    val min: Double? = null,
    val max: Double? = null,
    val minExclusive: Boolean = false,
    val choices: List<String> = emptyList(),
    val assetType: String? = null,
)

/** A game component as data: its short [name], the [className] that declares it, its [label] and [fields] in order. */
data class ComponentSchema(val name: String, val className: String, val label: String, val fields: List<SchemaField>)

/** Registration of a game component failed; the message names the component and the reason. */
class ComponentRegistrationException(message: String) : IllegalArgumentException(message)

/** The game component classes a program registers, in the order they are exported. No classpath scanning. */
fun interface ComponentRegistry {
    fun components(): List<Class<out Component>>
}
