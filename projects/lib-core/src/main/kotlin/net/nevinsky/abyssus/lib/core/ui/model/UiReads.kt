/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ui.model

import net.nevinsky.abyssus.lib.core.util.displayTextAt

/** The string widget property [name], or null when it is missing or of another kind. */
fun UiWidget.propertyText(name: String): String? = properties[name]?.takeIf { it.isTextual }?.textValue()

/** The widget property [name] as display text whatever its kind; empty when missing. */
fun UiWidget.propertyDisplay(name: String): String = properties[name]?.asText().orEmpty()

/** The numeric widget property [name], or null. */
fun UiWidget.propertyNumber(name: String): Double? = properties[name]?.takeIf { it.isNumber }?.doubleValue()

/** The whole-number widget property [name], or null. */
fun UiWidget.propertyWhole(name: String): Double? = properties[name]?.takeIf { it.isIntegralNumber }?.doubleValue()

/** The style value at [path] as display text; empty when missing. */
fun UiStyle.displayAt(vararg path: String): String = values.displayTextAt(*path)
