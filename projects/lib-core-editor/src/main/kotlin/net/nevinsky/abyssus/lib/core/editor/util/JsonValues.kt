/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.util

/**
 * Readers over the plain values `JsonProcessor.parse(text, Map::class.java)` returns: a JSON object is a map in file
 * order, an array a list, and a scalar a string, number, boolean or null. Used to show a document's keys without
 * binding them to a class; they never fail on a value of another kind, they just do not find it.
 */
typealias JsonValues = Map<String, Any?>

/** The object [value] is, or null. */
@Suppress("UNCHECKED_CAST")
fun valuesOf(value: Any?): JsonValues? = (value as? Map<*, *>)?.let { it as JsonValues }

/** The array [value] is, or null. */
fun listOfValues(value: Any?): List<Any?>? = value as? List<Any?>

/** The member reached by [path], or null when a step is missing or not an object. */
fun JsonValues.at(vararg path: String): Any? {
    var current: Any? = this
    for (step in path) current = valuesOf(current)?.get(step) ?: return null
    return current
}

/** The text at [path]; null when it is missing or not text. */
fun JsonValues.textAt(vararg path: String): String? = at(*path) as? String

/** The object at [path], or null. */
fun JsonValues.objectAt(vararg path: String): JsonValues? = valuesOf(at(*path))

/** The array at [path], or null. */
fun JsonValues.arrayAt(vararg path: String): List<Any?>? = listOfValues(at(*path))

/** True when the object at [path] has member [key] (even a null one). */
fun JsonValues.hasMember(key: String, vararg path: String): Boolean = (if (path.isEmpty()) this else objectAt(*path))?.containsKey(key) == true

/** A scalar as the rows show it: text as is, a number or boolean as written, null as `null`. */
fun scalarText(value: Any?): String = when (value) {
    null -> "null"
    else -> value.toString()
}
