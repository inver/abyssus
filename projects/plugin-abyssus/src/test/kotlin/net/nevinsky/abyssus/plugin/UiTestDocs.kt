/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin

import net.nevinsky.abyssus.lib.core.editor.ui.UiMetaDoc
import net.nevinsky.abyssus.lib.core.editor.ui.UiWidgetDoc
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.slf4j.helpers.NOPLogger

internal val uiDocJson = JsonProcessor(NOPLogger.NOP_LOGGER)

/** [raw] in the printer's own layout, so "unchanged text" can be compared exactly. */
internal fun canonicalUi(raw: String): String = uiDocJson.pretty(uiDocJson.parse(raw, UiMetaDoc::class.java))

internal fun admitUi(text: String) = UiCollectionDecoder().decode(text, uiDocJson)

internal fun uiDoc(text: String): UiMetaDoc = uiDocJson.parse(text, UiMetaDoc::class.java)

/** The root widget of the first screen of the UI asset [text]. */
internal fun uiRoot(text: String): UiWidgetDoc = uiDoc(text).additional!!.ui!!.screens!![0].root!!

/** The widget ids of the first screen of [text], parents before children. */
internal fun uiWidgetIds(text: String): List<String> {
    val out = mutableListOf<String>()
    fun walk(w: UiWidgetDoc) { w.id?.let { out += it }; w.children?.forEach(::walk) }
    walk(uiRoot(text))
    return out
}
