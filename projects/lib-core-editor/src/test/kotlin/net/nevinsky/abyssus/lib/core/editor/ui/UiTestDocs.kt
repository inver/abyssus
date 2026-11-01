/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.slf4j.helpers.NOPLogger

internal val uiJson = JsonProcessor(NOPLogger.NOP_LOGGER)

/** [raw] in the printer's own layout, so "unchanged text" can be compared exactly. */
internal fun canonicalUi(raw: String): String = uiJson.pretty(uiJson.parse(raw, UiMetaDoc::class.java))

internal fun admitUi(text: String) = UiCollectionDecoder().decode(text, uiJson)
