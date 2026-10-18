/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.round

/** Existing editor spellings differ intentionally; document transforms continue to write float nodes directly. */
enum class NumberTextStyle { PLAIN, FOLIAGE, DETAIL, UI, UI_DOCUMENT }

fun numberText(value: Number, style: NumberTextStyle = NumberTextStyle.PLAIN): String {
    val text = value.toString()
    val double = value.toDouble()
    return when (style) {
        NumberTextStyle.PLAIN -> BigDecimal(text).stripTrailingZeros().toPlainString()
        NumberTextStyle.FOLIAGE -> if ('E' in text) {
            BigDecimal(text).stripTrailingZeros().toPlainString()
        } else text

        NumberTextStyle.DETAIL -> BigDecimal(double).round(MathContext(10)).stripTrailingZeros().toPlainString()
        NumberTextStyle.UI -> if (double == round(double)) {
            double.toLong().toString()
        } else double.toString()

        NumberTextStyle.UI_DOCUMENT -> if (double == round(double) && abs(double) < 1e9) {
            double.toInt().toString()
        } else double.toString()
    }
}

/** UI documents retain their existing integer-node threshold and double precision outside it. */
fun uiNumberNode(value: Double): JsonNode {
    val integer = numberText(value, NumberTextStyle.UI_DOCUMENT).toIntOrNull()
    return if (integer != null) JsonNodeFactory.instance.numberNode(integer) else JsonNodeFactory.instance.numberNode(
        value
    )
}
