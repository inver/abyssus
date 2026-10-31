/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation

/**
 * The text transform behind every asset `meta.json` write: read the text with the [AssetMetaParser], let the caller
 * replace its typed `additional` block, and write the meta back in the style of the original.
 */
class AssetMetaTextEditor(private val parser: AssetMetaParser) {
    /** Replaces the `additional` block of the [type] asset in [text] by what [change] makes of it. */
    fun <T : Any> edit(text: String, type: MetaType, change: (T) -> T): TextEditOutcome {
        val meta = runCatchingKeepingCancellation { parser.read("", text) }
            .getOrElse { return TextEditOutcome.Refused(it) }
        if (meta.type != type) return TextEditOutcome.Refused(IllegalArgumentException("not a ${type.name} asset"))
        val before = meta.typedAdditional<T>()
        val after = change(before)
        if (after == before) return TextEditOutcome.Unchanged
        return TextEditOutcome.Edited(parser.writeLike(parser.withAdditional(meta, after), text))
    }
}
