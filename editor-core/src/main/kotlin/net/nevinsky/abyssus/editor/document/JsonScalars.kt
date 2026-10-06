/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor.document

import com.fasterxml.jackson.databind.JsonNode

/** The text-or-number behind a scalar [value]; `null` for null and for a JSON null. */
fun scalarOf(value: Any?): Any? = when {
    value is JsonNode -> when {
        value.isNull || value.isMissingNode -> null
        value.isTextual -> value.asText()
        value.isBoolean -> value.asBoolean()
        value.isNumber -> value.numberValue()
        else -> value.toString()
    }
    else -> value
}
