/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.util

import com.fasterxml.jackson.databind.JsonNode

/** The child [name] unless it is absent or an explicit JSON null. */
fun JsonNode.opt(name: String): JsonNode? = get(name)?.takeIf { !it.isNull && !it.isMissingNode }

/** The child [name] when it is a string. */
fun JsonNode.text(name: String): String? = opt(name)?.takeIf { it.isTextual }?.asText()

/** The child [name] when it is a finite number. */
fun JsonNode.float(name: String): Float? = opt(name)?.takeIf { it.isNumber }?.floatValue()?.takeIf { it.isFinite() }

/** The child [name] when it is an object. */
fun JsonNode.obj(name: String): JsonNode? = opt(name)?.takeIf { it.isObject }
private fun JsonNode.walk(path: Array<out String>): JsonNode? = path.fold(this as JsonNode?) { node, key -> node?.opt(key) }

/** The string at [path] below this node, or null when anything on the way is missing, null or of another kind. */
fun JsonNode.textAt(vararg path: String): String? = walk(path)?.takeIf { it.isTextual }?.textValue()

/** The boolean at [path] below this node, or null. */
fun JsonNode.boolAt(vararg path: String): Boolean? = walk(path)?.takeIf { it.isBoolean }?.booleanValue()

/** The number at [path] below this node as written (`2.50` stays `2.50`), or null. */
fun JsonNode.numberTextAt(vararg path: String): String? = walk(path)?.takeIf { it.isNumber }?.asText()

/** True when [path] below this node leads to an object. */
fun JsonNode.isObjectAt(vararg path: String): Boolean = walk(path)?.isObject == true

/** True when the member [name] is present, even as an explicit null. */
fun JsonNode.hasMember(name: String): Boolean = has(name)

/** The string entries of the array at [path] below this node; empty when it is no array. */
fun JsonNode.textsAt(vararg path: String): List<String> =
    walk(path)?.takeIf { it.isArray }?.mapNotNull { it.takeIf(JsonNode::isTextual)?.textValue() }.orEmpty()

/** The member names of the object at [path] below this node, in file order; empty when it is no object. */
fun JsonNode.namesAt(vararg path: String): List<String> =
    walk(path)?.takeIf { it.isObject }?.fieldNames()?.asSequence()?.toList().orEmpty()

/** True when this node is an object, an array or a string/number/boolean/null scalar: the kind a tree row shows. */
val JsonNode.isContainer: Boolean get() = isContainerNode

/** True when [path] below this node leads to a member that is present. */
fun JsonNode.hasAt(vararg path: String): Boolean = walk(path.copyOfRange(0, path.size - 1))?.has(path.last()) == true

/** The node at [path] below this node, whatever its kind; null when anything on the way is missing. */
fun JsonNode.rawAt(vararg path: String): JsonNode? = path.fold(this as JsonNode?) { node, key -> node?.get(key) }

/** The object at [path] below this node, or null. */
fun JsonNode.objectAt(vararg path: String): JsonNode? = walk(path)?.takeIf { it.isObject }

/** This node's value as a number when it is one; [Double] for arithmetic. */
fun JsonNode?.numberOrNull(): Double? = this?.takeIf { it.isNumber }?.doubleValue()

/** This node's number as written, or null when it is not a number. */
fun JsonNode?.numberTextOrNull(): String? = this?.takeIf { it.isNumber }?.asText()

/** The value at [path] below this node as display text; empty when it is missing or null. */
fun JsonNode.displayTextAt(vararg path: String): String =
    path.fold(this as JsonNode?) { node, key -> node?.get(key) }?.takeIf { !it.isMissingNode && !it.isNull }?.asText().orEmpty()

/** The members of an object (name to value, in file order) or the elements of an array (index to value); empty for a scalar. */
fun JsonNode.members(): List<Pair<String, JsonNode>> =
    if (isObject) properties().map { (k, v) -> k to v } else mapIndexed { i, v -> "$i" to v }

/** Every string held under a member called one of [fields], anywhere below this node. */
fun JsonNode.textValuesOf(vararg fields: String): Set<String> =
    fields.flatMap { field -> findValues(field).filter { it.isTextual }.map { it.textValue() } }.toSet()

/** True when [text] is one of the strings below this node, at any depth. */
fun JsonNode.containsText(text: String): Boolean = when {
    isTextual -> textValue() == text
    isContainerNode -> any { it.containsText(text) }
    else -> false
}

/** The whole number at [path] below this node (`40` or `40.0`), or null when it is missing or fractional. */
fun JsonNode.wholeNumberAt(vararg path: String): Int? =
    walk(path)?.takeIf { it.isNumber && it.doubleValue() == it.intValue().toDouble() }?.intValue()

/** The string member [member] of every element of the array at [path] below this node. */
fun JsonNode.memberTextsAt(member: String, vararg path: String): List<String> =
    walk(path)?.takeIf { it.isArray }?.mapNotNull { it.textAt(member) }.orEmpty()
