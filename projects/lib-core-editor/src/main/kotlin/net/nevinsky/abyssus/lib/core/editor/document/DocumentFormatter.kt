/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

/** The standard printing of a native document, for the text editor to show: null when the text is not a supported document. */
class DocumentFormatter(
    private val parsing: DocumentParsing,
    private val json: JsonProcessor,
) {
    private val metas = AssetMetaParser(json)

    fun pretty(text: String, kind: DocumentKind): String? = runCatchingKeepingCancellation {
        when (kind) {
            DocumentKind.SCENE -> json.pretty(parsing.parse(text))
            DocumentKind.PROJECT -> json.pretty(parsing.project(text))
            DocumentKind.ASSET -> metas.write(parsing.assetMeta(text), pretty = true)
        }
    }.getOrNull()
}
