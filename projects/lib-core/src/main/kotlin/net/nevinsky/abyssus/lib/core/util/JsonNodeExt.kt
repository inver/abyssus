/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.IntNode
import java.math.BigDecimal
import kotlin.math.abs

/** The child [name] unless it is absent or an explicit JSON null. */
fun JsonNode.opt(name: String): JsonNode? = get(name)?.takeIf { !it.isNull && !it.isMissingNode }

/** The child [name] when it is a string. */
fun JsonNode.text(name: String): String? = opt(name)?.takeIf { it.isTextual }?.asText()

/** The child [name] when it is a finite number. */
fun JsonNode.float(name: String): Float? = opt(name)?.takeIf { it.isNumber }?.floatValue()?.takeIf { it.isFinite() }

/** The child [name] when it is an object. */
fun JsonNode.obj(name: String): JsonNode? = opt(name)?.takeIf { it.isObject }

fun JsonNode.number(value: Float): JsonNode {
    val v = if (value.isFinite()) value else 0f
    return if (v == Math.rint(v.toDouble()).toFloat() && abs(v) < 1e9f) IntNode.valueOf(v.toInt()) else DecimalNode(
        BigDecimal(v.toString())
    )
}